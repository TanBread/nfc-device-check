package com.tanbread.nfcdevicecheck.checker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EnrolledDevice::class, CheckLog::class],
    version = 2,
    exportSchema = false,
)
abstract class CheckerDatabase : RoomDatabase() {
    abstract fun enrolledDeviceDao(): EnrolledDeviceDao
    abstract fun checkLogDao(): CheckLogDao

    companion object {
        @Volatile
        private var instance: CheckerDatabase? = null

        fun get(context: Context): CheckerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CheckerDatabase::class.java,
                    "checker.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { instance = it }
            }
    }
}
