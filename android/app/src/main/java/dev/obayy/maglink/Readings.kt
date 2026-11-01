package dev.obayy.maglink

class Snapshot(val t: DoubleArray, val x: DoubleArray, val y: DoubleArray, val z: DoubleArray)

class Readings(private val seconds: Double) {

    private val cap = 32768
    private val t = DoubleArray(cap)
    private val x = DoubleArray(cap)
    private val y = DoubleArray(cap)
    private val z = DoubleArray(cap)

    private var start = 0

    var size = 0
        private set

    fun add(time: Double, a: Float, b: Float, c: Float) {
        
        if (size == cap) {
            start = (start + 1) % cap
            size--
        }

        val i = (start + size) % cap
        t[i] = time
        x[i] = a.toDouble()
        y[i] = b.toDouble()
        z[i] = c.toDouble()
        size++

        while (size > 0 && time - t[start] > seconds) {
            start = (start + 1) % cap
            size--
        }
    }
    
    fun snapshot(): Snapshot {
        val ts = DoubleArray(size)
        val xs = DoubleArray(size)
        val ys = DoubleArray(size)
        val zs = DoubleArray(size)
        
        for (k in 0 until size) {
            val i = (start + k) % cap
            ts[k] = t[i]
            xs[k] = x[i]
            ys[k] = y[i]
            zs[k] = z[i]
        }
        return Snapshot(ts, xs, ys, zs)
    }
}
