package dev.obayy.maglink

import kotlin.math.abs
import kotlin.math.sqrt


class Frame(val bytes: ByteArray, val complete: Boolean, val mode: String, val total: Int, val progress: Float = 1f, val amp: Double = 0.0)

class Mode(val name: String, val half: Double, val nrzi: Boolean)

fun crc16(data: ByteArray): Int {
    var crc = 0xFFFF
    for (b in data) {
        crc = crc xor ((b.toInt() and 0xFF) shl 8)
        
        repeat(8) {
            crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
            crc = crc and 0xFFFF
        }
    }
    return crc
}

object Decoder {
    private val MODES = listOf(
            Mode("turbo", 0.016, true),
            Mode("fast", 0.04, true),
            Mode("normal", 0.05, false),
            Mode("slow", 0.1, false),
        )

    private var mode = MODES[0]
    private var HALF = 0.04
    private var SKIP = 0.02
    private var BIT = 0.08
    private var BYTE = 0.4
    private var STEP = 0.005

    private val CODES = intArrayOf(
        0b11110, 0b01001, 0b10100, 0b10101, 0b01010, 0b01011, 0b01110, 0b01111,
        0b10010, 0b10011, 0b10110, 0b10111, 0b11010, 0b11011, 0b11100, 0b11101,
    )

    private const val SYNC = "11100010010"
    private const val MIN_AMP = 2.3
    private const val WEAK = 8
    private const val SURE = 0.25

    private var t = DoubleArray(0)
    private var sums = Array(3) { DoubleArray(1) }
    private var doneUntil = 0.0
    private var amp = 0.0

    // every phone has its compass chip turned a different way, so each sync works out its own mix of x, y and z
    private var dir = DoubleArray(3)

    // the mix from the last message that came through (starts as x + y - z, which was right for the A16)
    var learned = doubleArrayOf(1 / sqrt(3.0), 1 / sqrt(3.0), -1 / sqrt(3.0))

    private fun use(m: Mode) {
        mode = m
        HALF = m.half
        SKIP = HALF / 2
        BIT = 2 * HALF
        BYTE = if (m.nrzi) 10 * HALF else 16 * HALF
        STEP = minOf(0.005, HALF / 8) // turbo pieces are only 2 phone readings long, so line the sync up finer
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
        var sum = 0.0
        for (k in 0 until 3) sum += dir[k] * (sums[k][j] - sums[k][i])
        return sum / maxOf(j - i, 1)
    }

    private fun margins(start: Double): DoubleArray {
        val a = firstAt(start + SKIP)
        val b = firstAt(start + HALF)
        val c = firstAt(start + HALF + SKIP)
        val d = firstAt(start + BIT)

        return DoubleArray(3) { k ->
            val s = sums[k]
            (s[b] - s[a]) / maxOf(b - a, 1) - (s[d] - s[c]) / maxOf(d - c, 1)
        }
    }

    private fun margin(start: Double): Double {
        return avg(start + SKIP, start + HALF) - avg(start + HALF + SKIP, start + BIT)
    }

    // python is 100% better than kotlin :sob:

    private fun sign(k: Int) = if (SYNC[k] == '1') 1.0 else -1.0

    // the best mix is wherever the 11 sync bits point together. Picking it lets noise pass as a sync more easily, so every bit has to clearly agree
    private fun syncAt(margins: Array<DoubleArray>, i: Int, apart: Int): Pair<Double, DoubleArray>? {
        val w = DoubleArray(3)
        for (k in SYNC.indices) {
            val m = margins[i + k * apart]
            for (a in 0 until 3) w[a] += sign(k) * m[a]
        }

        val strength = sqrt(w[0] * w[0] + w[1] * w[1] + w[2] * w[2])
        if (strength == 0.0) return null
        for (a in 0 until 3) w[a] /= strength

        for (k in SYNC.indices) {
            val m = margins[i + k * apart]
            if (sign(k) * (w[0] * m[0] + w[1] * m[1] + w[2] * m[2]) < SURE * strength / SYNC.length) return null
        }
        return Pair(strength, w)
    }

    private fun level(start: Double) = avg(start + SKIP, start + HALF)

    // above 0 means a 1, below 0 a 0, and close to 0 means we're not sure :(
    private fun soft(dataStart: Double, i: Int): Double {
        if (!mode.nrzi) return margin(dataStart + i * BIT)

        val jump = level(dataStart + i * HALF) - level(dataStart + (i - 1) * HALF)
        return abs(jump) - amp / 2
    }

    private fun bitsPerByte() = if (mode.nrzi) 10 else 8

    private fun softBits(dataStart: Double, from: Int, count: Int): DoubleArray {
        val per = bitsPerByte()
        return DoubleArray(per * count) { soft(dataStart, per * from + it) }
    }

