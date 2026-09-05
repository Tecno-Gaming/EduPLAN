package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.model.DifficultyLevel
import com.example.data.model.HomeworkEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object NotificationScheduler {

    fun scheduleHomeworkNotifications(
        context: Context,
        homeworkList: List<HomeworkEntity>,
        notificationsEnabled: Boolean = true,
        firstReminderHour: Int = 18,
        secondReminderHour: Int = 20,
        projectReminderDays: Int = 3,
        hardHomeworkReminderDays: Int = 2
    ) {
        if (!notificationsEnabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val today = LocalDate.now()

        // Filter active non-expired and non-completed homeworks
        val activeHomeworks = homeworkList.filter { !it.isCompleted && !it.dueDate.isBefore(today) }

        activeHomeworks.forEach { homework ->
            scheduleForSingleHomework(
                context,
                alarmManager,
                homework,
                firstReminderHour,
                secondReminderHour,
                projectReminderDays,
                hardHomeworkReminderDays
            )
        }
    }

    private fun scheduleForSingleHomework(
        context: Context,
        alarmManager: AlarmManager,
        homework: HomeworkEntity,
        firstReminderHour: Int,
        secondReminderHour: Int,
        projectReminderDays: Int,
        hardHomeworkReminderDays: Int
    ) {
        val dueDate = homework.dueDate
        val isHard = homework.difficultyEnum == DifficultyLevel.HARD
        val daysBeforeRegular = if (isHard) hardHomeworkReminderDays.toLong() else 1L

        val regularDate = dueDate.minusDays(daysBeforeRegular)
        val nowMillis = System.currentTimeMillis()

        // Regular 1st notification
        val time1 = LocalDateTime.of(regularDate, LocalTime.of(firstReminderHour.coerceIn(0, 23), 0))
        val millis1 = time1.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (millis1 > nowMillis) {
            val reqCode = (homework.id * 10 + 1).toInt()
            val message = "Ödev Hatırlatıcı: ${homework.subject} ödevinin teslim tarihine ${daysBeforeRegular} gün kaldı!"
            scheduleAlarm(context, alarmManager, reqCode, millis1, "EduPLAN Hatırlatma", message)
        }

        // Regular 2nd notification
        val time2 = LocalDateTime.of(regularDate, LocalTime.of(secondReminderHour.coerceIn(0, 23), 0))
        val millis2 = time2.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (millis2 > nowMillis) {
            val reqCode = (homework.id * 10 + 2).toInt()
            val message = "Akşam Hatırlatması: ${homework.subject} ödevinizi kontrol etmeyi unutmayın!"
            scheduleAlarm(context, alarmManager, reqCode, millis2, "EduPLAN Hatırlatma", message)
        }

        // Special Project notification
        if (homework.isProject) {
            val projectDate = dueDate.minusDays(projectReminderDays.toLong())
            val time1525 = LocalDateTime.of(projectDate, LocalTime.of(15, 25))
            val millis1525 = time1525.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (millis1525 > nowMillis) {
                val reqCode = (homework.id * 10 + 3).toInt()
                val message = "${homework.subject} proje ödevi için malzemelerini hazırlamayı unutma!"
                scheduleAlarm(context, alarmManager, reqCode, millis1525, "EduPLAN Proje Hatırlatması", message)
            }
        }
    }

    private fun scheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        triggerAtMillis: Long,
        title: String,
        message: String
    ) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(NotificationReceiver.EXTRA_TITLE, title)
            putExtra(NotificationReceiver.EXTRA_MESSAGE, message)
            putExtra(NotificationReceiver.EXTRA_NOTIFICATION_ID, requestCode)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
