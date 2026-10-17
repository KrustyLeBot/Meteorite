package app.meteorite

import android.app.Application
import app.meteorite.data.CityStore
import app.meteorite.data.Locator
import app.meteorite.data.WeatherApi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MeteoApp : Application() {
    val cityStore by lazy { CityStore(this) }
    val locator by lazy { Locator(this) }
    val api by lazy {
        WeatherApi(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        )
    }
}
