package dev.obayy.maglink

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import java.time.LocalTime

const val VERSION = "v7"

class Screen(private val context: Context) {
    private val colors = paletteFor(context)
    val root = LinearLayout(context)
    val scope = Scope(context, colors)
    private val title = TextView(context)
    private val state = TextView(context)
    private val message = TextView(context)
    private val time = TextView(context)
    private val history = TextView(context)
    private val status = TextView(context)
    private val earlier = mutableListOf<String>()

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private fun style(view: TextView, size: Float, color: Int) {
        view.textSize = size
        view.setTextColor(color)
    }

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(colors.ground)
        root.setPadding(dp(24), dp(64), dp(24), dp(32))
        root.clipToPadding = false

        style(title, 24f, colors.ink)
        title.typeface = Typeface.DEFAULT_BOLD
        title.text = "MagLink"

        style(state, 17f, colors.muted)
        state.text = "Listening to the laptop's magnetic field."

        style(message, 72f, colors.ink)
        message.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        message.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        message.setAutoSizeTextTypeUniformWithConfiguration(28, 72, 2, TypedValue.COMPLEX_UNIT_SP)
        message.hint = "Nothing yet"
        message.setHintTextColor(colors.muted)

        style(time, 17f, colors.muted)
        style(history, 17f, colors.muted)
        history.setLineSpacing(0f, 1.3f)
        style(status, 13f, colors.muted)

        val band = LayoutParams(LayoutParams.MATCH_PARENT, dp(160))
        band.topMargin = dp(32)
        band.leftMargin = -dp(24)
        band.rightMargin = -dp(24)

        val gap = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        gap.topMargin = dp(24)
        gap.bottomMargin = dp(24)

        root.addView(title)
        root.addView(state)
        root.addView(scope, band)
        root.addView(message, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(time)
        root.addView(history, gap)
        root.addView(status)
    }

    fun showStatus(sensor: String, perSecond: Int) {
        status.text = "$sensor, $perSecond readings a second. Build $VERSION."
    }

    fun showMessage(text: String) {
        if (message.text.isNotEmpty()) {
            earlier.add(0, "${message.text}   ${time.text.removePrefix("Received at ")}")
            if (earlier.size > 4) earlier.removeAt(4)
            history.text = earlier.joinToString("\n")
        }
        message.text = text
        time.text = "Received at ${LocalTime.now().withNano(0)}"
    }
}