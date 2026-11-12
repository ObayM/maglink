package dev.obayy.maglink

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Meter {
    private const val WINDOW = 4.0

    // all three axes together, so it works before we know which way this phone's sensor is turned
    private fun strength(t: DoubleArray, axes: List<DoubleArray>, hz: Double): Double {
        var total = 0.0
        for (v in axes) {
            var c = 0.0
            var s = 0.0
            for (i in t.indices) {
                val a = 2 * PI * hz * t[i]
                c += v[i] * cos(a)
                s += v[i] * sin(a)
            }
            total += c * c + s * s
        }

        return PI * sqrt(total) / t.size
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
        val tm = t.average()

        var across = 0.0
        for (i in 0 until n) across += (t[i] - tm) * (t[i] - tm)

        val v = listOf(r.x, r.y, r.z).map { axis ->
            val a = DoubleArray(n) { axis[from + it] }
            val am = a.average()

            var up = 0.0
            for (i in 0 until n) up += (t[i] - tm) * (a[i] - am)

            val slope = up / across
            for (i in 0 until n) a[i] -= am + slope * (t[i] - tm)
            a
        }

        val step = strength(t, v, 1.0)
        val noise = (strength(t, v, 0.75) + strength(t, v, 1.25)) / 2

        if (step < 4 * noise) return null
        return step
    }

    fun advice(step: Double) = when {
        step >= 15 -> "fast should work :)"
        step >= 8 -> "use normal"
        else -> "too weak, move the phone"
    }
}
