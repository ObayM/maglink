package dev.obayy.maglink

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class Screen(context: Context) {
    val root = LinearLayout(context)
    private val status = TextView(context)
    private val message = TextView(context)

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 96, 48, 48)
        status.textSize = 16f
        message.textSize = 40f
        message.gravity = Gravity.CENTER
        message.text = "listening..."
        root.addView(status)
        root.addView(message)
    }

    fun showStatus(sensor: String, perSecond: Int, buffered: Int) {
        status.text = "$sensor\n$perSecond readings/s, $buffered in buffer"
    }

    fun showMessage(text: String) {
        message.text = text
    }
}