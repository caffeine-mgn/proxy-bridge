package ru.proxybridge.front

import androidx.compose.runtime.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable

object Colors {
    val bg = Color("#121212")
    val surface = Color("#1E1E1E")
    val text = Color("#E0E0E0")
    val textMuted = Color("#9E9E9E")
}

@Composable
fun App() {
    Div(
        attrs = {
            style {
                display(DisplayStyle.Flex)
                alignItems(AlignItems.Center)
                justifyContent(JustifyContent.Center)
                height(100.percent)
                backgroundColor(Colors.bg)
                color(Colors.text)
                fontFamily("system-ui, sans-serif")
            }
        }
    ) {
        Div(
            attrs = {
                style {
                    property("text-align", "center")
                    padding(40.px)
                }
            }
        ) {
            H1(attrs = {
                style {
                    fontSize(28.px)
                    fontWeight("600")
                    color(Colors.text)
                    marginBottom(12.px)
                }
            }) { Text("proxy-bridge") }

            P(attrs = {
                style {
                    fontSize(14.px)
                    color(Colors.textMuted)
                    property("line-height", "1.5")
                }
            }) { Text("Frontend module. Work in progress.") }
        }
    }
}

fun main() {
    renderComposable(rootElementId = "root") {
        App()
    }
}
