package ar.jornadas.data

/** Modelo que usan el ViewModel y la UI. No depende de Room ni de Retrofit. */
data class Charla(
    val id: String,
    val titulo: String,
    val tema: String,
    val dia: Int,
    val hora: String,
    val sala: String,
    val ponente: String,
    val resumen: String,
    val enMiAgenda: Boolean,
)

internal fun CharlaConFavorita.aModelo() = Charla(
    id = charla.id,
    titulo = charla.titulo,
    tema = charla.tema,
    dia = charla.dia,
    hora = charla.hora,
    sala = charla.sala,
    ponente = charla.ponente,
    resumen = charla.resumen,
    enMiAgenda = favorita,
)

/** Los tres tipos de falla se distinguen: conexión, HTTP no exitoso y respuesta con otro formato. */
sealed interface ResultadoSincronizacion {
    data object Exitosa : ResultadoSincronizacion
    data object SinConexion : ResultadoSincronizacion
    data class ErrorDelServidor(val codigo: Int) : ResultadoSincronizacion
    data object RespuestaInvalida : ResultadoSincronizacion
}

/** Estado de la descarga a nivel proceso: no depende de qué pantalla o ViewModel esté vivo. */
sealed interface EstadoDescarga {
    data object Inactiva : EstadoDescarga
    data object EnCurso : EstadoDescarga
    data class Terminada(val resultado: ResultadoSincronizacion) : EstadoDescarga
}
