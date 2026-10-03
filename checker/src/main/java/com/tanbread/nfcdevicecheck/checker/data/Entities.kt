package com.tanbread.nfcdevicecheck.checker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.tanbread.nfcdevicecheck.shared.hashing.HashBundle
import kotlinx.coroutines.flow.Flow

/**
 * A device accepted by this Checker: the per-field hashes captured at enroll
 * time, stored as the bundle's JSON. Raw system values never exist on the
 * Checker at all.
 */
@Entity(tableName = "enrolled_devices")
data class EnrolledDevice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val bundleJson: String,
    val enrolledAt: Long = System.currentTimeMillis(),
) {
    fun toBundle(): HashBundle = HashBundle.fromJson(bundleJson) ?: HashBundle(emptyMap())

    companion object {
        fun fromBundle(label: String, bundle: HashBundle): EnrolledDevice = EnrolledDevice(
            label = label,
            bundleJson = bundle.toJson(),
        )
    }
}

/** One accept/deny decision, kept for the Checker's history log. */
@Entity(tableName = "check_log")
data class CheckLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val accepted: Boolean,
    val deviceLabel: String?,
    val mismatchedFields: String,
    val receivedBundleJson: String,
)

@Dao
interface EnrolledDeviceDao {
    @Query("SELECT * FROM enrolled_devices ORDER BY enrolledAt DESC")
    fun observeAll(): Flow<List<EnrolledDevice>>

    @Query("SELECT COUNT(*) FROM enrolled_devices")
    suspend fun count(): Int

    @Insert
    suspend fun insert(device: EnrolledDevice): Long

    @Query("DELETE FROM enrolled_devices WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM enrolled_devices")
    suspend fun clear()
}

@Dao
interface CheckLogDao {
    @Query("SELECT * FROM check_log ORDER BY timestamp DESC LIMIT 200")
    fun observeRecent(): Flow<List<CheckLog>>

    @Insert
    suspend fun insert(log: CheckLog): Long

    @Query("DELETE FROM check_log")
    suspend fun clear()
}
