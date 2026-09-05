package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.DifficultyLevel
import com.example.data.model.HomeworkEntity
import com.example.data.model.SubjectEntity
import com.example.data.repository.HomeworkRepository
import com.example.notification.NotificationScheduler
import com.example.util.AppLanguage
import com.example.util.ParsedVoiceResult
import com.example.util.VoiceHomeworkParser
import com.example.util.VoiceListeningManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class EduPlanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HomeworkRepository

    val allHomeworks: StateFlow<List<HomeworkEntity>>
    val allSubjects: StateFlow<List<SubjectEntity>>

    val selectedDate = MutableStateFlow(LocalDate.now())
    private val prefs = application.getSharedPreferences("eduplan_prefs", android.content.Context.MODE_PRIVATE)

    val themeMode = MutableStateFlow(prefs.getString("theme_mode", "LIGHT") ?: "LIGHT") // LIGHT, DARK, SYSTEM
    val language = MutableStateFlow(AppLanguage.TR) // TR, EN

    val subjectFilter = MutableStateFlow<String?>(null)
    val difficultyFilter = MutableStateFlow<String?>(null)

    // Automatic Notification Settings
    val notificationsEnabled = MutableStateFlow(true)
    val firstReminderHour = MutableStateFlow(18) // e.g. 18:00
    val secondReminderHour = MutableStateFlow(20) // e.g. 20:00
    val projectReminderDays = MutableStateFlow(3) // 3 days before
    val hardHomeworkReminderDays = MutableStateFlow(2) // 2 days before

    // Pro Mod Voice Homework State
    val proModeEnabled = MutableStateFlow(false)
    val proModeAutoOffHours = MutableStateFlow(8) // 0 = continuous, 8 = 8 hours auto off
    val isVoiceListeningActive = MutableStateFlow(false)
    val pendingVoiceHomework = MutableStateFlow<ParsedVoiceResult?>(null)
    val voiceErrorMessage = MutableStateFlow<String?>(null)

    private var voiceListeningManager: VoiceListeningManager? = null

    init {
        val db = AppDatabase.getDatabase(application)
        repository = HomeworkRepository(db.homeworkDao(), db.subjectDao())

        allHomeworks = repository.allHomeworks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allSubjects = repository.allSubjects.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Automatically reschedule notifications whenever homework list or notification settings change
        viewModelScope.launch {
            combine(
                allHomeworks,
                notificationsEnabled,
                firstReminderHour,
                secondReminderHour,
                projectReminderDays,
                hardHomeworkReminderDays
            ) { _ ->
                NotificationScheduler.scheduleHomeworkNotifications(
                    context = getApplication(),
                    homeworkList = allHomeworks.value,
                    notificationsEnabled = notificationsEnabled.value,
                    firstReminderHour = firstReminderHour.value,
                    secondReminderHour = secondReminderHour.value,
                    projectReminderDays = projectReminderDays.value,
                    hardHomeworkReminderDays = hardHomeworkReminderDays.value
                )
            }.collect {}
        }
    }

    // Filtered active (non-expired, non-completed) homeworks for calendar day
    val homeworksForSelectedDate = combine(allHomeworks, selectedDate) { list, date ->
        list.filter { !it.isCompletedEffective && it.dueDate == date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active & upcoming pending homeworks (dueDate >= today and not completed)
    val activeUpcomingHomeworks = combine(allHomeworks, subjectFilter, difficultyFilter) { list, subF, diffF ->
        val today = LocalDate.now()
        list.filter { hw ->
            !hw.isCompletedEffective && !hw.dueDate.isBefore(today) &&
                    (subF == null || hw.subject.equals(subF, ignoreCase = true)) &&
                    (diffF == null || hw.difficulty.equals(diffF, ignoreCase = true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Completed homeworks (including past due homeworks)
    val completedHomeworks = allHomeworks.combine(allHomeworks) { list, _ ->
        list.filter { it.isCompletedEffective }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun addHomework(
        subject: String,
        dueDate: LocalDate,
        difficulty: DifficultyLevel,
        isProject: Boolean,
        notes: String
    ) {
        viewModelScope.launch {
            val entity = HomeworkEntity(
                subject = subject,
                dueDateEpochDay = dueDate.toEpochDay(),
                difficulty = difficulty.name,
                isProject = isProject,
                notes = notes,
                isCompleted = false
            )
            repository.insertHomework(entity)
        }
    }

    fun toggleHomeworkCompletion(homework: HomeworkEntity) {
        viewModelScope.launch {
            val updated = homework.copy(isCompleted = !homework.isCompleted)
            repository.updateHomework(updated)
        }
    }

    fun updateHomework(homework: HomeworkEntity) {
        viewModelScope.launch {
            repository.updateHomework(homework)
        }
    }

    fun deleteHomework(homework: HomeworkEntity) {
        viewModelScope.launch {
            repository.deleteHomework(homework)
        }
    }

    fun addCustomSubject(name: String) {
        viewModelScope.launch {
            repository.addSubject(name)
        }
    }

    fun deleteCustomSubject(subject: SubjectEntity) {
        viewModelScope.launch {
            repository.deleteSubject(subject)
        }
    }

    fun importHomeworks(homeworks: List<HomeworkEntity>) {
        viewModelScope.launch {
            repository.insertHomeworks(homeworks)
        }
    }

    fun setThemeMode(mode: String) {
        themeMode.value = mode
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun setLanguage(lang: AppLanguage) {
        language.value = lang
    }

    fun setSubjectFilter(subject: String?) {
        subjectFilter.value = subject
    }

    fun setDifficultyFilter(difficulty: String?) {
        difficultyFilter.value = difficulty
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        notificationsEnabled.value = enabled
    }

    fun setFirstReminderHour(hour: Int) {
        firstReminderHour.value = hour.coerceIn(0, 23)
    }

    fun setSecondReminderHour(hour: Int) {
        secondReminderHour.value = hour.coerceIn(0, 23)
    }

    fun setProjectReminderDays(days: Int) {
        projectReminderDays.value = days.coerceIn(1, 14)
    }

    fun setHardHomeworkReminderDays(days: Int) {
        hardHomeworkReminderDays.value = days.coerceIn(1, 7)
    }

    fun resetNotificationDefaults() {
        notificationsEnabled.value = true
        firstReminderHour.value = 18
        secondReminderHour.value = 20
        projectReminderDays.value = 3
        hardHomeworkReminderDays.value = 2
    }

    // Pro Mod Voice & Recording Operations
    private val proRecordingManager = com.example.util.ProRecordingManager(application)

    val isProRecordingActive = proRecordingManager.isRecording
    val proRecordingElapsedSeconds = proRecordingManager.elapsedSeconds
    val proRecordingsList = proRecordingManager.recordingsList
    val lastProRecognizedText = proRecordingManager.lastRecognizedText

    fun toggleProMode(enabled: Boolean) {
        proModeEnabled.value = enabled
        if (!enabled) {
            stopProRecording()
        }
    }

    fun setProModeAutoOffHours(hours: Int) {
        proModeAutoOffHours.value = hours
    }

    fun startProRecording(): Boolean {
        return proRecordingManager.startRecording()
    }

    fun stopProRecording() {
        proRecordingManager.stopRecording()
    }

    fun deleteProRecording(fileName: String) {
        proRecordingManager.deleteRecording(fileName)
    }

    fun getProRecordingContent(fileName: String): String {
        return proRecordingManager.getRecordingContent(fileName)
    }

    fun dismissVoiceHomeworkDialog() {
        pendingVoiceHomework.value = null
    }

    fun confirmVoiceHomework(
        subject: String,
        dueDate: LocalDate,
        difficulty: DifficultyLevel,
        isProject: Boolean,
        notes: String
    ) {
        addHomework(subject, dueDate, difficulty, isProject, notes)
        pendingVoiceHomework.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopProRecording()
    }
}
