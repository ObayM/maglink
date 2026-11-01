package dev.obayy.maglink

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator

class Commands(private val context: Context) {
    private var torchOn = false

    fun run(message: String): String? {
        if (!message.startsWith("!")) return null
        
        val parts = message.removePrefix("!").split(" ", limit = 2)
        val argument = parts.getOrNull(1)

        return when (parts[0]) {
            "flash" -> flash()
            "buzz" -> buzz()
            "open" -> open(argument)
            else -> "Unknown command ${parts[0]}"
        }
    }

    private fun flash(): String {
        val cameras = context.getSystemService(CameraManager::class.java)
        
        val id = cameras.cameraIdList.first {
            cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }

        torchOn = !torchOn
        cameras.setTorchMode(id, torchOn)
        return if (torchOn) "Flashlight on" else "Flashlight off"
    }

    private fun buzz(): String {
        val vibrator = context.getSystemService(Vibrator::class.java)
        
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        
        return "Buzzed"
    }

    private fun open(link: String?): String {
        if (link == null || !link.startsWith("http")) return "Not a link"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        
        return "Opened $link"
    }
}