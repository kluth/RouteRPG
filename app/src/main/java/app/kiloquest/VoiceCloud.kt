package app.kiloquest

import android.content.Context
import android.content.SharedPreferences
import app.kiloquest.nav.VoicePack
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Zugang zum eigenen Stimmen-Proxy (tools/voice-proxy, Spec 016). Wie [ServerConfig]: URL und Token nur auf dem Geraet,
 * nur https, keine Vorgabe im Build. Ohne beides (oder ohne Einwilligung in den Einstellungen) wird nichts gesendet.
 */
object VoiceCloudConfig {
    private const val KEY_URL = "voice_url"
    private const val KEY_TOKEN = "voice_token"
    @Volatile var url = ""
        private set
    @Volatile var token = ""
        private set
    val configured: Boolean get() = url.isNotBlank() && token.isNotBlank()

    fun load(p: SharedPreferences) {
        url = p.getString(KEY_URL, "") ?: ""
        token = p.getString(KEY_TOKEN, "") ?: ""
    }

    fun save(p: SharedPreferences, newUrl: String, newToken: String): Boolean {
        val u = newUrl.trim()
        if (u.isNotEmpty() && !u.startsWith("https://", ignoreCase = true)) return false
        p.edit().putString(KEY_URL, u).putString(KEY_TOKEN, newToken.trim().ifBlank { token }).apply()
        load(p)
        return true
    }
}

/** Abruf eines Namens-Clips (mp3) vom eigenen Proxy; gesendet wird nur der Name und die Sprache, nie Position oder Kindernamen. */
object VoiceCloud {
    suspend fun fetchName(lang: String, text: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(VoiceCloudConfig.url.trimEnd('/') + "/tts").openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.connectTimeout = 10_000
                c.readTimeout = 30_000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("X-Api-Token", VoiceCloudConfig.token)
                c.outputStream.use { it.write(JSONObject().put("text", text).put("lang", lang).toString().toByteArray()) }
                if (c.responseCode == 200) c.inputStream.use { it.readBytes() }.takeIf { it.size > 1000 } else null
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }
}

/** Ablage der Namens-Clips: `filesDir/voice_cache/<lang>/<hash>.mp3` (Hash wie [VoicePack.nameHash]); wird vom Player gelesen. */
class NameClips(private val ctx: Context) {
    fun file(lang: String, text: String) = File(ctx.filesDir, "voice_cache/$lang/${VoicePack.nameHash(text)}.mp3")
    fun has(lang: String, text: String) = file(lang, text).length() > 0
    fun save(lang: String, text: String, bytes: ByteArray) {
        val f = file(lang, text)
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(f)
    }
}
