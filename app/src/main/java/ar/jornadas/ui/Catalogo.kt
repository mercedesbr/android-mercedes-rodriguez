package ar.jornadas.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.jornadas.data.Charla

private val OPCIONES_DIA = listOf("Ambos días", "Día 1", "Día 2") // el índice coincide con Filtros.dia

/** Sin estado propio ni lógica de filtrado: recibe la lista ya filtrada por el ViewModel. */
@Composable
fun Catalogo(
    estado: ProgramaUiState,
    onDia: (Int) -> Unit,
    onTema: (String?) -> Unit,
    onActualizar: () -> Unit,
    onAbrir: (String) -> Unit,
) {
    val temas = listOf<String?>(null) + estado.temas
    val sincronizando = estado.sincronizacion == EstadoSincronizacion.EnCurso
    val falla = estado.sincronizacion as? EstadoSincronizacion.Fallida

    // Filtros, avisos y charlas en una sola lista desplazable: en horizontal no hay altura
    // para todos los controles fijos y la lista quedaba invisible.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item(key = "filtros") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Selector(OPCIONES_DIA, estado.filtros.dia, onDia)
                Selector(temas.map { it ?: "Todos" }, temas.indexOf(estado.filtros.tema)) { onTema(temas[it]) }
                TextButton(onClick = onActualizar, enabled = !sincronizando) { Text("Actualizar") }
                if (sincronizando || !estado.datosListos) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (falla != null) item(key = "falla") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(falla.mensaje)
                if (estado.hayEdicion) Text("Se están usando datos guardados.")
                OutlinedButton(onClick = onActualizar) { Text("Reintentar") }
            }
        }
        when {
            !estado.datosListos -> Unit
            !estado.hayEdicion -> Unit // nunca se descargó: solo progreso o falla con reintento
            estado.programaVacio -> item(key = "vacio") { Text("Sin charlas programadas") }
            estado.charlas.isEmpty() -> item(key = "sin-resultados") { Text("No hay charlas para este filtro.") }
            else -> items(estado.charlas, key = { it.id }) { TarjetaCharla(it, onAbrir) }
        }
    }
}

@Composable
private fun Selector(opciones: List<String>, indice: Int, elegir: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEachIndexed { i, texto ->
            FilterChip(selected = i == indice, onClick = { elegir(i) }, label = { Text(texto) })
        }
    }
}

/** Formato de presentación en un solo lugar (antes estaba repetido en dos pantallas). */
internal val Charla.encabezado: String get() = "Día $dia · $hora · $sala"

@Composable
fun Grilla(fichas: List<Charla>, abrir: (String) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        items(fichas, key = { it.id }) { TarjetaCharla(it, abrir) }
    }
}

@Composable
private fun TarjetaCharla(ficha: Charla, abrir: (String) -> Unit) {
    ElevatedCard(onClick = { abrir(ficha.id) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(ficha.encabezado, style = MaterialTheme.typography.labelMedium)
            Text(ficha.titulo, style = MaterialTheme.typography.titleLarge)
            Text(if (ficha.enMiAgenda) "${ficha.tema} · En mi agenda" else ficha.tema)
        }
    }
}
