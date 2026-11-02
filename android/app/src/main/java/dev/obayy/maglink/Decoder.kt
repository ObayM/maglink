package dev.obayy.maglink

import kotlin.math.abs


class Frame(val bytes: ByteArray, val complete: Boolean, val mode: String, val total: Int)

class Mode(val name: String, val half: Double, val nrzi: Boolean)

object Decoder {
    private val MODES = listOf(
            Mode("fast", 0.04, true),
            Mode("normal", 0.05, false),
            Mode("slow", 0.1, false),
        )

    private var mode = MODES[0]
    private var HALF = 0.04
    private var SKIP = 0.02
    private var BIT = 0.08
    private var BYTE = 0.4

    private val CODES = intArrayOf(
        0b11110, 0b01001, 0b10100, 0b10101, 0b01010, 0b01011, 0b01110, 0b01111,
        0b10010, 0b10011, 0b10110, 0b10111, 0b11010, 0b11011, 0b11100, 0b11101,
    )

    private const val SYNC = "11100010010"
    private const val MIN_AMP = 4.0

    private var t = DoubleArray(0)
    private var prefix = DoubleArray(1)
    private var doneUntil = 0.0
    private var amp = 0.0

    private fun use(m: Mode) {
        mode = m
        HALF = m.half
        SKIP = HALF / 2
        BIT = 2 * HALF
        BYTE = if (m.nrzi) 10 * HALF else 16 * HALF
    }

    private fun readManchester(dataStart: Double, k: Int): Int {
        var value = 0
        for (i in 0 until 8) {
            value *= 2
            if (margin(dataStart + (8 * k + i) * BIT) > 0) value += 1
        }
        return value
    }

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
        if (!mode.nrzi) return readManchester(dataStart, k)

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

        var partial: Frame? = null
        for (m in MODES) {
            use(m)

            val frame = search(last) ?: continue
            if (frame.complete) return frame

            if (partial == null) partial = frame
        }
        return partial
    }

    private fun search(last: Double): Frame? {
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
        var partialTotal = 0

        for ((strength, s) in candidates) {
            val dataStart = s + SYNC.length * BIT

            amp = strength / SYNC.length
            
            val length = readByte(dataStart, 0)
            if (length <= 0) continue
            
            val end = dataStart + (length + 2) * BYTE

            if (end > last) {
                val have = minOf(((last - dataStart) / BYTE).toInt() - 1, length)
                if (partial == null && amp >= MIN_AMP && have >= 2) {
                    val head = ByteArray(have) { readByte(dataStart, 1 + it).toByte() }

                    if (pictureBytes(head) == length || head.all { it.toInt() in 32..126 }) {
                        partial = head
                        partialTotal = length
                    }
                }
                continue
            }
            
            tried.add(s)

            val got = IntArray(length) { readByte(dataStart, 1 + it) }
            
            val check = readByte(dataStart, 1 + length)

            if (got.all { it >= 0 } && check == 255 - (length + got.sum()) % 256) {
                doneUntil = end
                return Frame(ByteArray(length) { got[it].toByte() }, true, mode.name, length)
            }

            if (tried.size == 15) break
        }
        if (partial != null) return Frame(partial, false, mode.name, partialTotal)
        return null
    }
}
