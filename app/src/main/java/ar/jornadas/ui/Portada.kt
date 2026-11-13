package ar.jornadas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.jornadas.data.Charla

/** Conecta: obtiene el estado de los ViewModels y les pasa las intenciones. */
@Composable
fun PortadaRoute(programa: ProgramaModel, mesa: MesaViewModel) {
    val estado by programa.uiState.collectAsStateWithLifecycle()
    val detalle by programa.detalle.collectAsStateWithLifecycle()
    Portada(
        estado = estado,
        detalle = detalle,
        onSeccion = programa::irA,
        onAbrir = programa::abrirCharla,
        onVolver = programa::volver,
        onFavorita = programa::alternarFavorita,
        onDia = programa::elegirDia,
        onTema = programa::elegirTema,
        onActualizar = programa::actualizar,
        mesa = { MesaRoute(mesa) },
    )
}

/** Sin estado propio: dibuja lo que recibe y emite eventos. Se puede previsualizar y probar. */
@Composable
fun Portada(
    estado: ProgramaUiState,
    detalle: DetalleUiState,
    onSeccion: (Seccion) -> Unit,
    onAbrir: (String) -> Unit,
    onVolver: () -> Unit,
    onFavorita: (String) -> Unit,
    onDia: (Int) -> Unit,
    onTema: (String?) -> Unit,
    onActualizar: () -> Unit,
    mesa: @Composable () -> Unit,
) {
    val enDetalle = detalle != DetalleUiState.Cerrado
    BackHandler(enabled = enDetalle || estado.seccion != Seccion.PROGRAMA, onBack = onVolver)
    Scaffold(bottomBar = {
        if (!enDetalle) NavigationBar {
            Seccion.entries.forEach { seccion ->
                NavigationBarItem(
                    selected = estado.seccion == seccion,
                    onClick = { onSeccion(seccion) },
                    icon = { Text(seccion.icono) },
                    label = { Text(seccion.titulo) },
                )
            }
        }
    }) { margen ->
        Column(
            Modifier.fillMaxSize().padding(margen).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Jornadas del Sur", style = MaterialTheme.typography.labelLarge)
            if (enDetalle) {
                Detalle(detalle, onVolver, onFavorita)
            } else {
                Text(estado.seccion.titulo, style = MaterialTheme.typography.headlineLarge)
                when (estado.seccion) {
                    Seccion.PROGRAMA -> Catalogo(estado, onDia, onTema, onActualizar, onAbrir)
                    Seccion.AGENDA -> MiAgenda(estado.agenda, onAbrir)
                    Seccion.MESA -> mesa()
                }
            }
        }
    }
}

@Composable
private fun Detalle(detalle: DetalleUiState, onVolver: () -> Unit, onFavorita: (String) -> Unit) {
    TextButton(onClick = onVolver) { Text("Volver") }
    when (detalle) {
        DetalleUiState.Cerrado -> Unit
        DetalleUiState.Cargando -> LinearProgressIndicator(Modifier.fillMaxWidth())
        DetalleUiState.NoDisponible -> Text("Esta charla ya no figura en el programa.")
        is DetalleUiState.Abierto -> FichaCompleta(detalle.charla, onFavorita)
    }
}

@Composable
private fun FichaCompleta(ficha: Charla, onFavorita: (String) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(ficha.titulo, style = MaterialTheme.typography.headlineMedium)
        Text(ficha.encabezado)
        Text(ficha.ponente, style = MaterialTheme.typography.titleMedium)
        Text(ficha.resumen)
        Button(onClick = { onFavorita(ficha.id) }) {
            Text(if (ficha.enMiAgenda) "Quitar de mi agenda" else "Agregar a mi agenda")
        }
    }
}

@Composable
private fun MiAgenda(charlas: List<Charla>, onAbrir: (String) -> Unit) {
    if (charlas.isEmpty()) Text("Todavía no agregaste charlas.")
    else Grilla(charlas, onAbrir)
}
