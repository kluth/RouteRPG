package app.kiloquest.ui.kids

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Bewegungs-Policy: true = nur ruhige Ueberblendungen (kein Huepfen, Skalieren, Konfetti, Dauer-Animation).
 * Gilt bei der Systemeinstellung "Animationen entfernen", bei "Ruhige Ansicht" und im Reiseuebelkeits-Modus auf kurvigen Abschnitten.
 */
val LocalReducedMotion = compositionLocalOf { false }

fun systemAnimationsOff(ctx: Context): Boolean =
    runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

@Composable
fun rememberReducedMotion(calmView: Boolean, curvy: Boolean): Boolean {
    val ctx = LocalContext.current
    val system = remember { systemAnimationsOff(ctx) }
    return system || calmView || curvy
}

/** Wie `animateFloat`, steht aber bei ruhiger Ansicht auf dem Startwert still (keine Dauer-Animation, keine Neuzeichnung). */
@Composable
fun androidx.compose.animation.core.InfiniteTransition.animateOrFreeze(
    reduced: Boolean, initialValue: Float, targetValue: Float, animationSpec: androidx.compose.animation.core.InfiniteRepeatableSpec<Float>, label: String = "",
): androidx.compose.runtime.State<Float> =
    if (reduced) remember { androidx.compose.runtime.mutableStateOf(initialValue) } else animateFloat(initialValue, targetValue, animationSpec, label)
