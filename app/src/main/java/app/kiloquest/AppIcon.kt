package app.kiloquest

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Waehlbares App-Icon: jede Variante ist ein `activity-alias` im Manifest; genau eines ist aktiviert (nur dieses hat den Launcher-Eintrag).
 * Der Wechsel kann den Launcher kurz neu laden; manche Launcher zeigen das neue Icon erst nach einigen Sekunden.
 */
object AppIcon {
    enum class Variant(val alias: String, val labelRes: Int, val previewRes: Int) {
        DEFAULT("IconDefault", R.string.icon_default, R.drawable.icon_preview_default),
        NIGHT("IconNight", R.string.icon_night, R.drawable.icon_preview_night),
        SUNSET("IconSunset", R.string.icon_sunset, R.drawable.icon_preview_sunset),
        FOREST("IconForest", R.string.icon_forest, R.drawable.icon_preview_forest),
        MONO("IconMono", R.string.icon_mono, R.drawable.icon_preview_mono),
    }

    private fun component(ctx: Context, v: Variant) = ComponentName(ctx.packageName, ctx.packageName + "." + v.alias)

    /** Die gerade aktivierte Variante (Standard, wenn keine eindeutig aktiv ist). */
    fun current(ctx: Context): Variant {
        val pm = ctx.packageManager
        // Ausdruecklich aktivierte Variante gewinnt; sonst gilt der Manifest-Standard (Standard-Icon)
        return Variant.entries.firstOrNull { pm.getComponentEnabledSetting(component(ctx, it)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED && it != Variant.DEFAULT }
            ?: Variant.DEFAULT
    }

    /** Aktiviert [v] zuerst und deaktiviert danach die anderen (so gibt es nie keinen Launcher-Eintrag). */
    fun apply(ctx: Context, v: Variant) {
        val pm = ctx.packageManager
        pm.setComponentEnabledSetting(component(ctx, v), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        Variant.entries.filter { it != v }.forEach {
            pm.setComponentEnabledSetting(component(ctx, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
