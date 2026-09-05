package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.HomeworkDao
import com.example.data.dao.SubjectDao
import com.example.data.model.HomeworkEntity
import com.example.data.model.SubjectEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [HomeworkEntity::class, SubjectEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun homeworkDao(): HomeworkDao
    abstract fun subjectDao(): SubjectDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val DEFAULT_SUBJECTS = listOf(
            "Türk Dili ve Edebiyatı",
            "Matematik",
            "Fizik",
            "Kimya",
            "Biyoloji",
            "Tarih",
            "Coğrafya",
            "Din Kültürü ve Ahlak Bilgisi",
            "Felsefe",
            "İngilizce",
            "Beden Eğitimi ve Spor",
            "Görsel Sanatlar / Müzik"
        )

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "eduplan_database"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    val dao = database.subjectDao()
                                    val defaultEntities = DEFAULT_SUBJECTS.map {
                                        SubjectEntity(name = it, isCustom = false)
                                    }
                                    dao.insertSubjects(defaultEntities)
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
