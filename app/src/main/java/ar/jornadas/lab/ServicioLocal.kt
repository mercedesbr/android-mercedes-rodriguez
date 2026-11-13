package ar.jornadas.lab

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.* // OkHttp 3.14 (la que trae Retrofit 2.11): API estilo Java
import java.io.IOException

data class ConfigMesa(
    val disponible: Boolean = true,
    val revision: Int = ServicioLocal.REVISIONES.first,
    val demoraLarga: Boolean = false,
)

/** Simula el servidor del programa (interceptor de OkHttp) con opciones guardadas en SharedPreferences. */
class ServicioLocal(private val app: Context) : Interceptor {
    private val prefs by lazy { app.getSharedPreferences("mesa", Context.MODE_PRIVATE) }

    private val _llamadas = MutableStateFlow(0)
    val llamadas: StateFlow<Int> = _llamadas.asStateFlow()

    /** La primera lectura carga el archivo de preferencias desde disco: se hace en IO. */
    suspend fun leer(): ConfigMesa = withContext(Dispatchers.IO) { leerAhora() }

    /** apply() actualiza la memoria al instante y escribe en disco en segundo plano. */
    fun guardar(config: ConfigMesa) {
        prefs.edit()
            .putBoolean(CLAVE_DISPONIBLE, config.disponible)
            .putInt(CLAVE_REVISION, config.revision)
            .putBoolean(CLAVE_DEMORA, config.demoraLarga)
            .apply()
    }

    /**
     * Escritura inmediata en memoria (apply), para el extra de prueba del Intent:
     * tiene que valer ya para la primera petición. Puede cargar el archivo de preferencias
     * en el hilo principal; se acepta porque solo se usa en builds depurables.
     */
    fun guardarDisponibleAhora(disponible: Boolean) {
        prefs.edit().putBoolean(CLAVE_DISPONIBLE, disponible).apply()
    }

    private fun leerAhora() = ConfigMesa(
        disponible = prefs.getBoolean(CLAVE_DISPONIBLE, true),
        revision = prefs.getInt(CLAVE_REVISION, REVISIONES.first).coerceIn(REVISIONES),
        demoraLarga = prefs.getBoolean(CLAVE_DEMORA, false),
    )

    // Corre en un hilo de OkHttp, nunca en el principal.
    override fun intercept(chain: Interceptor.Chain): Response {
        _llamadas.update { it + 1 }
        val config = leerAhora() // cada solicitud captura disponibilidad y edición al comenzar
        Thread.sleep(if (config.demoraLarga) 8000L else 1400L)
        if (!config.disponible) throw IOException("Servicio desconectado")
        val json = app.assets.open("programa-${config.revision}.json").bufferedReader().use { it.readText() }
        return Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK").body(ResponseBody.create(MediaType.get("application/json"), json)).build()
    }

    companion object {
        val REVISIONES = 1..4
        private const val CLAVE_DISPONIBLE = "disponible"
        private const val CLAVE_REVISION = "revision"
        private const val CLAVE_DEMORA = "demoraLarga"
    }
}
