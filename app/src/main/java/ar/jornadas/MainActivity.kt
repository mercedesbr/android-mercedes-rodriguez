package ar.jornadas

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import ar.jornadas.ui.*

class MainActivity : ComponentActivity() {
    private val programa: ProgramaModel by viewModels { ProgramaModel.Factory }
    private val mesa: MesaViewModel by viewModels { MesaViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) aplicarExtraDePrueba()
        setContent { JornadasTheme { PortadaRoute(programa, mesa) } }
    }

    /**
     * Gancho de prueba: permite iniciar con el servicio apagado (adb ... --ez servicio_disponible false).
     * Solo en builds depurables, porque la Activity es exportada y cualquier app podría enviar el extra.
     * La Activity no modifica estado: le pasa la intención al ViewModel.
     */
    private fun aplicarExtraDePrueba() {
        val depurable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (depurable && intent.hasExtra(EXTRA_SERVICIO_DISPONIBLE)) {
            mesa.forzarDisponible(intent.getBooleanExtra(EXTRA_SERVICIO_DISPONIBLE, true))
            intent.removeExtra(EXTRA_SERVICIO_DISPONIBLE)
        }
    }

    private companion object {
        const val EXTRA_SERVICIO_DISPONIBLE = "servicio_disponible"
    }
}
