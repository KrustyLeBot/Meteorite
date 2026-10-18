package app.meteorite.data

import android.content.Context
import org.json.JSONObject
import kotlin.math.abs

/** Last downloaded forecast of each city (raw API JSON), so the app and widget never start empty. */
class ForecastCache(context: Context) {
    private val prefs = context.getSharedPreferences("forecasts", Context.MODE_PRIVATE)

    fun save(city: City, json: String) {
        val entry = JSONObject()
            .put("lat", city.lat)
            .put("lon", city.lon)
            .put("at", System.currentTimeMillis())
            .put("data", json)
        prefs.edit().putString(city.id, entry.toString()).apply()
    }

    fun load(city: City): Cached? {
        val raw = prefs.getString(city.id, null) ?: return null
        return try {
            val entry = JSONObject(raw)
            // A "Ma position" that moved must not reuse the old place's forecast.
            if (abs(entry.getDouble("lat") - city.lat) > 1e-6 ||
                abs(entry.getDouble("lon") - city.lon) > 1e-6
            ) return null
            Cached(
                WeatherApi.parseForecast(JSONObject(entry.getString("data"))),
                entry.getLong("at"),
            )
        } catch (e: Exception) {
            null
        }
    }

    fun remove(city: City) {
        prefs.edit().remove(city.id).apply()
    }
}
