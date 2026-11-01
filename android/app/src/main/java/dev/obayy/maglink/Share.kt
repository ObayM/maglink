package dev.obayy.maglink

import java.net.ServerSocket
import kotlin.concurrent.thread

object Share {
    @Volatile var latest: Snapshot? = null
    private var started = false

    private fun list(a: DoubleArray) = a.joinToString(",", "[", "]")

    fun start() {
        if (started) return
        started = true
        thread(isDaemon = true) {
            val server = ServerSocket(8081)
            
            while (true) {
                try {
                    server.accept().use { socket ->
                        socket.getInputStream().read(ByteArray(4096))
                        val s = latest
                        
                        val body = if (s == null) "{}" else
                            "{\"t\":${list(s.t)},\"x\":${list(s.x)},\"y\":${list(s.y)},\"z\":${list(s.z)}}"
                        val head = "HTTP/1.0 200 OK\r\nContent-Type: application/json\r\n\r\n"
                        
                        socket.getOutputStream().write((head + body).toByteArray())
                    }
                } catch (e: Exception) {
                }
            }

        }
    }
}