package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class DifficultyLevel(val key: String) {
    EASY("EASY"),
    NORMAL("NORMAL"),
    HARD("HARD");

    companion object {
        fun fromString(value: String): DifficultyLevel {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: NORMAL
        }
    }
}

@Entity(tableName = "homeworks")
data class HomeworkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subject: String,
    val dueDateEpochDay: Long, // LocalDate.toEpochDay()
    val difficulty: String, // EASY, NORMAL, HARD
    val isProject: Boolean = false,
    val notes: String = "",
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val dueDate: LocalDate
        get() = LocalDate.ofEpochDay(dueDateEpochDay)

    val difficultyEnum: DifficultyLevel
        get() = DifficultyLevel.fromString(difficulty)

    val isCompletedEffective: Boolean
        get() = isCompleted || dueDate.isBefore(LocalDate.now())

    val isExpired: Boolean
        get() = false
}

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isCustom: Boolean = false
)
