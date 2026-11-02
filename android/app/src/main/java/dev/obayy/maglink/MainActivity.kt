
package dev.obayy.maglink

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import kotlin.math.abs

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensors: SensorManager
    private lateinit var screen: Screen
    private lateinit var commands: Commands

    private val readings = Readings(seconds = 60.0)
    private val handler = Handler(Looper.getMainLooper())
    private var count = 0
    private var sensorName = "none"
    private val every = 500L

    private var shown: ByteArray? = null
    private var moved = true
    private val gravity = floatArrayOf(0f, 0f, 9.8f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        screen = Screen(this)

        commands = Commands(this)
        setContentView(screen.root)
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager

        Share.start()
    }

    override fun onResume() {
        super.onResume()


        val mag = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (mag != null) {
            sensorName = mag.name
            sensors.registerListener(this, mag, 0)
        }

        val accel = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel != null) sensors.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
        
        handler.post(tick)
        handler.post(draw)
    }

    override fun onPause() {
        super.onPause()
        sensors.unregisterListener(this)
        handler.removeCallbacks(tick)
        handler.removeCallbacks(draw)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            var shake = 0f

            for (i in 0 until 3) {
                shake += abs(event.values[i] - gravity[i])
                gravity[i] += 0.1f * (event.values[i] - gravity[i])
            }
            
            if (shake > 2f) {
                moved = true
                Laptop.reset()
            }
            return
        }

        readings.add(event.timestamp / 1e9, event.values[0], event.values[1], event.values[2])
        count++
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}


    private val tick = object : Runnable {
        override fun run() {
            screen.showStatus(sensorName, (count * 1000 / every).toInt())
            count = 0

            val snap = readings.snapshot()
            Share.latest = snap
            val step = Meter.measure(snap)
            screen.showSignal(step)

            val frame = Decoder.listen(snap)
            if (frame != null) screen.mode = frame.mode

            if (frame != null || step != null) {
                screen.showDoing(null)
            } else {
                val doing = Laptop.guess(snap)
                screen.showDoing(doing?.label)
                if (doing?.finished == true) laptopDone()
            }

            if (frame == null) {
                screen.idle()

            } else if (repeat(frame)) {
                // REPEAT sends the same thing every round, only show it again after the phone was picked up

            } else if (!frame.complete) {
                screen.showPartial(frame.bytes, frame.total, frame.progress)

            } else {
                shown = frame.bytes
                moved = false

                if (isPicture(frame.bytes)) {
                    screen.showPicture(frame.bytes)
                } else {
                    val text = frame.bytes.decodeToString()
                    screen.showMessage(commands.run(text) ?: text)
                }
            }

            handler.postDelayed(this, every)
        }
    }

    private fun laptopDone() {
        val vibrator = getSystemService(Vibrator::class.java)
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 150, 200), -1))

        screen.mode = ""
        screen.showMessage("Laptop's done")
    }

    private fun repeat(f: Frame): Boolean {
        val s = shown ?: return false
        if (moved) return false
        if (f.complete) return f.bytes.contentEquals(s)

        return f.total == s.size && f.bytes.indices.all { f.bytes[it] == s[it] }
    }

    private val draw = object : Runnable {
        override fun run() {
            screen.scope.show(readings.snapshot(), 5.0)
            handler.postDelayed(this, 100)
        }
    }

}
