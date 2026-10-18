package app.meteorite.data

import android.location.Location
import kotlinx.coroutines.CancellationException

/** Builds and refreshes the "Ma position" city from the phone location. */
class MyPosition(private val locator: Locator, private val api: WeatherApi) {

    suspend fun city(location: Location) = City(
        City.GPS_ID, "Ma position", placeName(location.latitude, location.longitude),
        location.latitude, location.longitude,
    )

    /**
     * The updated "Ma position" when the phone moved more than 1 km from [old] (or when its town
     * name was still missing), null when nothing changed.
     */
    suspend fun refreshed(old: City): City? {
        val location = locator.locate() ?: return null
        val moved = FloatArray(1)
        Location.distanceBetween(old.lat, old.lon, location.latitude, location.longitude, moved)
        if (moved[0] >= MIN_MOVE_METERS) return city(location)
        if (old.subtitle.isNotBlank()) return null
        val name = placeName(old.lat, old.lon)
        return if (name.isBlank()) null else old.copy(subtitle = name)
    }

    private suspend fun placeName(lat: Double, lon: Double): String = try {
        api.placeName(lat, lon)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ""
    }

    private companion object {
        const val MIN_MOVE_METERS = 1_000f
    }
}
