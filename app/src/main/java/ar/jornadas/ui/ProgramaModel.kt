package ar.jornadas.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ar.jornadas.data.AgendaRepository
import ar.jornadas.data.EstadoDescarga
import ar.jornadas.data.ResultadoSincronizacion
import ar.jornadas.servicios
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla principal.
 * - Recibe el repository por constructor (se puede probar con un fake).
 * - Expone solo StateFlow de lectura; la UI comunica intenciones llamando funciones.
 * - Navegación y filtros viven en SavedStateHandle: sobreviven a la rotación y al process death.
 * - El control de descargas (una automática por proceso, sin superposición) vive en el repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgramaModel(
    private val repo: AgendaRepository,
    private val estadoGuardado: SavedStateHandle,
) : ViewModel() {

    private val seccion = estadoGuardado.getStateFlow(CLAVE_SECCION, Seccion.PROGRAMA.name).map { aSeccion(it) }
    private val filtros = combine(
        estadoGuardado.getStateFlow(CLAVE_DIA, 0),
        estadoGuardado.getStateFlow<String?>(CLAVE_TEMA, null),
    ) { dia, tema -> Filtros(dia, tema) }
    private val detalleId = estadoGuardado.getStateFlow<String?>(CLAVE_DETALLE, null)

    private val catalogo = combine(
        filtros,
        filtros.flatMapLatest { repo.programa(it.dia, it.tema) },
        repo.temas(),
        repo.cantidadDeCharlas(),
        repo.hayEdicion(),
    ) { filtros, charlas, temas, cantidad, hayEdicion ->
        ProgramaUiState(
            filtros = filtros,
            charlas = charlas,
            // Si el tema elegido desapareció del programa, se sigue mostrando para poder quitarlo.
            temas = (temas + listOfNotNull(filtros.tema)).distinct(),
            hayEdicion = hayEdicion,
            programaVacio = cantidad == 0,
            datosListos = true,
        )
    }

    val uiState: StateFlow<ProgramaUiState> =
        combine(catalogo, seccion, repo.agenda(), repo.estadoDescarga) { base, seccion, agenda, descarga ->
            base.copy(seccion = seccion, agenda = agenda, sincronizacion = aEstadoSincronizacion(descarga))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProgramaUiState(seccion = aSeccion(estadoGuardado[CLAVE_SECCION])),
        )

    val detalle: StateFlow<DetalleUiState> = detalleId.flatMapLatest { id ->
        if (id == null) flowOf(DetalleUiState.Cerrado)
        else repo.charla(id).map { charla -> charla?.let(DetalleUiState::Abierto) ?: DetalleUiState.NoDisponible }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = if (detalleId.value == null) DetalleUiState.Cerrado else DetalleUiState.Cargando,
    )

    init {
        // El repository garantiza que sea una sola por proceso, aunque se creen varios ViewModels.
        repo.cargaAutomatica()
    }

    fun actualizar() = repo.actualizar()

    fun irA(seccion: Seccion) { estadoGuardado[CLAVE_SECCION] = seccion.name }

    fun elegirDia(dia: Int) { estadoGuardado[CLAVE_DIA] = dia }

    fun elegirTema(tema: String?) { estadoGuardado[CLAVE_TEMA] = tema }

    fun abrirCharla(id: String) { estadoGuardado[CLAVE_DETALLE] = id }

    /** Atrás: primero cierra el detalle; si no hay detalle, vuelve a Programa. */
    fun volver() {
        if (detalleId.value != null) estadoGuardado[CLAVE_DETALLE] = null
        else irA(Seccion.PROGRAMA)
    }

    fun alternarFavorita(id: String) {
        viewModelScope.launch { repo.alternarFavorita(id) }
    }

    companion object {
        private const val CLAVE_SECCION = "seccion"
        private const val CLAVE_DIA = "dia"
        private const val CLAVE_TEMA = "tema"
        private const val CLAVE_DETALLE = "detalle"

        private fun aSeccion(nombre: String?) = Seccion.entries.find { it.name == nombre } ?: Seccion.PROGRAMA

        private fun aEstadoSincronizacion(descarga: EstadoDescarga): EstadoSincronizacion = when (descarga) {
            EstadoDescarga.Inactiva -> EstadoSincronizacion.Inactiva
            EstadoDescarga.EnCurso -> EstadoSincronizacion.EnCurso
            is EstadoDescarga.Terminada -> when (val resultado = descarga.resultado) {
                ResultadoSincronizacion.Exitosa -> EstadoSincronizacion.Inactiva
                ResultadoSincronizacion.SinConexion ->
                    EstadoSincronizacion.Fallida("No se pudo conectar con el servidor del programa.")
                is ResultadoSincronizacion.ErrorDelServidor ->
                    EstadoSincronizacion.Fallida("El servidor respondió con un error (${resultado.codigo}).")
                ResultadoSincronizacion.RespuestaInvalida ->
                    EstadoSincronizacion.Fallida("El programa recibido tiene un formato inesperado.")
            }
        }

        val Factory = viewModelFactory {
            initializer { ProgramaModel(servicios().agenda, createSavedStateHandle()) }
        }
    }
}