    private fun bytesOf(bits: BooleanArray): IntArray {
        return IntArray(bits.size / bitsPerByte()) { k ->
            var value = 0
            if (!mode.nrzi) {
                for (i in 0 until 8) value = value * 2 + if (bits[8 * k + i]) 1 else 0
            } else {
                for (n in 0 until 2) {
                    var code = 0
                    for (j in 0 until 5) code = code * 2 + if (bits[10 * k + 5 * n + j]) 1 else 0

                    val nibble = CODES.indexOf(code)
                    value = if (value < 0 || nibble < 0) -1 else value * 16 + nibble
                }
            }
            value
        }
    }

    private fun readBytes(dataStart: Double, from: Int, count: Int): IntArray {
        val soft = softBits(dataStart, from, count)
        return bytesOf(BooleanArray(soft.size) { soft[it] > 0 })
    }

    private fun readByte(dataStart: Double, k: Int) = readBytes(dataStart, k, 1)[0]

    private fun check(bits: BooleanArray, length: Int): ByteArray? {
        val got = bytesOf(bits)
        if (got.any { it < 0 } || got[0] != length) return null

        val body = ByteArray(length + 1) { got[it].toByte() }
        if (crc16(body) != got[length + 1] * 256 + got[length + 2]) return null
        return body.copyOfRange(1, length + 1)
    }

    private fun repair(soft: DoubleArray, length: Int, weak: Int): ByteArray? {
        val bits = BooleanArray(soft.size) { soft[it] > 0 }
        check(bits, length)?.let { return it }

        val unsure = soft.indices.sortedBy { abs(soft[it]) }.take(weak)
        
        for (a in unsure.indices) {
            bits[unsure[a]] = !bits[unsure[a]]
            check(bits, length)?.let { return it }

            for (b in a + 1 until unsure.size) {
                bits[unsure[b]] = !bits[unsure[b]]
                check(bits, length)?.let { return it }
                bits[unsure[b]] = !bits[unsure[b]]
            }

            bits[unsure[a]] = !bits[unsure[a]]
        }
        return null
    }

    fun mix(r: Snapshot, i: Int) = learned[0] * r.x[i] + learned[1] * r.y[i] + learned[2] * r.z[i]

    fun listen(r: Snapshot): Frame? {
        if (r.t.size < 100){
            return null
        }
        t = r.t 

        sums = Array(3) { DoubleArray(t.size + 1) }
        val axes = arrayOf(r.x, r.y, r.z)

        for (k in 0 until 3) {
            for (i in t.indices) sums[k][i + 1] = sums[k][i] + axes[k][i]
        }

        val last = t.last()

        var partial: Frame? = null
        for (m in MODES) {
            use(m)

            val frame = search(last) ?: continue
            if (frame.complete) return frame

            if (partial == null || frame.amp > partial.amp) partial = frame
        }
        return partial
    }

    private fun search(last: Double): Frame? {
        val candidates = mutableListOf<Triple<Double, Double, DoubleArray>>()
        val first = maxOf(t.first(), doneUntil)
        val n = ((last - SYNC.length * BIT - first) / STEP).toInt()
        if (n <= 0) return null

        // BIT is a whole number of STEPs, so each margin only has to be worked out once
        val apart = Math.round(BIT / STEP).toInt()
        val margins = Array(n + SYNC.length * apart) { margins(first + it * STEP) }

        for (i in 0 until n) {
            val (strength, w) = syncAt(margins, i, apart) ?: continue
            candidates.add(Triple(strength, first + i * STEP, w))
        }

        candidates.sortByDescending { it.first }

        val tried = mutableListOf<Double>()
        var partial: Frame? = null

        for ((strength, s, w) in candidates) {
            val dataStart = s + SYNC.length * BIT

            dir = w
            amp = strength / SYNC.length
            
            val length = readByte(dataStart, 0)
            if (length <= 0) continue
            
            val end = dataStart + (length + 3) * BYTE

            if (end > last) {
                if (end - s > 60) continue

                val have = minOf(((last - dataStart) / BYTE).toInt() - 1, length)
                if (partial == null && amp >= MIN_AMP && have >= 0) {
                    
                    val read = readBytes(dataStart, 1, have)
                    val head = ByteArray(have) { read[it].toByte() }

                    val picture = have > 0 && read[0] == 1 && (have < 3 || pictureBytes(head) == length)
                    if (picture || read.all { it in 32..126 }) {
                        partial = Frame(head, false, mode.name, length, ((last - s) / (end - s)).toFloat(), amp)
                    }
                }
                continue
            }
            
            tried.add(s)

            val body = repair(softBits(dataStart, 0, length + 3), length, if (tried.size <= 3) WEAK else 0)

            if (body != null) {
                doneUntil = end
                learned = w

                return Frame(body, true, mode.name, length)
            }

            if (tried.size == 15) break
        }
        return partial
    }
}
