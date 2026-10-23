package app.meteorite.data

import android.content.Context
import org.json.JSONObject

/** Last downloaded forecast of each city (raw API JSON), so the app and widget never start empty. */
class ForecastCache(context: Context) {
    private val prefs = context.getSharedPreferences("forecasts", Context.MODE_PRIVATE)

    fun save(city: City, json: String) {
        val entry = JSONObject()
            .put("lat", city.lat)
            .put("lon", city.lon)
            .put("name", city.subtitle)
            .put("at", System.currentTimeMillis())
            .put("data", json)
        prefs.edit().putString(city.id, entry.toString()).apply()
    }

    fun load(city: City): Cached? {
        val raw = prefs.getString(city.id, null) ?: return null
        return try {
            val entry = JSONObject(raw)
            // After a move, the forecast of the previous place is still returned (better than
            // nothing); `source` tells which place it is for.
            Cached(
                WeatherApi.parseForecast(JSONObject(entry.getString("data"))),
                entry.getLong("at"),
                city.copy(
                    subtitle = entry.optString("name"),
                    lat = entry.getDouble("lat"),
                    lon = entry.getDouble("lon"),
                ),
            )
        } catch (e: Exception) {
            null
        }
    }

    fun remove(city: City) {
        prefs.edit().remove(city.id).apply()
    }
}
