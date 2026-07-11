package pw.binom.proxy

import java.awt.AWTException
import java.awt.Color
import java.awt.Font
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/**
 * Manages the system tray icon for the proxy server.
 * Activated via the `--tray` command-line argument.
 */
object TrayManager {
    private var trayIcon: TrayIcon? = null

    /**
     * Shows the tray icon with a right-click context menu.
     * The menu contains a single "Выход" item that calls [onExit].
     */
    fun show(onExit: () -> Unit) {
        if (!SystemTray.isSupported()) {
            println("[Tray] System tray is not supported")
            return
        }

        val tray = SystemTray.getSystemTray()

        // Generate a simple 16x16 icon programmatically
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics().apply {
            color = Color(0x22, 0x66, 0xCC)
            fillOval(0, 0, 16, 16)
            color = Color.WHITE
            font = Font("SansSerif", Font.BOLD, 10)
            val fm = fontMetrics
            val text = "P"
            val x = (16 - fm.stringWidth(text)) / 2
            val y = ((16 + fm.height) / 2) - 2
            drawString(text, x, y)
            dispose()
        }

        // Context menu
        val popup = PopupMenu()
        val exitItem = MenuItem("Выход")
        exitItem.addActionListener {
            hide()
            onExit()
        }
        popup.add(exitItem)

        val icon = TrayIcon(image, "Proxy Server", popup).apply {
            isImageAutoSize = true
        }
        trayIcon = icon

        try {
            tray.add(icon)
            println("[Tray] Icon added")
        } catch (e: AWTException) {
            println("[Tray] Failed to add icon: ${e.message}")
            trayIcon = null
        }
    }

    /**
     * Removes the tray icon if it is currently visible.
     */
    fun hide() {
        trayIcon?.also { icon ->
            try {
                SystemTray.getSystemTray().remove(icon)
            } catch (_: Exception) { /* ignore */ }
            trayIcon = null
        }
    }
}
