package ar.jornadas.data

import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Contrato que conoce el ViewModel: no sabe si los datos vienen de Room, de Retrofit o de un fake. */
interface AgendaRepository {
    fun programa(dia: Int, tema: String?): Flow<List<Charla>>
    fun agenda(): Flow<List<Charla>>
    fun charla(id: String): Flow<Charla?>
    fun temas(): Flow<List<String>>
    fun cantidadDeCharlas(): Flow<Int>

    /** true si alguna vez se descargó una edición con éxito (aunque haya sido vacía). */
    fun hayEdicion(): Flow<Boolean>

    val estadoDescarga: StateFlow<EstadoDescarga>

    /** Carga automática: a lo sumo una por proceso, la pidan cuantos ViewModels la pidan. */
    fun cargaAutomatica()

    /** Actualización explícita. Si ya hay una descarga en curso, no inicia otra. */
    fun actualizar()

    suspend fun alternarFavorita(id: String)
}

/**
 * Room es la fuente de verdad (offline-first); Retrofit solo la actualiza.
 * Es una instancia única por proceso: las descargas corren en [alcance] (scope de la aplicación),
 * así no se cancelan si la pantalla se destruye a mitad de una petición.
 */
class AgendaRepositoryImpl(
    private val api: ProgramaApi,
    private val dao: ProgramaDao,
    private val alcance: CoroutineScope,
    private val reloj: () -> Long = System::currentTimeMillis,
) : AgendaRepository {

    private val _estadoDescarga = MutableStateFlow<EstadoDescarga>(EstadoDescarga.Inactiva)
    override val estadoDescarga: StateFlow<EstadoDescarga> = _estadoDescarga.asStateFlow()
    private val cargaAutomaticaHecha = AtomicBoolean(false)

    override fun programa(dia: Int, tema: String?) =
        dao.observarPrograma(dia, tema).map { lista -> lista.map { it.aModelo() } }

    override fun agenda() = dao.observarAgenda().map { lista -> lista.map { it.aModelo() } }

    override fun charla(id: String) = dao.observarCharla(id).map { it?.aModelo() }

    override fun temas() = dao.observarTemas()

    override fun cantidadDeCharlas() = dao.observarCantidad()

    override fun hayEdicion() = dao.observarHayEdicion()

    override fun cargaAutomatica() {
        if (cargaAutomaticaHecha.compareAndSet(false, true)) actualizar()
    }

    @Synchronized
    override fun actualizar() {
        if (_estadoDescarga.value == EstadoDescarga.EnCurso) return // dos toques = un intento
        _estadoDescarga.value = EstadoDescarga.EnCurso
        alcance.launch { _estadoDescarga.value = EstadoDescarga.Terminada(descargar()) }
    }

    // Room y Retrofit con suspend ya ejecutan fuera del hilo principal.
    private suspend fun descargar(): ResultadoSincronizacion = try {
        val fichas = api.consultar()
        val charlas = fichas.mapNotNull { it?.aEntidad() }.distinctBy { it.id }
        if (fichas.isNotEmpty() && charlas.isEmpty()) {
            // Llegaron datos pero ninguno es válido: no borramos el programa guardado.
            ResultadoSincronizacion.RespuestaInvalida
        } else {
            // Una lista vacía es una edición válida (v4): se guarda como tal.
            dao.reemplazarPrograma(charlas, reloj())
            ResultadoSincronizacion.Exitosa
        }
    } catch (e: HttpException) {          // 4xx / 5xx: Retrofit lo lanza porque no usamos Response<T>
        ResultadoSincronizacion.ErrorDelServidor(e.code())
    } catch (e: JsonParseException) {     // el backend mandó otra estructura
        ResultadoSincronizacion.RespuestaInvalida
    } catch (e: MalformedJsonException) { // JSON roto: es IOException, pero no es un problema de red
        ResultadoSincronizacion.RespuestaInvalida
    } catch (e: IOException) {            // sin conexión, timeout, DNS
        ResultadoSincronizacion.SinConexion
    }

    override suspend fun alternarFavorita(id: String) = dao.alternarFavorita(id)
}
