package app.meteorite.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/** Open-Meteo client (free, no API key). */
class WeatherApi(private val client: OkHttpClient, private val cache: ForecastCache) {

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

    /** Name of the town at these coordinates (OpenStreetMap Nominatim), empty when unknown. */
    suspend fun placeName(lat: Double, lon: Double): String {
        val url = "https://nominatim.openstreetmap.org/reverse".toHttpUrl().newBuilder()
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("lat", lat.toString())
            .addQueryParameter("lon", lon.toString())
            .addQueryParameter("zoom", "12")
            .addQueryParameter("addressdetails", "1")
            .addQueryParameter("accept-language", "fr")
            .build()
        val address = get(url, USER_AGENT).optJSONObject("address") ?: return ""
        return listOf("city", "town", "village", "municipality", "hamlet", "suburb")
            .map { address.optString(it) }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    /** Downloads the forecast and keeps a copy on the phone. */
    suspend fun forecast(city: City): Forecast {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", city.lat.toString())
            .addQueryParameter("longitude", city.lon.toString())
            .addQueryParameter(
                "current", "temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m"
            )
            .addQueryParameter(
                "hourly",
                "temperature_2m,apparent_temperature,weather_code,rain,showers,snowfall," +
                    "wind_speed_10m,is_day"
            )
            .addQueryParameter(
                "daily",
                "weather_code,temperature_2m_max,temperature_2m_min,rain_sum,showers_sum," +
                    "snowfall_sum,precipitation_hours,wind_speed_10m_max,sunrise,sunset"
            )
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "16")
            .build()
        val json = get(url)
        val forecast = parseForecast(json)
        cache.save(city, json.toString())
        return forecast
    }

    /**
     * Phones often get the network back slowly (waking from sleep, switching networks): a failed
     * request is tried again after 2 s and 5 s before giving up.
     */
    private suspend fun get(url: HttpUrl, userAgent: String? = null): JSONObject {
        for (wait in RETRY_DELAYS_MS) {
            try {
                return getOnce(url, userAgent)
            } catch (e: IOException) {
                delay(wait)
            }
        }
        return getOnce(url, userAgent)
    }

    private suspend fun getOnce(url: HttpUrl, userAgent: String? = null): JSONObject =
        suspendCancellableCoroutine { cont ->
        val request = Request.Builder().url(url)
            .apply { userAgent?.let { header("User-Agent", it) } }
            .build()
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val body = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            throw IOException("Erreur du service météo (${it.code})")
                        }
                        cont.resume(JSONObject(body))
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            }
        })
    }

    companion object {
        /** Nominatim requires an identifying User-Agent. */
        private const val USER_AGENT = "Meteorite/1.0 (Android weather app)"
        private val RETRY_DELAYS_MS = listOf(2_000L, 5_000L)

        fun parseForecast(json: JSONObject): Forecast {
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
            val hFeels = h.optJSONArray("apparent_temperature")
            val hCode = h.getJSONArray("weather_code")
            val hRain = h.getJSONArray("rain")
            val hShowers = h.getJSONArray("showers")
            val hSnow = h.getJSONArray("snowfall")
            val hWind = h.getJSONArray("wind_speed_10m")
            val hDay = h.getJSONArray("is_day")
            val hours = List(hTime.length()) { i ->
                Hour(
                    time = LocalDateTime.parse(hTime.getString(i)),
                    temp = hTemp.num(i).roundToInt(),
                    feelsLike = (hFeels ?: hTemp).num(i).roundToInt(),
                    code = hCode.num(i).roundToInt(),
                    rainMm = hRain.num(i) + hShowers.num(i),
                    snowCm = hSnow.num(i),
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
            val dRain = d.getJSONArray("rain_sum")
            val dShowers = d.getJSONArray("showers_sum")
            val dSnow = d.getJSONArray("snowfall_sum")
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
                    rainMm = dRain.num(i) + dShowers.num(i),
                    rainHours = dRainHours.num(i).roundToInt(),
                    snowCm = dSnow.num(i),
                    windKmh = dWind.num(i).roundToInt(),
                    sunrise = LocalDateTime.parse(dSunrise.getString(i)).toLocalTime(),
                    sunset = LocalDateTime.parse(dSunset.getString(i)).toLocalTime(),
                    hours = hoursByDate[date].orEmpty(),
                )
            }
            return Forecast(current, days, json.getInt("utc_offset_seconds"))
        }
    }
}

/** Numeric value at [i], 0 when the API returned null. */
private fun JSONArray.num(i: Int): Double = if (isNull(i)) 0.0 else getDouble(i)
