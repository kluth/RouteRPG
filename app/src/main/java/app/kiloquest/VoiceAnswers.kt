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
 * Ohne On-Device-Erkennung ist die Funktion nicht verfuegbar (kein Rueckfall auf Cloud-Erkennung).
 */
class VoiceAnswers(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    companion object {
        fun isAvailable(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }

    /** Hoert einmal zu und meldet die Erkennungsergebnisse (leer bei Fehler/Stille). Muss auf dem Hauptthread laufen. */
    fun listen(onResult: (List<String>) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !isAvailable(context)) { onResult(emptyList()); return }
        stop()
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
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
