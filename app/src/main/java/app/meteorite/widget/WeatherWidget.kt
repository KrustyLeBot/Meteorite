package app.meteorite.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.meteorite.MainActivity
import app.meteorite.MeteoApp
import app.meteorite.R
import app.meteorite.data.City
import app.meteorite.data.Forecast
import app.meteorite.restOfDaySentence
import app.meteorite.updatedText
import app.meteorite.weatherEmoji
import app.meteorite.weatherLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Home-screen widget: weather now and the next hours for the first saved city. */
class WeatherWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MeteoApp
                fun push(views: RemoteViews) = ids.forEach { manager.updateAppWidget(it, views) }

                // Show the saved forecast right away, then try to refresh it.
                val first = app.cityStore.load().firstOrNull()
                val saved = first?.let { app.forecastCache.load(it) }
                push(render(context, first, saved?.forecast, saved?.savedAt, null, saved?.source))

                // "Ma position" follows the phone even when the app is closed.
                var city = first
                if (first != null && first.isGps) {
                    val moved = try {
                        withTimeout(15_000) { app.position.refreshed(first) }
                    } catch (e: Exception) {
                        null
                    }
                    if (moved != null) {
                        app.cityStore.upsert(moved)
                        city = moved
                    }
                }
                if (city != null) {
                    val cached = if (city === first) saved else app.forecastCache.load(city)
                    if (city !== first) {
                        push(render(context, city, cached?.forecast, cached?.savedAt, null, cached?.source))
                    }
                    try {
                        val fresh = withTimeout(12_000) { app.api.forecast(city) }
                        push(render(context, city, fresh, System.currentTimeMillis(), null, city))
                    } catch (e: Exception) {
                        push(render(context, city, cached?.forecast, cached?.savedAt, e.javaClass.simpleName, cached?.source))
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** [failure] is the name of the error when the last refresh did not work. */
    private fun render(
        context: Context,
        city: City?,
        forecast: Forecast?,
        savedAt: Long?,
        failure: String?,
        source: City?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_weather)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        clear(views)
        if (city == null) {
            views.setTextViewText(R.id.w_city, "Météorite")
            views.setTextViewText(R.id.w_desc, "Ouvre l'application pour ajouter une ville")
            return views
        }
        // The pin alone marks the live position; "Ma position" is kept only while the town is unknown.
        // Name of the place the shown forecast is for (the previous one right after a move).
        val shown = source ?: city
        views.setTextViewText(
            R.id.w_city,
            if (shown.isGps) "📍 ${shown.subtitle.ifBlank { shown.name }}" else shown.name
        )
        if (forecast == null || savedAt == null) {
            views.setTextViewText(R.id.w_desc, if (failure == null) "Chargement…" else "Pas encore de météo")
            if (failure != null) views.setTextViewText(R.id.w_updated, "Erreur : $failure")
            return views
        }
        val rolled = forecast.rolled()
        val now = rolled.current
        views.setInt(R.id.widget_root, "setBackgroundResource", background(now.code, now.isDay))
        views.setTextViewText(R.id.w_emoji, weatherEmoji(now.code, now.isDay))
        views.setTextViewText(R.id.w_temp, "${now.temp}°")
        views.setTextViewText(R.id.w_desc, weatherLabel(now.code))
        views.setTextViewText(
            R.id.w_updated,
            updatedText(savedAt) + (failure?.let { " · actualisation impossible" } ?: "")
        )
        views.setTextViewText(R.id.w_rain, restOfDaySentence(rolled.todayRemaining(), now.time))
        // Every second hour, so six slots cover the next twelve hours.
        rolled.upcoming().filterIndexed { i, _ -> i % 2 == 1 }.take(SLOTS.size)
            .forEachIndexed { i, hour ->
                val (h, e, t) = SLOTS[i]
                views.setTextViewText(h, "${hour.time.hour} h")
                views.setTextViewText(e, weatherEmoji(hour.code, hour.isDay))
                views.setTextViewText(t, "${hour.temp}°")
            }
        return views
    }

    /** Background gradient matching the current weather. */
    private fun background(code: Int, isDay: Boolean) = when {
        code in 95..99 -> R.drawable.widget_bg_storm
        code in 71..77 || code == 85 || code == 86 -> R.drawable.widget_bg_snow
        code in 51..67 || code in 80..82 -> R.drawable.widget_bg_rain
        !isDay -> R.drawable.widget_bg_night
        code == 3 || code == 45 || code == 48 -> R.drawable.widget_bg_cloudy
        else -> R.drawable.widget_bg_sunny
    }

    private fun clear(views: RemoteViews) {
        listOf(R.id.w_emoji, R.id.w_temp, R.id.w_city, R.id.w_desc, R.id.w_updated, R.id.w_rain).forEach {
            views.setTextViewText(it, "")
        }
        SLOTS.forEach { (h, e, t) ->
            views.setTextViewText(h, "")
            views.setTextViewText(e, "")
            views.setTextViewText(t, "")
        }
    }

    companion object {
        private val SLOTS = listOf(
            Triple(R.id.s0_h, R.id.s0_e, R.id.s0_t),
            Triple(R.id.s1_h, R.id.s1_e, R.id.s1_t),
            Triple(R.id.s2_h, R.id.s2_e, R.id.s2_t),
            Triple(R.id.s3_h, R.id.s3_e, R.id.s3_t),
            Triple(R.id.s4_h, R.id.s4_e, R.id.s4_t),
            Triple(R.id.s5_h, R.id.s5_e, R.id.s5_t),
        )

        /** Asks every placed widget to refresh (no-op when none is placed). */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, WeatherWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }
}
