package dev.obayy.maglink

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Meter {
    private const val WINDOW = 4.0

    private fun strength(t: DoubleArray, v: DoubleArray, hz: Double): Double {
        var c = 0.0
        var s = 0.0
        for (i in t.indices) {
            val a = 2 * PI * hz * t[i]
            c += v[i] * cos(a)
            s += v[i] * sin(a)
        }

        return PI * sqrt(c * c + s * s) / t.size
    }

    fun measure(r: Snapshot): Double? {
        if (r.t.isEmpty() || r.t.last() - r.t.first() < WINDOW) return null

        val from = r.t.indexOfFirst { it >= r.t.last() - WINDOW }
        val n = r.t.size - from

        val t = DoubleArray(n) { r.t[from + it] - r.t[from] }
        val v = DoubleArray(n) { Decoder.mix(r, from + it) }

        val tm = t.average()
        val vm = v.average()

        var up = 0.0
        var across = 0.0

        for (i in 0 until n) {
            up += (t[i] - tm) * (v[i] - vm)
            across += (t[i] - tm) * (t[i] - tm)
        }
        val slope = up / across
        for (i in 0 until n) v[i] -= vm + slope * (t[i] - tm)

        val step = strength(t, v, 1.0)
        val noise = (strength(t, v, 0.75) + strength(t, v, 1.25)) / 2

        if (step < 4 * noise) return null
        return step
    }

    fun advice(step: Double) = when {
        step >= 12 -> "fast should work :)"
        step >= 4 -> "use normal"
        step >= 2 -> "use slow"
        else -> "too weak, move the phone"
    }
}
