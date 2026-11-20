package app.kiloquest

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kiloquest.nav.LatLon
import kotlinx.coroutines.delay
import java.io.File

private fun Context.activity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is ComponentActivity) return c; c = c.baseContext }
    return null
}

/** Ablauf einer Foto-Aufnahme. */
private enum class PhotoStep { PERMISSION, COUNTDOWN, SHOOTING, DONE, FAILED }

/**
 * Vollbild-Ablauf "Foto-Moment" fuer den Beifahrer: Kamera-Erlaubnis, Countdown (3-2-1, alle lachen), beide Kameras ausloesen,
 * speichern, Tagebuch-Eintrag. [place] = Name des schoenen Ortes (oder null), [pos] = Position fuer den Eintrag.
 * [onFinished] meldet, ob ein Foto entstanden ist; danach schliesst der Aufrufer den Bildschirm.
 */
@Composable
fun PhotoMomentScreen(place: String?, pos: LatLon?, galleryCopy: Boolean, onFinished: (taken: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as KiloQuestApp
    var step by remember { mutableStateOf(if (ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) PhotoStep.COUNTDOWN else PhotoStep.PERMISSION) }
    var count by remember { mutableStateOf(3) }
    var saved by remember { mutableStateOf<List<String>>(emptyList()) }
    var concurrent by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> step = if (ok) PhotoStep.COUNTDOWN else PhotoStep.FAILED }

    LaunchedEffect(step) {
        when (step) {
            PhotoStep.PERMISSION -> permission.launch(Manifest.permission.CAMERA)
            PhotoStep.COUNTDOWN -> {
                for (i in 3 downTo 1) { count = i; delay(1000) }
                step = PhotoStep.SHOOTING
            }
            PhotoStep.SHOOTING -> {
                val activity = ctx.activity()
                if (activity == null) { step = PhotoStep.FAILED; return@LaunchedEffect }
                val base = "p" + System.currentTimeMillis()
                val work = File(ctx.cacheDir, "shot").apply { mkdirs() }
                val r = runCatching { DualPhotoCapture(ctx, activity).capture(work, base) }.getOrNull()
                val names = ArrayList<String>()
                r?.back?.let { PhotoStore.processAndStore(ctx, it, "$base-back.jpg") }?.let { names += it.name }
                r?.front?.let { PhotoStore.processAndStore(ctx, it, "$base-front.jpg") }?.let { names += it.name }
                // Reihenfolge im Eintrag: Aussicht zuerst; fehlt sie, steht das Selfie allein
                if (names.isEmpty()) { step = PhotoStep.FAILED; return@LaunchedEffect }
                if (galleryCopy) names.forEach { n -> PhotoStore.file(ctx, n)?.let { PhotoStore.copyToGallery(ctx, it, n) } }
                concurrent = r?.concurrent == true
                saved = names
                app.nav.logPhoto(names, place, pos)
                step = PhotoStep.DONE
            }
            else -> Unit
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            when (step) {
                PhotoStep.PERMISSION -> Text(stringResource(R.string.photo_permission), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                PhotoStep.COUNTDOWN -> {
                    if (!place.isNullOrBlank()) Text(stringResource(R.string.photo_place, place), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.photo_get_ready), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
                    Text(count.toString(), style = MaterialTheme.typography.displayLarge)
                }
                PhotoStep.SHOOTING -> {
                    CircularProgressIndicator(Modifier.size(64.dp))
                    Text(stringResource(R.string.photo_shooting), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                }
                PhotoStep.DONE -> {
                    Text(stringResource(R.string.photo_done), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        saved.forEach { n ->
                            val bmp = remember(n) { PhotoStore.thumbnail(ctx, n, 600) }
                            if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.weight(1f).height(220.dp))
                        }
                    }
                    if (!concurrent && saved.size == 2) Text(stringResource(R.string.photo_one_after_another), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onFinished(true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.photo_close)) }
                }
                PhotoStep.FAILED -> {
                    Text(stringResource(R.string.photo_failed), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = { onFinished(false) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text(stringResource(R.string.photo_close)) }
                }
            }
        }
    }
}

/** Karte "Foto-Moment!" mit "Foto machen" und "Spaeter" (oder einfach nur ein Knopf, wenn [request] fehlt). */
@Composable
fun PhotoRequestCard(reasonScenic: Boolean, place: String?, onTake: () -> Unit, onLater: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.photo_request_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(
                if (reasonScenic && !place.isNullOrBlank()) stringResource(R.string.photo_request_scenic_place, place)
                else stringResource(if (reasonScenic) R.string.photo_request_scenic else R.string.photo_request_random),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onTake, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.photo_take)) }
                OutlinedButton(onClick = onLater, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.photo_later)) }
            }
        }
    }
}
