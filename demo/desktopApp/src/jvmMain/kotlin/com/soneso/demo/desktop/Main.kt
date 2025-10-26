package com.soneso.demo.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.soneso.demo.App
import org.jetbrains.skia.Image
import java.awt.Taskbar
import java.io.InputStream
import javax.imageio.ImageIO

fun loadIconResource(resourcePath: String): BitmapPainter {
    val stream: InputStream = object {}.javaClass.getResourceAsStream("/$resourcePath")
        ?: throw IllegalArgumentException("Resource not found: $resourcePath")
    val bytes = stream.readBytes()
    return BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}

fun setDockIcon() {
    try {
        if (Taskbar.isTaskbarSupported()) {
            val taskbar = Taskbar.getTaskbar()
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                val iconStream = object {}.javaClass.getResourceAsStream("/app_icon_256.png")
                if (iconStream != null) {
                    val iconImage = ImageIO.read(iconStream)
                    taskbar.setIconImage(iconImage)
                }
            }
        }
    } catch (e: Exception) {
        println("Failed to set dock icon: ${e.message}")
    }
}

fun main() = application {
    // Set dock icon on macOS
    setDockIcon()

    Window(
        onCloseRequest = ::exitApplication,
        title = "KMP Stellar SDK Demo",
        icon = loadIconResource("app_icon_256.png"),
        state = rememberWindowState(
            width = 700.dp,
            height = 900.dp
        )
    ) {
        App()
    }
}
