package app.kiloquest

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Spracherkennung **nur auf dem Geraet** (Android 12+, Offline-Sprachpaket): Es wird kein Audio an Server geschickt.
 * Ohne On-Device-Erkennung ist die Funktion nicht verfuegbar, ausser die Eltern erlauben ausdruecklich den normalen Dienst ([allowCloud], Standard aus).
 */
class VoiceAnswers(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    /** Warum Sprachbefehle/Sprachantworten (nicht) gehen: fuer eine verstaendliche Rueckmeldung in den Einstellungen. */
    enum class Status { OK, OLD_ANDROID, NO_ON_DEVICE, NO_MIC }

    companion object {
        /**
         * Nur nach ausdruecklicher Zustimmung in den Einstellungen: ohne Offline-Paket den normalen Spracherkennungsdienst des Geraets nutzen
         * (der das Audio an seinen Server, meist Google, senden kann). Standard: aus.
         */
        @Volatile var allowCloud: Boolean = false

        private fun onDevice(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

        private fun cloud(context: Context): Boolean = allowCloud && SpeechRecognizer.isRecognitionAvailable(context)

        fun status(context: Context): Status = when {
            !onDevice(context) && !cloud(context) -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) Status.OLD_ANDROID else Status.NO_ON_DEVICE
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED -> Status.NO_MIC
            else -> Status.OK
        }

        fun isAvailable(context: Context): Boolean = onDevice(context) || cloud(context)
    }

    /** Hoert einmal zu und meldet die Erkennungsergebnisse (leer bei Fehler/Stille). Muss auf dem Hauptthread laufen. */
    fun listen(onResult: (List<String>) -> Unit) {
        if (!isAvailable(context)) { onResult(emptyList()); return }
        stop()
        // Bevorzugt auf dem Geraet; nur ohne Offline-Paket und mit Zustimmung der normale Dienst
        val r = if (onDevice(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                onResult(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
            }
            override fun onError(error: Int) { onResult(emptyList()) }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        r.startListening(intent)
    }

    fun stop() {
        recognizer?.let { runCatching { it.cancel(); it.destroy() } }
        recognizer = null
    }
}
