package app.meteorite

import android.app.Application
import app.meteorite.data.CityStore
import app.meteorite.data.ForecastCache
import app.meteorite.data.Locator
import app.meteorite.data.MyPosition
import app.meteorite.data.WeatherApi
import app.meteorite.suite.SuiteUpdater
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MeteoApp : Application() {
    val position by lazy { MyPosition(locator, api) }
    val cityStore by lazy { CityStore(this) }
    val forecastCache by lazy { ForecastCache(this) }
    val locator by lazy { Locator(this) }
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
    val api by lazy { WeatherApi(http, forecastCache) }
    val updater by lazy { SuiteUpdater(this, "KrustyLeBot/Meteorite", "$packageName.fileprovider") }
}
