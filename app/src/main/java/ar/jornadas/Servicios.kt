package ar.jornadas

import android.content.Context
import androidx.room.Room
import ar.jornadas.data.*
import ar.jornadas.lab.ServicioLocal
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

/**
 * Contenedor de dependencias de la app: se crea una sola vez (en [JornadasApp]),
 * así la base de datos, OkHttp y Retrofit son instancias únicas por proceso.
 * Es el único lugar donde se decide de dónde salen los datos.
 */
class Servicios(context: Context) {
    private val app = context.applicationContext

    val mesa: ServicioLocal by lazy { ServicioLocal(app) }

    private val baseDeDatos by lazy {
        Room.databaseBuilder(app, JornadasDatabase::class.java, JornadasDatabase.NOMBRE)
            .addMigrations(MIGRACION_1_2)
            .build()
    }

    private val cliente by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS) // también cubre al interceptor de prueba
            .addInterceptor(RegistroHttp())
            .addInterceptor(mesa)
            .build()
    }

    private val api by lazy {
        Retrofit.Builder()
            .baseUrl(URL_BASE)
            .client(cliente)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProgramaApi::class.java)
    }

    /** Vive lo mismo que el proceso: las descargas no dependen de ninguna pantalla. */
    private val alcanceApp = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val agenda: AgendaRepository by lazy { AgendaRepositoryImpl(api, baseDeDatos.programaDao(), alcanceApp) }

    private companion object {
        const val URL_BASE = "https://programa.invalid/"
        const val TIMEOUT_SEGUNDOS = 15L
    }
}
