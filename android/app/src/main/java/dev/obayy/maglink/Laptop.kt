package dev.obayy.maglink

class Doing(val label: String, val finished: Boolean)

object Laptop {
    private const val WINDOW = 5.0
    private const val HEAVY = 9.0
    private const val GAP = 3.0
    private const val LONG = 20.0
    private const val CREEP = 0.3 / 120

    private var quietest = Double.MAX_VALUE
    private var busySince = 0.0
    private var armed = false

    fun reset() {
        quietest = Double.MAX_VALUE
        busySince = 0.0
        armed = false
    }

    fun guess(r: Snapshot): Doing? {
        val now = r.t.last()
        if (r.t.first() > now - WINDOW) return null
        val from = r.t.indexOfFirst { it >= now - WINDOW }

        var sum = 0.0
        for (i in from until r.t.size) sum += Decoder.mix(r, i)
        val level = sum / (r.t.size - from)

        if (level - quietest < HEAVY) quietest = minOf(quietest + CREEP, level)
        val rise = level - quietest

        var finished = false
        if (rise >= HEAVY) {
            if (busySince == 0.0) busySince = now
            if (now - busySince > LONG) armed = true

        } else {
            busySince = 0.0
            if (armed && rise < HEAVY - GAP) {
                finished = true
                armed = false
            }
        }

        return Doing(if (rise >= HEAVY) "high load" else "idle", finished)
    }
}
