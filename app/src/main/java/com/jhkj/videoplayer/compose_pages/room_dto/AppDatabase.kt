package com.jhkj.videoplayer.compose_pages.room_dto

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jhkj.videoplayer.compose_pages.models.ConnInfo

@Database(
    entities = [ConnInfo::class],  // 所有 Entity 类
    version = 1,
    exportSchema = false  // 是否导出 schema 文件（用于版本迁移）
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connInfoDao(): ConnInfoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE users ADD COLUMN age INTEGER DEFAULT 0")
            }
        }

        // 单例模式初始化数据库
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database.db"  // 数据库文件名
                )
//                    .createFromAsset("database/prefilled.db")  // 从 assets 预加载
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            // 数据库首次创建时执行
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}