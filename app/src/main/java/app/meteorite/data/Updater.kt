package app.meteorite.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Looks for a newer APK in the GitHub "latest" release, downloads it and starts the install. */
class Updater(private val context: Context, private val client: OkHttpClient) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val dir get() = File(context.cacheDir, "updates")

    data class Release(val build: Long, val url: String, val sizeBytes: Long)

    /** The build number is the CI run number, which is also the APK versionCode. */
    fun installedBuild(): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) throw IOException("Aucune version publiée pour l'instant.")
            if (!response.isSuccessful) throw IOException("GitHub a répondu ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            val build = Regex("\\d+").find(json.getString("name"))?.value?.toLong()
                ?: throw IOException("Numéro de version introuvable")
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name") == APK_NAME }
                ?: throw IOException("APK introuvable dans la version")
            Release(build, apk.getString("browser_download_url"), apk.getLong("size"))
        }
    }

    suspend fun download(release: Release, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val file = File(dir, APK_NAME)
            client.newCall(Request.Builder().url(release.url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Téléchargement impossible (${response.code})")
                val body = response.body ?: throw IOException("Réponse vide")
                val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            ensureActive()
                            onProgress(done.toFloat() / total)
                        }
                    }
                }
            }
            prefs.edit().putLong(PENDING_KEY, release.build).apply()
            file
        }

    /** Opens Android's usual install screen for the downloaded APK. */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Deletes the downloaded APK once the update is installed (the new version starts and sees its
     * build number reach the downloaded one), or when it is more than a day old (install cancelled).
     */
    fun cleanup() {
        val files = dir.listFiles() ?: return
        val installed = installedBuild() >= prefs.getLong(PENDING_KEY, 0)
        val old = files.all { System.currentTimeMillis() - it.lastModified() > DAY_MS }
        if (installed || old) {
            dir.deleteRecursively()
            prefs.edit().remove(PENDING_KEY).apply()
        }
    }

    private companion object {
        const val REPO = "KrustyLeBot/Meteorite"
        const val APK_NAME = "Meteorite.apk"
        const val PENDING_KEY = "pendingBuild"
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
