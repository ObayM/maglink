
package dev.obayy.maglink

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensors: SensorManager
    private lateinit var screen: Screen
    private lateinit var commands: Commands

    private val readings = Readings(seconds = 60.0)
    private val handler = Handler(Looper.getMainLooper())
    private var count = 0
    private var sensorName = "none"

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
        readings.add(event.timestamp / 1e9, event.values[0], event.values[1], event.values[2])
        count++
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}


    private val tick = object : Runnable {
        override fun run() {
            screen.showStatus(sensorName, count)
            count = 0

            val snap = readings.snapshot()
            Share.latest = snap
            val message = Decoder.listen(snap)

            if (message != null) screen.showMessage(commands.run(message) ?: message)
            handler.postDelayed(this, 1000)
        }
    }

    private val draw = object : Runnable {
        override fun run() {
            screen.scope.show(readings.snapshot(), 5.0)
            handler.postDelayed(this, 100)
        }
    }

}
