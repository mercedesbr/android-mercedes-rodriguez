package ar.jornadas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MesaRoute(viewModel: MesaViewModel) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    Mesa(
        estado = estado,
        onDisponible = viewModel::cambiarDisponible,
        onRevision = viewModel::elegirRevision,
        onDemoraLarga = viewModel::cambiarDemoraLarga,
    )
}

/** Sin copias locales del estado: la única fuente es MesaUiState. */
@Composable
fun Mesa(
    estado: MesaUiState,
    onDisponible: (Boolean) -> Unit,
    onRevision: (Int) -> Unit,
    onDemoraLarga: (Boolean) -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Control del servicio de prueba", style = MaterialTheme.typography.titleMedium)
        Text("Las opciones se aplican a la próxima petición.")
        val config = estado.config
        if (config == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            Text("Servicio disponible")
            Switch(checked = config.disponible, onCheckedChange = onDisponible)
            Text("Edición del programa")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                estado.revisiones.forEach {
                    FilterChip(config.revision == it, { onRevision(it) }, { Text("v$it") })
                }
            }
            Text("Peticiones en esta sesión: ${estado.llamadas}")
            Text("Demora larga (8 segundos)")
            Switch(checked = config.demoraLarga, onCheckedChange = onDemoraLarga)
        }
    }
}
