package app.kiloquest

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import app.kiloquest.game.VoiceIndex
import java.util.Locale

/**
 * Spielt aufgenommene Kilo-Saetze (assets/kilo_voice, siehe tools/voice) statt der Geraete-Sprachausgabe, sobald ein gesprochener
 * Text genau zu einer Aufnahme passt. Alles andere (andere Sprache, geaenderter Text, dynamische Aufgaben) bleibt bei der
 * Sprachausgabe: [play] gibt dann false zurueck. Aufnahmen werden nacheinander abgespielt und sofort gestoppt, wenn eine
 * Navigationsansage kommt ([stop]).
 */
class KiloVoicePlayer(private val context: Context) {
    private val index: VoiceIndex by lazy {
        VoiceIndex.parse(runCatching { context.assets.open("kilo_voice/manifest.json").bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull())
    }
    private val audio = context.getSystemService(AudioManager::class.java)
    private val queue = ArrayDeque<String>()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    val isPlaying: Boolean get() = player != null || queue.isNotEmpty()

    /** true, wenn es zu [text] eine Aufnahme gibt (sie wird abgespielt oder eingereiht). */
    fun play(text: String): Boolean {
        val lang = Locale.getDefault().language
        val id = index.idFor(lang, text) ?: return false
        val path = "kilo_voice/$lang/$id.ogg"
        if (!exists(path)) return false
        queue.addLast(path)
        if (player == null) next()
        return true
    }

    fun stop() {
        queue.clear()
        release()
    }

    private fun exists(path: String) = runCatching { context.assets.openFd(path).close() }.isSuccess

    private fun next() {
        val path = queue.removeFirstOrNull() ?: run { abandonFocus(); return }
        val mp = MediaPlayer()
        runCatching {
            context.assets.openFd(path).use { afd ->
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            mp.setOnCompletionListener { release(); next() }
            mp.setOnErrorListener { _, _, _ -> release(); next(); true }
            mp.prepare()
            requestFocus()
            player = mp
            mp.start()
        }.onFailure {
            runCatching { mp.release() }
            player = null
            next()
        }
    }

    private fun release() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
    }

    private fun requestFocus() {
        if (focus != null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .build()
        focus = req
        audio.requestAudioFocus(req)
    }

    private fun abandonFocus() {
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }
}
