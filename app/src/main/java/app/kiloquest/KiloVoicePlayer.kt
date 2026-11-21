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

    /** Spielt mehrere Saetze nacheinander, aber nur, wenn es fuer ALLE eine Aufnahme gibt (sonst false, nichts wird eingereiht). */
    fun playAll(texts: List<String>): Boolean {
        val lang = Locale.getDefault().language
        val paths = texts.map { t -> index.idFor(lang, t)?.let { "kilo_voice/$lang/$it.ogg" } ?: return false }
        return enqueue(paths)
    }

    /**
     * Spielt Slots einer Navigationsansage: Bausteine aus den Assets, Namen aus dem Cache (`filesDir/voice_cache/<lang>/<hash>.ogg`,
     * siehe Spec 016). Nur wenn ALLE Teile vorhanden sind; sonst false und nichts wird eingereiht.
     */
    fun playSlots(slots: List<app.kiloquest.nav.Slot>): Boolean {
        val lang = Locale.getDefault().language
        // Namen ohne Aufnahme (z. B. noch kein Pro-Fahrt-Paket) entfallen samt "auf die": Kilo spricht dann ohne Strassenname
        val usable = app.kiloquest.nav.dropMissingNames(slots) { exists(nameFile(lang, it).path) }
        val paths = usable.map { slot ->
            when (slot) {
                is app.kiloquest.nav.Slot.Clip -> "kilo_voice/$lang/${slot.id}.ogg"
                is app.kiloquest.nav.Slot.Name -> nameFile(lang, slot.text).path
            }
        }
        return enqueue(paths)
    }

    /** Spielt Teilclips (Ids aus tools/voice, z. B. `part_limit_is`, `num_80`) nacheinander; false, wenn eine Datei fehlt. */
    fun playParts(ids: List<String>?): Boolean {
        ids ?: return false
        val lang = Locale.getDefault().language
        return enqueue(ids.map { "kilo_voice/$lang/$it.ogg" })
    }

    private fun enqueue(paths: List<String>): Boolean {
        if (paths.isEmpty() || !paths.all { exists(it) }) return false
        paths.forEach { queue.addLast(it) }
        if (player == null) next()
        return true
    }

    fun stop() {
        queue.clear()
        release()
    }

    private fun nameFile(lang: String, text: String) = java.io.File(context.filesDir, "voice_cache/$lang/${app.kiloquest.nav.VoicePack.nameHash(text)}.mp3")

    private fun isFile(path: String) = path.startsWith("/")

    private fun exists(path: String) = if (isFile(path)) java.io.File(path).length() > 0 else runCatching { context.assets.openFd(path).close() }.isSuccess

    private fun next() {
        val path = queue.removeFirstOrNull() ?: run { abandonFocus(); return }
        val mp = MediaPlayer()
        runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            if (isFile(path)) mp.setDataSource(path)
            else context.assets.openFd(path).use { afd -> mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }
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
