package dev.obayy.maglink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

fun isPicture(b: ByteArray): Boolean {
    if (b.size < 3 || b[0].toInt() != 1) return false

    val w = b[1].toInt() and 0xFF
    val h = b[2].toInt() and 0xFF
    
    return w > 0 && h > 0 && b.size == 3 + (w * h + 7) / 8
}

class Picture(context: Context, colors: Palette) : View(context) {
    private var w = 0
    private var h = 0
    private var pixels = BooleanArray(0)
    private val ink = Paint()

    init {
        ink.color = colors.ink
    }

    fun show(b: ByteArray) {
        w = b[1].toInt() and 0xFF
        h = b[2].toInt() and 0xFF
        
        pixels = BooleanArray(w * h) { i ->
            val byte = b[3 + i / 8].toInt()
            ((byte shr (7 - i % 8)) and 1) == 1
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (w == 0) return
        val size = minOf(width / w, height / h).toFloat()
        val top = (height - size * h) / 2
        
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (pixels[y * w + x]) {
                    canvas.drawRect(x * size, top + y * size, (x + 1) * size, top + (y + 1) * size, ink)
                }
            }
        }
    }
}

