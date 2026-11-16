package com.runit.webrecorder

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import androidx.core.view.WindowInsetsControllerCompat

/** Light / dark / system, accent colours, app font and text size. */
object VioTheme {

    val ACCENT_NAMES = arrayOf(
        "Indigo", "Blue", "Teal", "Green", "Orange", "Red", "Pink", "Purple", "Gold", "Slate"
    )

    // What the swatches show (same colours as res/values/colors.xml)
    val ACCENT_LIGHT = intArrayOf(
        0xFF4B42F0.toInt(), 0xFF1A67D2.toInt(), 0xFF0B8A7A.toInt(), 0xFF198A3A.toInt(), 0xFFD9560B.toInt(),
        0xFFD92D3A.toInt(), 0xFFD61F7A.toInt(), 0xFF8B2FD0.toInt(), 0xFFA66A00.toInt(), 0xFF4B5563.toInt()
    )
    val ACCENT_DARK = intArrayOf(
        0xFF8F89FF.toInt(), 0xFF6AA6FF.toInt(), 0xFF34D3BE.toInt(), 0xFF4ADE80.toInt(), 0xFFFF9142.toInt(),
        0xFFFF7480.toInt(), 0xFFFF7FBC.toInt(), 0xFFC58BFF.toInt(), 0xFFF5C542.toInt(), 0xFFAAB3C2.toInt()
    )

    private val ACCENT_STYLES = intArrayOf(
        R.style.VioAccent0, R.style.VioAccent1, R.style.VioAccent2, R.style.VioAccent3, R.style.VioAccent4,
        R.style.VioAccent5, R.style.VioAccent6, R.style.VioAccent7, R.style.VioAccent8, R.style.VioAccent9
    )
    private val ACCENT_STYLES_DK = intArrayOf(
        R.style.VioAccent0Dk, R.style.VioAccent1Dk, R.style.VioAccent2Dk, R.style.VioAccent3Dk, R.style.VioAccent4Dk,
        R.style.VioAccent5Dk, R.style.VioAccent6Dk, R.style.VioAccent7Dk, R.style.VioAccent8Dk, R.style.VioAccent9Dk
    )
    private val FONT_STYLES = intArrayOf(R.style.VioFont0, R.style.VioFont1, R.style.VioFont2, R.style.VioFont3)

    val FONT_NAMES = arrayOf("Default", "Serif", "Monospace", "Condensed")
    val THEME_NAMES = arrayOf("Same as phone", "Light", "Dark")
    val TEXT_SCALES = intArrayOf(85, 100, 115, 130)
    val TEXT_SCALE_NAMES = arrayOf("Small", "Default", "Large", "Extra large")

    fun isDark(ctx: Context): Boolean = when (Settings.themeMode) {
        1 -> false
        2 -> true
        else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    /** Puts the chosen theme, accent and font on the activity's theme. Call before the layout is inflated. */
    fun apply(a: Activity) {
        val dark = isDark(a)
        val t = a.theme
        t.applyStyle(if (dark) R.style.VioDark else R.style.VioLight, true)
        val i = Settings.accent.coerceIn(0, ACCENT_STYLES.size - 1)
        t.applyStyle(if (dark) ACCENT_STYLES_DK[i] else ACCENT_STYLES[i], true)
        t.applyStyle(FONT_STYLES[Settings.uiFont.coerceIn(0, FONT_STYLES.size - 1)], true)
        applyFontScale(a)
        val bg = attrColor(a, R.attr.vioBg)
        a.window.setBackgroundDrawable(ColorDrawable(bg))
        a.window.statusBarColor = bg
        a.window.navigationBarColor = attrColor(a, R.attr.vioSurface)
    }

    /** Status / navigation bar icons: dark on light themes, light on dark ones. Needs the layout to exist. */
    fun applyBars(a: Activity) {
        val dark = isDark(a)
        val c = WindowInsetsControllerCompat(a.window, a.window.decorView)
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
    }

    /** App text size = what the phone uses x the size picked in Settings. */
    @Suppress("DEPRECATION")
    fun applyFontScale(a: Activity) {
        val res = a.resources
        val cfg = Configuration(res.configuration)
        cfg.fontScale = Resources.getSystem().configuration.fontScale * Settings.uiTextScale / 100f
        res.updateConfiguration(cfg, res.displayMetrics)
    }

    fun attrColor(ctx: Context, attr: Int): Int {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
