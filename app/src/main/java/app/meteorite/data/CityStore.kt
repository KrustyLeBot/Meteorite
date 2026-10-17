package app.meteorite.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Saved cities, kept as JSON in SharedPreferences. */
class CityStore(context: Context) {
    private val prefs = context.getSharedPreferences("cities", Context.MODE_PRIVATE)

    fun load(): List<City> {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            City(
                id = o.getString("id"),
                name = o.getString("name"),
                subtitle = o.optString("subtitle"),
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
            )
        }
    }

    fun save(cities: List<City>) {
        val arr = JSONArray()
        cities.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("subtitle", it.subtitle)
                    .put("lat", it.lat)
                    .put("lon", it.lon)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "list"
    }
}
