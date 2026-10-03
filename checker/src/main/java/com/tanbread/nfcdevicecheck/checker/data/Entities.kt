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
 * time. Raw identity values never exist on the Checker at all.
 */
@Entity(tableName = "enrolled_devices")
data class EnrolledDevice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val eid: String?,
    val imei1: String?,
    val imei2: String?,
    val androidVersion: String?,
    val buildNumber: String?,
    val enrolledAt: Long = System.currentTimeMillis(),
) {
    fun toBundle(): HashBundle = HashBundle(
        eid = eid,
        imei1 = imei1,
        imei2 = imei2,
        androidVersion = androidVersion,
        buildNumber = buildNumber,
    )

    companion object {
        fun fromBundle(label: String, bundle: HashBundle): EnrolledDevice = EnrolledDevice(
            label = label,
            eid = bundle.eid,
            imei1 = bundle.imei1,
            imei2 = bundle.imei2,
            androidVersion = bundle.androidVersion,
            buildNumber = bundle.buildNumber,
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
    val receivedEid: String?,
    val receivedImei1: String?,
    val receivedImei2: String?,
    val receivedAndroidVersion: String?,
    val receivedBuildNumber: String?,
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
