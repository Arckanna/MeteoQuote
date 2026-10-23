package com.valerie.meteoquote.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Complete validated provider series, not a formatted or truncated screen snapshot. */
@Entity(tableName = "weather_snapshot")
data class WeatherRecord(
    @PrimaryKey val placeKey: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
    val fetchedAt: Long,
    val payload: String
)

@Entity(tableName = "air_snapshot")
data class AirRecord(@PrimaryKey val placeKey: String, val fetchedAt: Long, val payload: String)

@Dao
interface WeatherDao {
    @Transaction
    suspend fun snapshot(key: String): Pair<WeatherRecord?, AirRecord?> = weather(key) to air(key)
    @Query("SELECT * FROM weather_snapshot WHERE placeKey = :key")
    suspend fun weather(key: String): WeatherRecord?
    @Query("SELECT * FROM air_snapshot WHERE placeKey = :key")
    suspend fun air(key: String): AirRecord?
    @Query("SELECT * FROM weather_snapshot WHERE placeKey = :key")
    fun observeWeather(key: String): Flow<WeatherRecord?>
    @Query("SELECT * FROM air_snapshot WHERE placeKey = :key")
    fun observeAir(key: String): Flow<AirRecord?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putWeather(record: WeatherRecord)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAir(record: AirRecord)
    @Query("DELETE FROM weather_snapshot WHERE fetchedAt <= :cutoff")
    suspend fun purgeWeather(cutoff: Long)
    @Query("DELETE FROM air_snapshot WHERE fetchedAt <= :cutoff")
    suspend fun purgeAir(cutoff: Long)
    @Query("DELETE FROM weather_snapshot WHERE placeKey NOT IN (:keys)")
    suspend fun retainWeather(keys: List<String>)
    @Query("DELETE FROM air_snapshot WHERE placeKey NOT IN (:keys)")
    suspend fun retainAir(keys: List<String>)
    @Transaction
    suspend fun prune(cutoff: Long, keys: List<String>) {
        purgeWeather(cutoff)
        purgeAir(cutoff)
        retainWeather(keys)
        retainAir(keys)
    }
}

@Database(entities = [WeatherRecord::class, AirRecord::class], version = 1, exportSchema = true)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weather(): WeatherDao
}
