package dev.obayy.maglink

import android.content.Context
import android.content.res.Configuration

class Palette(
    val ground: Int,
    val surface: Int,
    val ink: Int,
    val muted: Int,
    val accent: Int,
)

val LIGHT = Palette(
    ground = 0xFFECEBE8.toInt(),
    surface = 0xFFE0DFDB.toInt(),
    ink = 0xFF16181D.toInt(),
    muted = 0xFF5C6067.toInt(),
    accent = 0xFFC8261C.toInt(),
)

val DARK = Palette(
    ground = 0xFF14161A.toInt(),
    surface = 0xFF1E2126.toInt(),
    ink = 0xFFE8E6E1.toInt(),
    muted = 0xFF9A9EA6.toInt(),
    accent = 0xFFFF6A5C.toInt(),
)

fun paletteFor(context: Context): Palette {
    val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return if (mode == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
}