package com.github.tvbox.newbox.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.tvbox.newbox.data.local.dao.VodCollectDao
import com.github.tvbox.newbox.data.local.dao.VodRecordDao
import com.github.tvbox.newbox.data.local.entity.VodCollect
import com.github.tvbox.newbox.data.local.entity.VodRecord

@Database(
    entities = [VodRecord::class, VodCollect::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vodRecordDao(): VodRecordDao
    abstract fun vodCollectDao(): VodCollectDao

    companion object {
        const val DATABASE_NAME = "newbox.db"
    }
}
