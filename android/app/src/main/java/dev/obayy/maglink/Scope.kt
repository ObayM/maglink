package dev.obayy.maglink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class Scope(context: Context, colors: Palette) : View(context) {
    private var values = DoubleArray(0)
    private val path = Path()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setBackgroundColor(colors.surface)
        line.color = colors.accent
        line.style = Paint.Style.STROKE
        line.strokeWidth = 2 * resources.displayMetrics.density
        line.strokeJoin = Paint.Join.ROUND
    }

    fun show(r: Snapshot, seconds: Double) {
        if (r.t.isEmpty()) return
        val from = r.t.last() - seconds
        val first = r.t.indexOfFirst { it >= from }
        values = DoubleArray(r.t.size - first) { Decoder.mix(r, first + it) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (values.size < 2) return
        val low = values.min()
        val span = maxOf(values.max() - low, 1.0)
        path.reset()
        for (i in values.indices) {
            val x = i * width / (values.size - 1f)
            val y = (height * (0.85 - 0.7 * (values[i] - low) / span)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, line)
    }
}