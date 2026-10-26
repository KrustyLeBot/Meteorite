package app.meteorite

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.meteorite.data.City
import app.meteorite.data.Forecast
import app.meteorite.data.Updater
import app.meteorite.sync.SkipReason
import app.meteorite.sync.SyncState
import app.meteorite.sync.SynchrotronClient
import app.meteorite.widget.WeatherWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Main : Screen
    data object Search : Screen
    data class Detail(val dayIndex: Int) : Screen
}

sealed interface UpdateState {
    data object Checking : UpdateState
    data class UpToDate(val build: Long) : UpdateState
    data class Available(val release: Updater.Release, val installed: Long) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Failed(val message: String) : UpdateState
}

sealed interface ForecastState {
    data object Loading : ForecastState
    data class Error(val message: String) : ForecastState
    /** [source] is the place the forecast was downloaded for. */
    data class Ready(val forecast: Forecast, val loadedAt: Long, val source: City) : ForecastState
}

class MeteoViewModel(app: Application) : AndroidViewModel(app) {
    private val meteo = app as MeteoApp

    /** Why `cities.json` could not be read; nothing is saved while it is set. */
    var storeError by mutableStateOf<String?>(null)
        private set
    var cities by mutableStateOf(readCities())
        private set
    var selected by mutableIntStateOf(0)
        private set
    var screen: Screen by mutableStateOf(Screen.Main)
    val forecasts = mutableStateMapOf<String, ForecastState>()

    /** Why the last refresh failed, per city, while older data is still shown. */
    val refreshErrors = mutableStateMapOf<String, String>()
    var refreshing by mutableStateOf(false)
        private set
    var backgroundOk by mutableStateOf(meteo.locator.hasBackgroundPermission())
        private set

    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<City>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var locating by mutableStateOf(false)
        private set
    var searchMessage by mutableStateOf<String?>(null)
        private set

    /** State of the update dialog; null when it is closed. */
    var update by mutableStateOf<UpdateState?>(null)
        private set
    private var updateJob: Job? = null

    /** Short message shown in a banner for a few seconds. */
    var notice by mutableStateOf<String?>(null)
        private set
    private var noticeJob: Job? = null

    // --- Proton Drive sync through Synchrotron ---

    /** A sync is running: the city list is read-only until it ends (forecasts still load). */
    var syncing by mutableStateOf(false)
        private set
    /** Synchrotron is installed but does not sync the Meteorite folder yet: offer to set it up. */
    var offerSyncSetup by mutableStateOf(false)
        private set
    /** "Configurer" was tapped before file access was granted: finish once it is. */
    private var setupPending = false
    private var stopSync: (() -> Unit)? = null

    val selectedCity get() = cities.getOrNull(selected)

    private val inFlight = mutableSetOf<String>()
    private var searchJob: Job? = null

    init {
        cities.forEach { city ->
            meteo.forecastCache.load(city)?.let {
                forecasts[city.id] = ForecastState.Ready(it.forecast, it.savedAt, it.source)
            }
        }
    }

    private fun readCities(): List<City> = try {
        meteo.cityStore.load().also { storeError = null }
    } catch (e: Exception) {
        storeError = e.message
        emptyList()
    }

