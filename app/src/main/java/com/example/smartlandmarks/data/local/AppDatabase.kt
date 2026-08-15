package com.example.smartlandmarks.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.smartlandmarks.data.local.dao.LandmarkDao
import com.example.smartlandmarks.data.local.dao.PendingCreateDao
import com.example.smartlandmarks.data.local.dao.VisitDao
import com.example.smartlandmarks.data.local.entity.LandmarkEntity
import com.example.smartlandmarks.data.local.entity.PendingCreateEntity
import com.example.smartlandmarks.data.local.entity.VisitEntity

@Database(
    entities = [
        LandmarkEntity::class,
        VisitEntity::class,
        PendingCreateEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun landmarkDao(): LandmarkDao
    abstract fun visitDao(): VisitDao
    abstract fun pendingCreateDao(): PendingCreateDao

    companion object {
        const val NAME = "smart_landmarks.db"
    }
}
