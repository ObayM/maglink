
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
    private val readings = Readings(seconds = 60.0)
    private val handler = Handler(Looper.getMainLooper())
    private var count = 0
    private var sensorName = "none"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        screen = Screen(this)
        setContentView(screen.root)
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    override fun onResume() {
        super.onResume()

        val mag = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
            ?: sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (mag != null) {
            sensorName = mag.name
            sensors.registerListener(this, mag, 0)
        }
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        sensors.unregisterListener(this)
        handler.removeCallbacks(tick)
    }

    override fun onSensorChanged(event: SensorEvent) {
        readings.add(event.timestamp / 1e9, event.values[0], event.values[1], event.values[2])
        count++
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}


    private val tick = object : Runnable {
        override fun run() {
            screen.showStatus(sensorName, count, readings.size)
            count = 0
            
            val message = Decoder.listen(readings.snapshot())
            if (message != null) screen.showMessage(message)
            handler.postDelayed(this, 1000)
        }
    }
}
