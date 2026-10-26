package app.meteorite

import android.app.Application
import app.meteorite.data.CityStore
import app.meteorite.data.ForecastCache
import app.meteorite.data.Locator
import app.meteorite.data.MyPosition
import app.meteorite.data.WeatherApi
import app.meteorite.suite.SuiteUpdater
import app.meteorite.widget.WeatherWidget
import app.meteorite.widget.WidgetWorker
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MeteoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Widgets placed before the 15-minute refresh existed get it too.
        if (WeatherWidget.isPlaced(this)) WidgetWorker.schedule(this)
    }

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