    private fun show(text: String) {
        notice = text
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            delay(4_000)
            notice = null
        }
    }

    /** Re-reads the list, e.g. after a sync brought another device's changes. */
    private fun reloadCities() {
        val keep = selectedCity?.id
        cities = readCities()
        selected = cities.indexOfFirst { it.id == keep }.coerceAtLeast(0)
        cities.forEach { city ->
            if (city.id !in forecasts) {
                meteo.forecastCache.load(city)?.let { forecasts[city.id] = ForecastState.Ready(it.forecast, it.savedAt, it.source) }
            }
        }
        WeatherWidget.requestUpdate(meteo)
        ensureLoaded()
    }

    /** Asks Synchrotron for a sync so the cities are up to date when the app opens. */
    fun syncOnOpen() {
        if (syncing) return
        if (!meteo.cityStore.hasStorageAccess()) {
            offerSyncSetup = SynchrotronClient.isInstalled(meteo) && !meteo.cityStore.syncHintDismissed
            return
        }
        stopSync?.invoke()
        stopSync = SynchrotronClient.syncOnOpen(meteo, meteo.cityStore.syncPath) { state ->
            when (state) {
                SyncState.Syncing -> syncing = true
                is SyncState.Done -> {
                    syncing = false
                    if (moveToFolder() || state.changes > 0) reloadCities()
                    if (state.changes > 0) show("Villes synchronisées avec Proton Drive")
                }
                is SyncState.Failed -> { syncing = false; show("Échec de la synchro Proton Drive") }
                SyncState.TimedOut -> { syncing = false; show("Synchro longue : elle continue en arrière-plan") }
                is SyncState.Skipped -> {
                    syncing = false
                    if (state.reason in SKIPS_WITHOUT_REMOTE_DATA && moveToFolder()) reloadCities()
                    offerSyncSetup = state.reason == SkipReason.NOT_REGISTERED && !meteo.cityStore.syncHintDismissed
                    if (state.reason == SkipReason.NEEDS_SETUP) show("Termine la configuration dans Synchrotron")
                }
            }
        }
    }

    private fun moveToFolder(): Boolean = try {
        meteo.cityStore.moveToFolder()
    } catch (e: Exception) {
        storeError = e.message
        false
    }

    /**
     * Registers `Documents/Meteorite` in Synchrotron. Without file access it returns false and the
     * caller opens the permission screen; [onStorageAccessChecked] finishes the setup on return.
     */
    fun setUpSync(): Boolean {
        if (!meteo.cityStore.hasStorageAccess()) {
            setupPending = true
            return false
        }
        setupPending = false
        offerSyncSetup = false
        // The private list moves to the folder after the first sync (CityStore.moveToFolder).
        SynchrotronClient.registerFolder(
            meteo, meteo.cityStore.syncPath, "Météorite",
            remotePath = "Apps/Meteorite", conflictPolicy = "NEWER", intervalMinutes = 360, watchChanges = true,
            exclusions = listOf("*.tmp"),
        )
        return true
    }

    fun onStorageAccessChecked() {
        if (setupPending && meteo.cityStore.hasStorageAccess()) setUpSync()
    }

    fun dismissSyncSetup() {
        offerSyncSetup = false
        meteo.cityStore.syncHintDismissed = true
    }

    /** True when the list cannot be changed now (sync running or unreadable file), with the reason shown. */
    private fun readOnly(): Boolean {
        when {
            syncing -> show("Synchronisation en cours…")
            storeError != null -> show("cities.json est illisible : corrige-le ou supprime-le")
            else -> return false
        }
        return true
    }

    override fun onCleared() {
        stopSync?.invoke()
    }

    fun select(index: Int) {
        selected = index
    }

    /** Loads the selected city's forecast if missing or older than 30 minutes. */
    fun ensureLoaded(manual: Boolean = false) {
        val city = selectedCity ?: return
        if (!city.hasPosition) return
        val old = forecasts[city.id]
        val fresh = old is ForecastState.Ready &&
            System.currentTimeMillis() - old.loadedAt < STALE_MS &&
            old.source.lat == city.lat && old.source.lon == city.lon
        if ((fresh && !manual) || !inFlight.add(city.id)) return
        if (old !is ForecastState.Ready) forecasts[city.id] = ForecastState.Loading
        refreshing = true
        viewModelScope.launch {
            val result = try {
                ForecastState.Ready(meteo.api.forecast(city), System.currentTimeMillis(), city)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ForecastState.Error(describe(e))
            }
            inFlight.remove(city.id)
            refreshing = false
            when {
                // The city moved while loading (position update): load again.
                cities.none { it == city } -> ensureLoaded()
                // Keep showing the saved forecast and say the refresh failed.
                result is ForecastState.Error && old is ForecastState.Ready ->
                    refreshErrors[city.id] = result.message.substringAfterLast("\n")
                else -> {
                    refreshErrors.remove(city.id)
                    forecasts[city.id] = result
                }
            }
        }
    }

    fun checkUpdate() {
        updateJob?.cancel()
        update = UpdateState.Checking
        updateJob = viewModelScope.launch {
            update = try {
                val release = meteo.updater.latest()
                val installed = meteo.updater.installedBuild()
                if (release.build > installed) UpdateState.Available(release, installed)
                else UpdateState.UpToDate(installed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed(describeUpdate(e))
            }
        }
    }

    /** Downloads the APK, then opens Android's install screen. */
    fun downloadUpdate(release: Updater.Release) {
        updateJob?.cancel()
        update = UpdateState.Downloading(0f)
        updateJob = viewModelScope.launch {
            try {
                val file = meteo.updater.download(release) { update = UpdateState.Downloading(it) }
                meteo.updater.install(file)
                update = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update = UpdateState.Failed(describeUpdate(e))
            }
        }
    }

    fun closeUpdate() {
        updateJob?.cancel()
        update = null
    }

    fun openSearch() {
        query = ""
        results = emptyList()
        searchMessage = null
        screen = Screen.Search
    }

    fun onQueryChange(text: String) {
        query = text
        searchJob?.cancel()
        searchMessage = null
        if (text.trim().length < 2) {
            results = emptyList()
            searching = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            searching = true
            try {
                results = meteo.api.search(text.trim())
                searchMessage = if (results.isEmpty()) "Aucune ville trouvée." else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                results = emptyList()
                searchMessage = describe(e)
            } finally {
                searching = false
            }
        }
    }

    fun addCity(city: City) {
        if (readOnly()) return
        val index = cities.indexOfFirst { it.id == city.id }
        if (index >= 0) {
            selected = index
        } else {
            saveCities(cities + city)
            selected = cities.lastIndex
        }
        screen = Screen.Main
    }

    fun removeSelected() {
        val city = selectedCity ?: return
        if (readOnly()) return
        forecasts.remove(city.id)
        refreshErrors.remove(city.id)
        meteo.forecastCache.remove(city)
        saveCities(cities.filter { it.id != city.id })
        selected = selected.coerceAtMost(cities.lastIndex).coerceAtLeast(0)
    }

    fun hasLocationPermission() = meteo.locator.hasPermission()

    /** Adds (or updates) the "Ma position" city from the phone location. */
    fun addMyLocation() {
        if (cities.none { it.isGps } && readOnly()) return
        viewModelScope.launch {
            locating = true
            searchMessage = null
            val location = meteo.locator.locate()
            if (location == null) {
                locating = false
                searchMessage = "Position introuvable. Vérifie que la localisation du téléphone est activée."
                return@launch
            }
            // A sync may have started while locating; the position itself is not in the synced file.
            if (cities.none { it.isGps } && readOnly()) {
                locating = false
                return@launch
            }
            applyMyPosition(meteo.position.city(location))
            locating = false
            selected = cities.indexOfFirst { it.isGps }
            screen = Screen.Main
        }
    }

    fun locationDenied() {
        searchMessage = "Autorisation de localisation refusée."
    }

    /** Follows the phone every minute while the app is open. */
    suspend fun trackMyLocation() {
        while (true) {
            cities.firstOrNull { it.isGps }?.let { old ->
                if (meteo.locator.hasPermission()) {
                    meteo.position.refreshed(old)?.let { applyMyPosition(it) }
                }
            }
            delay(60_000)
        }
    }

    private fun applyMyPosition(mine: City) {
        val index = cities.indexOfFirst { it.isGps }
        // The previous forecast stays on screen until the one for the new place arrives.
        refreshErrors.remove(City.GPS_ID)
        saveCities(
            if (index >= 0) cities.toMutableList().also { it[index] = mine } else cities + mine
        )
    }

    /** Moves a tab while it is dragged; the first tab is the one shown by the widget. */
    fun moveCity(from: Int, to: Int) {
        if (syncing || storeError != null) return
        val keep = selectedCity?.id
        val list = cities.toMutableList().apply { add(to, removeAt(from)) }
        cities = list
        selected = list.indexOfFirst { it.id == keep }.coerceAtLeast(0)
    }

    /** Called when a tab drag ends: saves the order once, and the widget picks the new first city. */
    fun orderChanged() {
        if (readOnly()) {
            reloadCities()
            return
        }
        saveCities(cities)
    }

    fun recheckPermissions() {
        backgroundOk = meteo.locator.hasBackgroundPermission()
    }

    private fun saveCities(list: List<City>) {
        cities = list
        try {
            meteo.cityStore.save(list)
        } catch (e: Exception) {
            storeError = e.message
        }
        WeatherWidget.requestUpdate(meteo)
    }

    private fun describeUpdate(e: Exception) =
        "Mise à jour impossible.\n(${e.javaClass.simpleName}: ${e.message})"

    private fun describe(e: Exception) =
        "Impossible de récupérer la météo. Vérifie ta connexion internet.\n" +
            "(${e.javaClass.simpleName}: ${e.message})"

    private companion object {
        const val STALE_MS = 30 * 60 * 1000L

        /** Nothing more is coming from Proton Drive: the private list can move to the synced folder. */
        val SKIPS_WITHOUT_REMOTE_DATA = setOf(SkipReason.NOT_INSTALLED, SkipReason.NOT_REGISTERED, SkipReason.FRESH, SkipReason.COOLDOWN)
    }
}
