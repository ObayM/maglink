package dev.obayy.maglink

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import java.time.LocalTime

const val VERSION = "v13"

class Screen(private val context: Context) {
    private val colors = paletteFor(context)
    val root = LinearLayout(context)
    val scope = Scope(context, colors)

    private val title = TextView(context)
    private val state = TextView(context)
    private val signal = TextView(context)
    private val message = TextView(context)
    private val picture = Picture(context, colors)
    
    private val time = TextView(context)
    private val history = TextView(context)
    private val status = TextView(context)
    private val earlier = mutableListOf<String>()
    private var last = ""
    private var receiving = false
    var mode = ""
    private val listening = "Listening to the laptop's magnetic field."



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
        state.text = listening

        style(signal, 15f, colors.muted)
        signal.visibility = View.GONE

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

        val under = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        under.topMargin = dp(12)
        root.addView(signal, under)

        root.addView(message, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(picture, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        picture.visibility = View.GONE


        root.addView(time)
        root.addView(history, gap)
        root.addView(status)
    }

    fun showStatus(sensor: String, perSecond: Int) {
        status.text = "$sensor, $perSecond readings a second. Build $VERSION."
    }

    fun showSignal(step: Double?) {
        if (step == null) {
            signal.visibility = View.GONE
            return
        }
        
        signal.text = "Signal ${"%.1f".format(step)} µT, ${Meter.advice(step)}"
        signal.visibility = View.VISIBLE
    }

     private fun remember(label: String) {
        if (last.isNotEmpty()) {
            earlier.add(0, "$last   ${time.text.removePrefix("Received at ")}")
            
            if (earlier.size > 4) earlier.removeAt(4)
            
            history.text = earlier.joinToString("\n")
        }

        last = label
        time.text = "Received at ${LocalTime.now().withNano(0)} in $mode mode"
    }

    fun showMessage(text: String) {
        remember(text)
        receiving = false
        scope.progress = 0f
        state.text = listening

        message.text = text
        message.setTextColor(colors.ink)
        message.visibility = View.VISIBLE
        picture.visibility = View.GONE
    }

    fun showPicture(b: ByteArray) {
        remember("A ${b[1]}×${b[2]} picture")
        receiving = false
        scope.progress = 0f
        state.text = listening
        picture.show(b)
        picture.visibility = View.VISIBLE
        message.visibility = View.GONE
    }

    fun showPartial(b: ByteArray, total: Int) {
        receiving = true
        scope.progress = b.size.toFloat() / total

        if (pictureBytes(b) == total) {
            state.text = "Receiving a ${b[1]}×${b[2]} picture in $mode mode..."
            picture.show(b)
            picture.visibility = View.VISIBLE
            message.visibility = View.GONE
        } else {
            
            state.text = "Reading in $mode mode..."
            message.text = b.decodeToString()
            message.setTextColor(colors.muted)
            message.visibility = View.VISIBLE
            picture.visibility = View.GONE
        }
    }

    fun idle() {
        if (!receiving) return
        receiving = false
        scope.progress = 0f
        state.text = "That one got scrambled. Try again?"
    }
}