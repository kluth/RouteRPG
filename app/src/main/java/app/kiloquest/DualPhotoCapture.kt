package app.kiloquest

import android.content.Context
import android.os.Build
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Macht ein Foto mit Rueck- und Frontkamera. Wenn das Geraet gleichzeitige Kameras kann (Android 11+, nicht alle Geraete),
 * werden beide parallel ausgeloest; sonst oder bei einem Fehler kurz hintereinander (Rueckkamera, dann Frontkamera).
 * Muss im Vordergrund laufen (Lebenszyklus der Activity). Fuer die Vorschau gibt es keine Anzeige: die App zaehlt vorher herunter.
 */
class DualPhotoCapture(private val context: Context, private val owner: LifecycleOwner) {
    /** [back]/[front] = rohe Dateien (null bei Fehler); [concurrent] = wirklich gleichzeitig aufgenommen. */
    data class Result(val back: File?, val front: File?, val concurrent: Boolean)

    private val executor = ContextCompat.getMainExecutor(context)

    suspend fun capture(workDir: File, base: String): Result {
        val provider = awaitProvider()
        val back = File(workDir, "$base-back-raw.jpg")
        val front = File(workDir, "$base-front-raw.jpg")
        val hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
        val hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        try {
            if (hasBack && hasFront && concurrentSupported(provider)) {
                runCatching { captureConcurrent(provider, back, front) }.getOrNull()?.let { return it }
            }
            return captureSequential(provider, hasBack, hasFront, back, front)
        } finally {
            runCatching { provider.unbindAll() }
        }
    }

    private suspend fun awaitProvider(): ProcessCameraProvider = ProcessCameraProvider.awaitInstance(context)

    private fun concurrentSupported(provider: ProcessCameraProvider): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return runCatching {
            provider.availableConcurrentCameraInfos.any { group ->
                CameraSelector.DEFAULT_BACK_CAMERA.filter(group).isNotEmpty() && CameraSelector.DEFAULT_FRONT_CAMERA.filter(group).isNotEmpty()
            }
        }.getOrDefault(false)
    }

    private fun newCapture() = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()

    private suspend fun captureConcurrent(provider: ProcessCameraProvider, back: File, front: File): Result {
        val backCap = newCapture()
        val frontCap = newCapture()
        provider.unbindAll()
        val camera: ConcurrentCamera = provider.bindToLifecycle(
            listOf(
                ConcurrentCamera.SingleCameraConfig(CameraSelector.DEFAULT_BACK_CAMERA, UseCaseGroup.Builder().addUseCase(backCap).build(), owner),
                ConcurrentCamera.SingleCameraConfig(CameraSelector.DEFAULT_FRONT_CAMERA, UseCaseGroup.Builder().addUseCase(frontCap).build(), owner),
            ),
        )
        check(camera.cameras.size == 2) { "Gleichzeitige Kameras nicht verfuegbar" }
        delay(WARMUP_MS) // Belichtung und Fokus einpendeln lassen
        val (okBack, okFront) = coroutineScope {
            val a = async { backCap.shoot(back) }
            val b = async { frontCap.shoot(front) }
            a.await() to b.await()
        }
        check(okBack || okFront) { "Beide Aufnahmen fehlgeschlagen" }
        return Result(back.takeIf { okBack && it.exists() }, front.takeIf { okFront && it.exists() }, concurrent = true)
    }

    private suspend fun captureSequential(provider: ProcessCameraProvider, hasBack: Boolean, hasFront: Boolean, back: File, front: File): Result {
        suspend fun one(selector: CameraSelector, file: File, present: Boolean): File? {
            if (!present) return null
            val cap = newCapture()
            return runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(owner, selector, cap)
                delay(WARMUP_MS)
                if (cap.shoot(file) && file.exists()) file else null
            }.getOrNull().also { runCatching { provider.unbindAll() } }
        }
        val b = one(CameraSelector.DEFAULT_BACK_CAMERA, back, hasBack)
        delay(300)
        val f = one(CameraSelector.DEFAULT_FRONT_CAMERA, front, hasFront)
        return Result(b, f, concurrent = false)
    }

    private suspend fun ImageCapture.shoot(file: File): Boolean = suspendCancellableCoroutine { cont ->
        takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(), executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) { if (cont.isActive) cont.resume(true) }
                override fun onError(exception: ImageCaptureException) { if (cont.isActive) cont.resume(false) }
            },
        )
    }

    private companion object { const val WARMUP_MS = 700L }
}
