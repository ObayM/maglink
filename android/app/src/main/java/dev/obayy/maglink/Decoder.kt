package dev.obayy.maglink

import kotlin.math.abs

object Decoder {
    private const val HALF = 0.05
    private const val SKIP = HALF / 2
    private const val BIT = 2 * HALF
    private const val SYNC = "11100010010"

    private var t = DoubleArray(0)
    private var prefix = DoubleArray(1)
    private var doneUntil = 0.0

    private fun firstAt(time: Double): Int {
        var low = 0
        var high = t.size

        while (low < high) {
            val mid = (low + high) / 2
            if (t[mid] < time) {
                low = mid +1
            }else {
                high = mid
            }
        }

        return low
    }

    private fun avg(a: Double, b: Double): Double {
        val i = firstAt(a)
        val j = firstAt(b)
        return (prefix[j] - prefix[i]) / maxOf(j - i, 1)
    }

    private fun margin(start: Double): Double {
        return avg(start + SKIP, start + HALF) - avg(start + HALF + SKIP, start + BIT)
    }

    // python is 100% better than kotlin :sob:

    private fun syncScore(start: Double): Pair<Int, Double> {
        var agree = 0
        var strength = 0.0
        
        for (k in SYNC.indices) {
            var m = margin(start + k * BIT)
            if (SYNC[k] == '0') m = -m
            
            strength += m
            if (m > 0) agree++
        }
        return Pair(agree, strength)
    }

    private fun readByte(dataStart: Double, k: Int): Int {
        var value = 0
        
        for (i in 0 until 8) {
            value *= 2
            if (margin(dataStart + (8 * k + i) * BIT) > 0) value += 1
        }

        return value
    }

    fun mix(r: Snapshot, i: Int) = r.x[i] + r.y[i] - r.z[i]

    fun listen (r: Snapshot): ByteArray? {
        if (r.t.size < 100){
            return null
        }
        t = r.t 

        prefix = DoubleArray(t.size + 1)

        for (i in t.indices) {
            prefix[i + 1] = prefix[i] + mix(r, i)
        }

        val last = t.last()

        val candidates = mutableListOf<Pair<Double, Double>>()
        var start = maxOf(t.first(), doneUntil)

        while (start < last - SYNC.length * BIT) {
            val (agree, strength) = syncScore(start)
            
            if (agree == SYNC.length) candidates.add(Pair(strength, start))
            start += 0.005
        }

        candidates.sortByDescending { it.first }

        val tried = mutableListOf<Double>()

        for ((_, s) in candidates) {
            if (tried.any { abs(s - it) < HALF }) continue
            val dataStart = s + SYNC.length * BIT
            
            val length = readByte(dataStart, 0)
            
            val end = dataStart + (length + 2) * 8 * BIT
            
            if (end > last) continue
            
            tried.add(s)

            val got = IntArray(length) { readByte(dataStart, 1 + it) }
            
            val check = readByte(dataStart, 1 + length)
            
            if (length > 0 && check == 255 - (length + got.sum()) % 256) {
                doneUntil = end
                return ByteArray(length) { got[it].toByte() }
            }
            
            if (tried.size == 5) break
        }
        return null
    }
}
