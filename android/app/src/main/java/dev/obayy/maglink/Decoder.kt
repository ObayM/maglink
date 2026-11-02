package dev.obayy.maglink

import kotlin.math.abs

class Frame(val bytes: ByteArray, val complete: Boolean)

object Decoder {
    private const val HALF = 0.04
    private const val BYTE = 10 * HALF

    private val CODES = intArrayOf(
        0b11110, 0b01001, 0b10100, 0b10101, 0b01010, 0b01011, 0b01110, 0b01111,
        0b10010, 0b10011, 0b10110, 0b10111, 0b11010, 0b11011, 0b11100, 0b11101,
    )

    private const val SKIP = HALF / 2
    private const val BIT = 2 * HALF
    private const val SYNC = "11100010010"

    private var t = DoubleArray(0)
    private var prefix = DoubleArray(1)
    private var doneUntil = 0.0
    private var amp = 0.0

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

    private fun level(start: Double) = avg(start + SKIP, start + HALF)

    private fun readNibble(dataStart: Double, n: Int): Int {
        var code = 0
        for (j in 0 until 5) {
            val i = 5 * n + j
            val jump = level(dataStart + i * HALF) - level(dataStart + (i - 1) * HALF)
            code = code * 2 + if (abs(jump) > amp / 2) 1 else 0
        }
        return CODES.indexOf(code)
    }

    private fun readByte(dataStart: Double, k: Int): Int {
        val high = readNibble(dataStart, 2 * k)
        val low = readNibble(dataStart, 2 * k + 1)
        if (high < 0 || low < 0) return -1
        return high * 16 + low
    }

    fun mix(r: Snapshot, i: Int) = r.x[i] + r.y[i] - r.z[i]

    fun listen(r: Snapshot): Frame? {
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
        var partial: ByteArray? = null

        for ((strength, s) in candidates) {
            if (tried.any { abs(s - it) < HALF }) continue
            val dataStart = s + SYNC.length * BIT

            amp = strength / SYNC.length
            
            val length = readByte(dataStart, 0)
            if (length <= 0) continue
            
            val end = dataStart + (length + 2) * BYTE

            if (end > last) {
                val have = ((last - dataStart) / BYTE).toInt() - 1
                if (partial == null && have >= 3) {
                    val head = ByteArray(3) { readByte(dataStart, 1 + it).toByte() }
                    
                    if (pictureBytes(head) == length) {
                        partial = ByteArray(minOf(have, length)) { readByte(dataStart, 1 + it).toByte() }
                    }
                }
                continue
            }
            
            tried.add(s)

            val got = IntArray(length) { readByte(dataStart, 1 + it) }
            
            val check = readByte(dataStart, 1 + length)

            if (got.all { it >= 0 } && check == 255 - (length + got.sum()) % 256) {
                doneUntil = end
                return Frame(ByteArray(length) { got[it].toByte() }, true)
            }
            
            if (tried.size == 5) break
        }
        if (partial != null) return Frame(partial, false)
        return null
    }
}
