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
    data class Ready(val forecast: Forecast, val loadedAt: Long) : ForecastState
}

class MeteoViewModel(app: Application) : AndroidViewModel(app) {
    private val meteo = app as MeteoApp

    var cities by mutableStateOf(meteo.cityStore.load())
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

    val selectedCity get() = cities.getOrNull(selected)

    private val inFlight = mutableSetOf<String>()
    private var searchJob: Job? = null

    init {
        cities.forEach { city ->
            meteo.forecastCache.load(city)?.let {
                forecasts[city.id] = ForecastState.Ready(it.forecast, it.savedAt)
            }
        }
    }

    fun select(index: Int) {
        selected = index
    }

    /** Loads the selected city's forecast if missing or older than 30 minutes. */
    fun ensureLoaded(manual: Boolean = false) {
        val city = selectedCity ?: return
        val old = forecasts[city.id]
        val fresh = old is ForecastState.Ready &&
            System.currentTimeMillis() - old.loadedAt < STALE_MS
        if ((fresh && !manual) || !inFlight.add(city.id)) return
        if (old !is ForecastState.Ready) forecasts[city.id] = ForecastState.Loading
        refreshing = true
        viewModelScope.launch {
            val result = try {
                ForecastState.Ready(meteo.api.forecast(city), System.currentTimeMillis())
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
        forecasts.remove(city.id)
        refreshErrors.remove(city.id)
        meteo.forecastCache.remove(city)
        saveCities(cities.filter { it.id != city.id })
        selected = selected.coerceAtMost(cities.lastIndex).coerceAtLeast(0)
    }

    fun hasLocationPermission() = meteo.locator.hasPermission()

    /** Adds (or updates) the "Ma position" city from the phone location. */
    fun addMyLocation() {
        viewModelScope.launch {
            locating = true
            searchMessage = null
            val location = meteo.locator.locate()
            if (location == null) {
                locating = false
                searchMessage = "Position introuvable. Vérifie que la localisation du téléphone est activée."
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
        val old = cities.getOrNull(index)
        // A different place needs a new forecast; a newly found town name does not.
        if (old == null || old.lat != mine.lat || old.lon != mine.lon) {
            forecasts.remove(City.GPS_ID)
            refreshErrors.remove(City.GPS_ID)
        }
        saveCities(
            if (index >= 0) cities.toMutableList().also { it[index] = mine } else cities + mine
        )
    }

    /** Moves a tab; the first tab is the one shown by the widget. */
    fun moveCity(from: Int, to: Int) {
        val keep = selectedCity?.id
        val list = cities.toMutableList().apply { add(to, removeAt(from)) }
        cities = list
        meteo.cityStore.save(list)
        selected = list.indexOfFirst { it.id == keep }.coerceAtLeast(0)
    }

    /** Called when a tab drag ends, so the widget picks the new first city. */
    fun orderChanged() = WeatherWidget.requestUpdate(meteo)

    fun recheckPermissions() {
        backgroundOk = meteo.locator.hasBackgroundPermission()
    }

    private fun saveCities(list: List<City>) {
        cities = list
        meteo.cityStore.save(list)
        WeatherWidget.requestUpdate(meteo)
    }

    private fun describeUpdate(e: Exception) =
        "Mise à jour impossible.\n(${e.javaClass.simpleName}: ${e.message})"

    private fun describe(e: Exception) =
        "Impossible de récupérer la météo. Vérifie ta connexion internet.\n" +
            "(${e.javaClass.simpleName}: ${e.message})"

    private companion object {
        const val STALE_MS = 30 * 60 * 1000L
    }
}
