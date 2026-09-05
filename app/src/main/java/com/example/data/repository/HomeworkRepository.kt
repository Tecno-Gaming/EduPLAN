package com.example.data.repository

import com.example.data.dao.HomeworkDao
import com.example.data.dao.SubjectDao
import com.example.data.model.HomeworkEntity
import com.example.data.model.SubjectEntity
import kotlinx.coroutines.flow.Flow

class HomeworkRepository(
    private val homeworkDao: HomeworkDao,
    private val subjectDao: SubjectDao
) {
    val allHomeworks: Flow<List<HomeworkEntity>> = homeworkDao.getAllHomeworks()
    val allSubjects: Flow<List<SubjectEntity>> = subjectDao.getAllSubjects()

    suspend fun insertHomework(homework: HomeworkEntity): Long {
        return homeworkDao.insertHomework(homework)
    }

    suspend fun insertHomeworks(homeworks: List<HomeworkEntity>) {
        homeworkDao.insertHomeworks(homeworks)
    }

    suspend fun updateHomework(homework: HomeworkEntity) {
        homeworkDao.updateHomework(homework)
    }

    suspend fun deleteHomework(homework: HomeworkEntity) {
        homeworkDao.deleteHomework(homework)
    }

    suspend fun deleteHomeworkById(id: Long) {
        homeworkDao.deleteHomeworkById(id)
    }

    suspend fun addSubject(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1
        return subjectDao.insertSubject(SubjectEntity(name = trimmed, isCustom = true))
    }

    suspend fun deleteSubject(subject: SubjectEntity) {
        subjectDao.deleteSubject(subject)
    }

    suspend fun deleteAllHomeworks() {
        homeworkDao.deleteAllHomeworks()
    }
}
