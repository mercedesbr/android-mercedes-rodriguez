package ar.jornadas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ar.jornadas.lab.ConfigMesa
import ar.jornadas.lab.ServicioLocal
import ar.jornadas.servicios
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** config = null mientras se leen las preferencias. */
data class MesaUiState(
    val config: ConfigMesa? = null,
    val llamadas: Int = 0,
    val revisiones: List<Int> = ServicioLocal.REVISIONES.toList(),
)

/** Panel de control del servidor simulado. La UI ya no toca SharedPreferences. */
class MesaViewModel(private val servicio: ServicioLocal) : ViewModel() {
    private val config = MutableStateFlow<ConfigMesa?>(null)
    private val carga = viewModelScope.launch { config.value = servicio.leer() }

    val uiState: StateFlow<MesaUiState> =
        combine(config, servicio.llamadas) { config, llamadas -> MesaUiState(config, llamadas) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MesaUiState())

    fun cambiarDisponible(disponible: Boolean) = actualizar { it.copy(disponible = disponible) }

    fun elegirRevision(revision: Int) = actualizar { it.copy(revision = revision) }

    fun cambiarDemoraLarga(demoraLarga: Boolean) = actualizar { it.copy(demoraLarga = demoraLarga) }

    /**
     * Usado por el extra de prueba del Intent. Se escribe de forma sincrónica para que valga
     * para la carga automática; después se relee para que el panel muestre el valor real.
     */
    fun forzarDisponible(disponible: Boolean) {
        servicio.guardarDisponibleAhora(disponible)
        viewModelScope.launch {
            carga.join()
            config.value = servicio.leer()
        }
    }

    private fun actualizar(cambio: (ConfigMesa) -> ConfigMesa) {
        viewModelScope.launch {
            carga.join()
            val nueva = cambio(checkNotNull(config.value))
            config.value = nueva
            servicio.guardar(nueva)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { MesaViewModel(servicios().mesa) }
        }
    }
}
