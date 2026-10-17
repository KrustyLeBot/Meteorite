package app.meteorite.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Open-Meteo client (free, no API key). */
class WeatherApi(private val client: OkHttpClient) {

    suspend fun search(query: String): List<City> {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", query)
            .addQueryParameter("count", "8")
            .addQueryParameter("language", "fr")
            .build()
        val arr = get(url).optJSONArray("results") ?: return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            City(
                id = "g${o.getLong("id")}",
                name = o.getString("name"),
                subtitle = listOf(o.optString("admin1"), o.optString("country"))
                    .filter { it.isNotBlank() }
                    .joinToString(", "),
                lat = o.getDouble("latitude"),
                lon = o.getDouble("longitude"),
            )
        }
    }

    suspend fun forecast(city: City): Forecast {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", city.lat.toString())
            .addQueryParameter("longitude", city.lon.toString())
            .addQueryParameter(
                "current", "temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m"
            )
            .addQueryParameter(
                "hourly", "temperature_2m,weather_code,precipitation,wind_speed_10m,is_day"
            )
            .addQueryParameter(
                "daily",
                "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum," +
                    "precipitation_hours,wind_speed_10m_max,sunrise,sunset"
            )
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "16")
            .build()
        val json = get(url)

        val c = json.getJSONObject("current")
        val current = Current(
            time = LocalDateTime.parse(c.getString("time")),
            temp = c.getDouble("temperature_2m").roundToInt(),
            feelsLike = c.getDouble("apparent_temperature").roundToInt(),
            code = c.getInt("weather_code"),
            isDay = c.getInt("is_day") == 1,
            windKmh = c.getDouble("wind_speed_10m").roundToInt(),
        )

        val h = json.getJSONObject("hourly")
        val hTime = h.getJSONArray("time")
        val hTemp = h.getJSONArray("temperature_2m")
        val hCode = h.getJSONArray("weather_code")
        val hRain = h.getJSONArray("precipitation")
        val hWind = h.getJSONArray("wind_speed_10m")
        val hDay = h.getJSONArray("is_day")
        val hours = List(hTime.length()) { i ->
            Hour(
                time = LocalDateTime.parse(hTime.getString(i)),
                temp = hTemp.num(i).roundToInt(),
                code = hCode.num(i).roundToInt(),
                rainMm = hRain.num(i),
                windKmh = hWind.num(i).roundToInt(),
                isDay = hDay.num(i) > 0.5,
            )
        }
        val hoursByDate = hours.groupBy { it.time.toLocalDate() }

        val d = json.getJSONObject("daily")
        val dTime = d.getJSONArray("time")
        val dCode = d.getJSONArray("weather_code")
        val dMax = d.getJSONArray("temperature_2m_max")
        val dMin = d.getJSONArray("temperature_2m_min")
        val dRain = d.getJSONArray("precipitation_sum")
        val dRainHours = d.getJSONArray("precipitation_hours")
        val dWind = d.getJSONArray("wind_speed_10m_max")
        val dSunrise = d.getJSONArray("sunrise")
        val dSunset = d.getJSONArray("sunset")
        val days = List(dTime.length()) { i ->
            val date = LocalDate.parse(dTime.getString(i))
            Day(
                date = date,
                code = dCode.num(i).roundToInt(),
                tMax = dMax.num(i).roundToInt(),
                tMin = dMin.num(i).roundToInt(),
                rainMm = dRain.num(i),
                rainHours = dRainHours.num(i).roundToInt(),
                windKmh = dWind.num(i).roundToInt(),
                sunrise = LocalDateTime.parse(dSunrise.getString(i)).toLocalTime(),
                sunset = LocalDateTime.parse(dSunset.getString(i)).toLocalTime(),
                hours = hoursByDate[date].orEmpty(),
            )
        }
        return Forecast(current, days)
    }

    /** Numeric value at [i], 0 when the API returned null. */
    private fun JSONArray.num(i: Int): Double = if (isNull(i)) 0.0 else getDouble(i)

    private suspend fun get(url: HttpUrl): JSONObject = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Erreur du service météo (${response.code})")
            JSONObject(body)
        }
    }
}
