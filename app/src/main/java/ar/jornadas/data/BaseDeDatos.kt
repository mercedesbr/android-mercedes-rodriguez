package ar.jornadas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Programa publicado por el servidor. No guarda datos del usuario: se reemplaza en cada sincronización. */
@Entity(tableName = "charlas")
data class CharlaEntity(
    @PrimaryKey val id: String,
    val titulo: String,
    val tema: String,
    val dia: Int,
    val hora: String,
    val sala: String,
    val ponente: String,
    val resumen: String,
)

/**
 * Charlas que el usuario agregó a su agenda. La sincronización no pisa las marcas de charlas vigentes;
 * solo descarta las de charlas retiradas (si una charla vuelve, vuelve sin marcar).
 */
@Entity(tableName = "favoritas")
data class FavoritaEntity(@PrimaryKey val charlaId: String)

/**
 * Fila única que registra la última edición descargada con éxito.
 * Permite distinguir "nunca descargué" de "descargué una edición vacía" (v4).
 */
@Entity(tableName = "edicion")
data class EdicionEntity(@PrimaryKey val id: Int = UNICA, val descargadaEn: Long) {
    companion object {
        const val UNICA = 0
    }
}

/** Resultado compuesto de una consulta (no es una tabla): la charla y si está en la agenda. */
data class CharlaConFavorita(
    @Embedded val charla: CharlaEntity,
    val favorita: Boolean,
)

private const val CHARLA_CON_FAVORITA =
    "SELECT c.*, EXISTS(SELECT 1 FROM favoritas f WHERE f.charlaId = c.id) AS favorita FROM charlas c"
private const val ORDEN = " ORDER BY c.dia, c.hora, c.sala"

@Dao
abstract class ProgramaDao {
    // dia = 0 significa "ambos días"; tema = null significa "todos".
    @Query(CHARLA_CON_FAVORITA + " WHERE (:dia = 0 OR c.dia = :dia) AND (:tema IS NULL OR c.tema = :tema)" + ORDEN)
    abstract fun observarPrograma(dia: Int, tema: String?): Flow<List<CharlaConFavorita>>

    @Query(CHARLA_CON_FAVORITA + " WHERE c.id IN (SELECT charlaId FROM favoritas)" + ORDEN)
    abstract fun observarAgenda(): Flow<List<CharlaConFavorita>>

    @Query(CHARLA_CON_FAVORITA + " WHERE c.id = :id")
    abstract fun observarCharla(id: String): Flow<CharlaConFavorita?>

    @Query("SELECT DISTINCT tema FROM charlas ORDER BY tema")
    abstract fun observarTemas(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM charlas")
    abstract fun observarCantidad(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM edicion)")
    abstract fun observarHayEdicion(): Flow<Boolean>

    @Upsert
    abstract suspend fun registrarEdicion(edicion: EdicionEntity)

    @Query("DELETE FROM favoritas WHERE charlaId NOT IN (SELECT id FROM charlas)")
    abstract suspend fun descartarFavoritasRetiradas()

    @Query("DELETE FROM charlas")
    abstract suspend fun borrarPrograma()

    @Insert
    abstract suspend fun insertarPrograma(charlas: List<CharlaEntity>)

    /**
     * Deja la tabla igual a lo que publicó el servidor, incluidas las bajas.
     * Es atómico: si algo falla, el programa anterior queda intacto.
     * Las marcas de charlas vigentes no se tocan (incluso si cambiaron durante la petición);
     * las de charlas retiradas se descartan.
     */
    @Transaction
    open suspend fun reemplazarPrograma(charlas: List<CharlaEntity>, descargadaEn: Long) {
        borrarPrograma()
        insertarPrograma(charlas)
        descartarFavoritasRetiradas()
        registrarEdicion(EdicionEntity(descargadaEn = descargadaEn))
    }

    @Query("SELECT EXISTS(SELECT 1 FROM favoritas WHERE charlaId = :id)")
    abstract suspend fun esFavorita(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun agregarFavorita(favorita: FavoritaEntity)

    @Query("DELETE FROM favoritas WHERE charlaId = :id")
    abstract suspend fun quitarFavorita(id: String)

    @Transaction
    open suspend fun alternarFavorita(id: String) {
        if (esFavorita(id)) quitarFavorita(id) else agregarFavorita(FavoritaEntity(id))
    }
}

@Database(entities = [CharlaEntity::class, FavoritaEntity::class, EdicionEntity::class], version = 2, exportSchema = true)
abstract class JornadasDatabase : RoomDatabase() {
    abstract fun programaDao(): ProgramaDao

    companion object {
        const val NOMBRE = "cartelera.db"
    }
}

/**
 * v1 → v2: la favorita sale de la tabla charlas y pasa a la tabla favoritas, y se agrega la tabla edicion.
 * Conserva las favoritas existentes. Si ya había charlas, el usuario ya tenía una edición descargada.
 * SQLite de API 26 no soporta DROP COLUMN, por eso se recrea la tabla.
 */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `favoritas` (`charlaId` TEXT NOT NULL, PRIMARY KEY(`charlaId`))")
        db.execSQL("INSERT OR IGNORE INTO favoritas (charlaId) SELECT id FROM charlas WHERE favorita = 1")
        db.execSQL("CREATE TABLE IF NOT EXISTS `edicion` (`id` INTEGER NOT NULL, `descargadaEn` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO edicion (id, descargadaEn) SELECT 0, 0 WHERE EXISTS (SELECT 1 FROM charlas)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `charlas_nueva` (`id` TEXT NOT NULL, `titulo` TEXT NOT NULL, " +
                "`tema` TEXT NOT NULL, `dia` INTEGER NOT NULL, `hora` TEXT NOT NULL, `sala` TEXT NOT NULL, " +
                "`ponente` TEXT NOT NULL, `resumen` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "INSERT INTO charlas_nueva (id, titulo, tema, dia, hora, sala, ponente, resumen) " +
                "SELECT id, titulo, tema, dia, hora, sala, ponente, resumen FROM charlas"
        )
        db.execSQL("DROP TABLE charlas")
        db.execSQL("ALTER TABLE charlas_nueva RENAME TO charlas")
    }
}
