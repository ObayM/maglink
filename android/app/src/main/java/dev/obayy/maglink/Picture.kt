package dev.obayy.maglink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

fun pictureBytes(b: ByteArray): Int {
    if (b.size < 3 || b[0].toInt() != 1) return -1
    val w = b[1].toInt() and 0xFF
    val h = b[2].toInt() and 0xFF
    
    if (w !in 1..64 || h !in 1..64) return -1
    return 3 + (w * h + 7) / 8
}

fun isPicture(b: ByteArray) = pictureBytes(b) == b.size

class Picture(context: Context, colors: Palette) : View(context) {
    private var w = 0
    private var h = 0
    private var pixels = BooleanArray(0)
    private val ink = Paint()

    private var known = 0
    private val pending = Paint()

    init {
        ink.color = colors.ink
        pending.color = colors.surface
    }

    fun show(b: ByteArray) {
        w = b[1].toInt() and 0xFF
        h = b[2].toInt() and 0xFF
        
        known = minOf((b.size - 3) * 8, w * h)
        
        pixels = BooleanArray(w * h) { i ->
            i < known && ((b[3 + i / 8].toInt() shr (7 - i % 8)) and 1) == 1
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (w == 0) return
        val size = minOf(width / w, height / h).toFloat()
        val top = (height - size * h) / 2
        
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                
                val paint = if (i >= known) pending else if (pixels[i]) ink else null
                
                if (paint != null) {
                    canvas.drawRect(x * size, top + y * size, (x + 1) * size, top + (y + 1) * size, paint)
                }
            }
        }
    }
}