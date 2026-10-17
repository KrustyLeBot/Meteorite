package app.meteorite

import android.app.Application
import android.location.Location
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
    var refreshing by mutableStateOf(false)
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

    val selectedCity get() = cities.getOrNull(selected)

    private val inFlight = mutableSetOf<String>()
    private var searchJob: Job? = null

    init {
        refreshMyLocation()
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
                result is ForecastState.Error && old is ForecastState.Ready && !manual -> Unit
                else -> forecasts[city.id] = result
            }
        }
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
            locating = false
            if (location == null) {
                searchMessage = "Position introuvable. Vérifie que la localisation du téléphone est activée."
                return@launch
            }
            setMyLocation(location)
            selected = cities.indexOfFirst { it.isGps }
            screen = Screen.Main
        }
    }

    fun locationDenied() {
        searchMessage = "Autorisation de localisation refusée."
    }

    /** Keeps "Ma position" up to date when the app opens. */
    private fun refreshMyLocation() {
        if (cities.none { it.isGps } || !meteo.locator.hasPermission()) return
        viewModelScope.launch { meteo.locator.locate()?.let { setMyLocation(it) } }
    }

    private fun setMyLocation(location: Location) {
        val mine = City(City.GPS_ID, "Ma position", "", location.latitude, location.longitude)
        val index = cities.indexOfFirst { it.isGps }
        if (index >= 0) {
            val old = cities[index]
            val moved = FloatArray(1)
            Location.distanceBetween(old.lat, old.lon, mine.lat, mine.lon, moved)
            if (moved[0] < 2_000) return
        }
        forecasts.remove(City.GPS_ID)
        saveCities(
            if (index >= 0) cities.toMutableList().also { it[index] = mine } else cities + mine
        )
    }

    private fun saveCities(list: List<City>) {
        cities = list
        meteo.cityStore.save(list)
        WeatherWidget.requestUpdate(meteo)
    }

    private fun describe(e: Exception) =
        "Impossible de récupérer la météo. Vérifie ta connexion internet.\n" +
            "(${e.javaClass.simpleName}: ${e.message})"

    private companion object {
        const val STALE_MS = 30 * 60 * 1000L
    }
}
