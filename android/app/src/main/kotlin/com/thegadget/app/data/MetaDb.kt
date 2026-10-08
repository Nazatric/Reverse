package com.thegadget.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The web app's `metaStore` IndexedDB (parsed tags + artwork URLs, looked up lazily as covers
 * scroll into view) becomes a Room table. The artwork bytes themselves live under
 * `filesDir/artwork/<id>.jpg` — cropped to fill and re-encoded at JPEG .86 exactly like
 * `src/utils/image.ts`.
 */
@Entity(tableName = "track_meta")
data class TrackMetaEntity(
    @PrimaryKey @androidx.room.ColumnInfo(name = "id") val id: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val coverPath: String?,
    val durationMs: Long,
    val updatedAt: Long,
)

@Dao
interface MetaDao {
    @Query("SELECT * FROM track_meta WHERE id = :id")
    suspend fun get(id: String): TrackMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(meta: TrackMetaEntity)

    @Query("DELETE FROM track_meta")
    suspend fun clear()

    @Query("SELECT id FROM track_meta")
    suspend fun ids(): List<String>
}

@Database(entities = [TrackMetaEntity::class], version = 1, exportSchema = false)
abstract class MetaDb : RoomDatabase() {
    abstract fun meta(): MetaDao

    companion object {
        fun create(context: Context): MetaDb =
            Room.databaseBuilder(context, MetaDb::class.java, "gadget-meta").build()
    }
}
