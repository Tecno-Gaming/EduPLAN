package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.HomeworkEntity
import com.example.data.model.SubjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeworkDao {
    @Query("SELECT * FROM homeworks ORDER BY dueDateEpochDay ASC, createdAt DESC")
    fun getAllHomeworks(): Flow<List<HomeworkEntity>>

    @Query("SELECT * FROM homeworks WHERE id = :id")
    suspend fun getHomeworkById(id: Long): HomeworkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomework(homework: HomeworkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeworks(homeworks: List<HomeworkEntity>)

    @Update
    suspend fun updateHomework(homework: HomeworkEntity)

    @Delete
    suspend fun deleteHomework(homework: HomeworkEntity)

    @Query("DELETE FROM homeworks WHERE id = :id")
    suspend fun deleteHomeworkById(id: Long)

    @Query("DELETE FROM homeworks")
    suspend fun deleteAllHomeworks()
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY isCustom DESC, name ASC")
    fun getAllSubjects(): Flow<List<SubjectEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubjects(subjects: List<SubjectEntity>)

    @Delete
    suspend fun deleteSubject(subject: SubjectEntity)
}
