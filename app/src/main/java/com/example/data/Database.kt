package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rhythms")
data class RhythmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val bpm: Float,
    val measures: Int,
    val eventsJson: String // Serialized IntArray of [tick, padIndex, velocity, tick, padIndex, velocity...]
)

@Dao
interface RhythmDao {
    @Query("SELECT * FROM rhythms")
    fun getAllRhythms(): Flow<List<RhythmEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRhythm(rhythm: RhythmEntity)

    @Query("DELETE FROM rhythms WHERE id = :id")
    suspend fun deleteRhythm(id: Int)
}

@Database(entities = [RhythmEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rhythmDao(): RhythmDao
}
