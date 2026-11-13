package ar.jornadas.ui

import ar.jornadas.data.Charla

enum class Seccion(val titulo: String, val icono: String) {
    PROGRAMA("Programa", "▤"),
    AGENDA("Mi agenda", "☆"),
    MESA("Mesa", "⚙"),
}

/** dia = 0: ambos días. tema = null: todos los temas. */
data class Filtros(val dia: Int = 0, val tema: String? = null)

/** Estados excluyentes de la sincronización con el servidor. */
sealed interface EstadoSincronizacion {
    data object Inactiva : EstadoSincronizacion
    data object EnCurso : EstadoSincronizacion
    data class Fallida(val mensaje: String) : EstadoSincronizacion
}

/** Todo lo que necesita la pantalla principal, en un único objeto inmutable. */
data class ProgramaUiState(
    val seccion: Seccion = Seccion.PROGRAMA,
    val filtros: Filtros = Filtros(),
    val temas: List<String> = emptyList(),
    val charlas: List<Charla> = emptyList(),
    val agenda: List<Charla> = emptyList(),
    /** Hay una edición descargada con éxito (puede ser vacía, como v4). */
    val hayEdicion: Boolean = false,
    val programaVacio: Boolean = false,
    /** false hasta que Room emite por primera vez: evita mostrar "vacío" mientras carga. */
    val datosListos: Boolean = false,
    /** La falla no reemplaza a los datos: se muestran ambos (offline-first). */
    val sincronizacion: EstadoSincronizacion = EstadoSincronizacion.Inactiva,
)

sealed interface DetalleUiState {
    data object Cerrado : DetalleUiState
    data object Cargando : DetalleUiState
    data class Abierto(val charla: Charla) : DetalleUiState
    /** La charla abierta ya no figura en el programa (por ejemplo, la quitó una sincronización). */
    data object NoDisponible : DetalleUiState
}
