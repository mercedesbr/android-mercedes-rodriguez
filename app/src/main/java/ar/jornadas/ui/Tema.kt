package ar.jornadas.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colores = lightColorScheme(
    primary = Color(0xFF185B54),
    secondary = Color(0xFF995136),
)

@Composable
fun JornadasTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colores, content = content)
}
