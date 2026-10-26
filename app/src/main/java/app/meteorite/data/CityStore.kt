package app.meteorite.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Saved cities, as `cities.json` in the public `Documents/Meteorite/` folder so that Synchrotron
 * can sync it with Proton Drive and it survives a reinstall. That folder needs the "all files
 * access" permission; until it is granted, and until the first sync after that, a private file
 * is used (see [moveToFolder]).
 *
 * "Ma position" is only a placeholder in the file (`{"id":"gps"}`, for its tab position): its
 * coordinates belong to this phone and change all the time, so they stay in private prefs and
 * moving around never rewrites the synced file.
 *
 * An unreadable file raises an error and is never overwritten.
 */
class CityStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("cities", Context.MODE_PRIVATE)

    private val publicDir: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Meteorite")

    private val privateFile: File
        get() = File(context.filesDir, FILE_NAME)

    /** Folder that Synchrotron syncs with Proton Drive (only meaningful with [hasStorageAccess]). */
    val syncPath: String get() = publicDir.path

    fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private val file: File
        get() = if (hasStorageAccess() && !privateFile.exists()) File(publicDir, FILE_NAME) else privateFile

    /** Older versions kept the list in SharedPreferences. */
    private fun migrateLegacy() {
        if (prefs.contains(LEGACY_KEY)) {
            val arr = JSONArray(prefs.getString(LEGACY_KEY, "[]"))
            val legacy = List(arr.length()) { parse(arr.getJSONObject(it)) }
            legacy.firstOrNull { it.isGps }?.let { saveMyPosition(it) }
            if (!privateFile.exists()) write(privateFile, legacy)
            prefs.edit().remove(LEGACY_KEY).apply()
        }
    }

    /**
     * Moves the private list to the public folder. Called once Proton Drive's copy has been pulled
     * (or there is none): moved before, it would be the newer file and replace the saved list.
     * An existing public file keeps its order and only gets the cities it lacks, so nothing is lost.
     * @return true when the list changed.
     */
    @Synchronized
    fun moveToFolder(): Boolean {
        if (!hasStorageAccess() || !privateFile.exists()) return false
        val target = File(publicDir, FILE_NAME)
        val mine = read(privateFile)
        val merged = if (target.exists()) read(target).let { theirs -> theirs + mine.filter { m -> theirs.none { it.id == m.id } } } else mine
        write(target, merged)
        privateFile.delete()
        return true
    }

    @Synchronized
    fun load(): List<City> {
        migrateLegacy()
        val f = file
        return if (f.exists()) read(f) else emptyList()
    }

    private fun read(f: File): List<City> {
        val arr = readRoot(f).optJSONArray("cities") ?: JSONArray()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            if (o.getString("id") == City.GPS_ID) myPosition() else parse(o)
        }
    }

    /** Replaces the city with the same id, or appends it. */
    @Synchronized
    fun upsert(city: City): List<City> {
        val list = load()
        val index = list.indexOfFirst { it.id == city.id }
        val result = if (index >= 0) list.toMutableList().also { it[index] = city } else list + city
        save(result)
        return result
    }

    @Synchronized
    fun save(cities: List<City>) {
        cities.firstOrNull { it.isGps }?.let { saveMyPosition(it) }
        val f = file
        // Rewriting an identical file would still wake Synchrotron's watcher for nothing.
        val current = if (f.exists()) readRoot(f).optJSONArray("cities")?.toString() else null
        if (current != toJson(cities).toString()) write(f, cities)
    }

    private fun readRoot(f: File): JSONObject = try {
        JSONObject(f.readText())
    } catch (e: Exception) {
        throw IllegalStateException("${f.path} est illisible (${e.message}). Il ne sera pas modifié.", e)
    }

    private fun write(target: File, cities: List<City>) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(JSONObject().put("format_version", FORMAT_VERSION).put("cities", toJson(cities)).toString(2))
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IllegalStateException("Impossible d'enregistrer ${target.path}")
        }
    }

    private fun toJson(cities: List<City>) = JSONArray().also { arr ->
        cities.forEach {
            arr.put(
                if (it.isGps) JSONObject().put("id", City.GPS_ID)
                else JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("subtitle", it.subtitle)
                    .put("lat", it.lat)
                    .put("lon", it.lon)
            )
        }
    }

    private fun parse(o: JSONObject) = City(
        id = o.getString("id"),
        name = o.getString("name"),
        subtitle = o.optString("subtitle"),
        lat = o.getDouble("lat"),
        lon = o.getDouble("lon"),
    )

    /** This phone's last known position; (0, 0) until it is located, which the app does on start. */
    private fun myPosition() = prefs.getString(GPS_KEY, null)?.let { parse(JSONObject(it)) }
        ?: City(City.GPS_ID, "Ma position", "", 0.0, 0.0)

    private fun saveMyPosition(city: City) {
        val json = JSONObject().put("id", city.id).put("name", city.name).put("subtitle", city.subtitle)
            .put("lat", city.lat).put("lon", city.lon).toString()
        prefs.edit().putString(GPS_KEY, json).apply()
    }

    private companion object {
        const val FILE_NAME = "cities.json"
        const val FORMAT_VERSION = 1
        const val LEGACY_KEY = "list"
        const val GPS_KEY = "gps"
    }
}
