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
        if (r.t.isEmpty() || r.t.last() - r.t.first() < 2 * WINDOW) return null

        val now = r.t.last()
        val start = r.t.indexOfFirst { it >= now - 2 * WINDOW }
        val mid = r.t.indexOfFirst { it >= now - WINDOW }

        beat(r, start, mid) ?: return null
        return beat(r, mid, r.t.size)
    }

    private fun beat(r: Snapshot, from: Int, to: Int): Double? {
        val n = to - from

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
        step >= 26 -> "fast should work :)"
        step >= 14 -> "use normal"
        else -> "too weak, move the phone"
    }
}
