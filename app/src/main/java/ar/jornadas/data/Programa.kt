package ar.jornadas.data

import retrofit2.http.GET

/**
 * DTO: refleja el JSON del servidor tal cual.
 * Todos los campos son anulables porque Gson no respeta la null safety de Kotlin:
 * si el backend omite un campo, lo detectamos al convertir y no con un NPE más adelante.
 */
data class FichaRemota(
    val id: String?,
    val title: String?,
    val track: String?,
    val day: Int?,
    val time: String?,
    val room: String?,
    val speaker: String?,
    val summary: String?,
)

interface ProgramaApi {
    // El arreglo puede traer elementos null: también se tratan al convertir.
    @GET("programa")
    suspend fun consultar(): List<FichaRemota?>
}

/** Mapper DTO → entidad. Devuelve null si falta un dato obligatorio, para descartar solo esa ficha. */
fun FichaRemota.aEntidad(): CharlaEntity? {
    return CharlaEntity(
        id = id?.takeIf { it.isNotBlank() } ?: return null,
        titulo = title?.takeIf { it.isNotBlank() } ?: return null,
        tema = track ?: return null,
        dia = day ?: return null,
        hora = time ?: return null,
        sala = room ?: return null,
        ponente = speaker.orEmpty(),
        resumen = summary.orEmpty(),
    )
}
