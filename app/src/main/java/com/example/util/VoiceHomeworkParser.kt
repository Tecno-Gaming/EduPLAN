package com.example.util

import com.example.data.model.DifficultyLevel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.util.Locale

data class ParsedVoiceResult(
    val subject: String,
    val dueDate: LocalDate,
    val difficulty: DifficultyLevel,
    val isProject: Boolean,
    val notes: String,
    val rawText: String
)

object VoiceHomeworkParser {

    private val TurkishMonths = mapOf(
        "ocak" to Month.JANUARY,
        "şubat" to Month.FEBRUARY,
        "mart" to Month.MARCH,
        "nisan" to Month.APRIL,
        "mayıs" to Month.MAY,
        "haziran" to Month.JUNE,
        "temmuz" to Month.JULY,
        "ağustos" to Month.AUGUST,
        "eylül" to Month.SEPTEMBER,
        "ekim" to Month.OCTOBER,
        "kasım" to Month.NOVEMBER,
        "aralık" to Month.DECEMBER
    )

    fun parseSpeech(rawText: String, knownSubjects: List<String>): ParsedVoiceResult {
        val lower = rawText.lowercase(Locale("tr", "TR"))
        val today = LocalDate.now()

        // 1. Extract Subject
        var foundSubject = "Genel Ödev"
        val allSubjectsToSearch = (knownSubjects + listOf(
            "Matematik", "Fizik", "Kimya", "Biyoloji", "Türkçe", "Edebiyat",
            "Tarih", "Coğrafya", "İngilizce", "Almanca", "Müzik", "Resim",
            "Beden Eğitimi", "Din Kültürü", "Felsefe"
        )).distinct()

        for (sub in allSubjectsToSearch) {
            val subLower = sub.lowercase(Locale("tr", "TR"))
            if (lower.contains(subLower)) {
                foundSubject = sub
                break
            }
        }

        // 2. Extract Difficulty
        val difficulty = when {
            lower.contains("zor") || lower.contains("karmaşık") -> DifficultyLevel.HARD
            lower.contains("kolay") || lower.contains("basit") -> DifficultyLevel.EASY
            else -> DifficultyLevel.NORMAL
        }

        // 3. Extract Project status
        val isProject = lower.contains("proje") || lower.contains("projesi")

        // 4. Extract Date
        var dueDate = today
        when {
            lower.contains("yarın") -> dueDate = today.plusDays(1)
            lower.contains("öbür gün") || lower.contains("2 gün sonra") -> dueDate = today.plusDays(2)
            lower.contains("3 gün sonra") -> dueDate = today.plusDays(3)
            lower.contains("haftaya") || lower.contains("gelecek hafta") -> dueDate = today.plusWeeks(1)
            lower.contains("pazartesi") -> dueDate = getNextWeekday(today, DayOfWeek.MONDAY)
            lower.contains("salı") -> dueDate = getNextWeekday(today, DayOfWeek.TUESDAY)
            lower.contains("çarşamba") -> dueDate = getNextWeekday(today, DayOfWeek.WEDNESDAY)
            lower.contains("perşembe") -> dueDate = getNextWeekday(today, DayOfWeek.THURSDAY)
            lower.contains("cuma") -> dueDate = getNextWeekday(today, DayOfWeek.FRIDAY)
            lower.contains("cumartesi") -> dueDate = getNextWeekday(today, DayOfWeek.SATURDAY)
            lower.contains("pazar") -> dueDate = getNextWeekday(today, DayOfWeek.SUNDAY)
            else -> {
                // Check month date pattern e.g. "15 eylül" or "3 kasım"
                for ((monthName, monthEnum) in TurkishMonths) {
                    if (lower.contains(monthName)) {
                        val regex = Regex("(\\d{1,2})\\s+$monthName")
                        val match = regex.find(lower)
                        if (match != null) {
                            val dayNum = match.groupValues[1].toIntOrNull()
                            if (dayNum != null && dayNum in 1..31) {
                                try {
                                    val year = if (monthEnum.value < today.monthValue) today.year + 1 else today.year
                                    dueDate = LocalDate.of(year, monthEnum, dayNum)
                                } catch (e: Exception) {
                                    dueDate = today
                                }
                            }
                        }
                        break
                    }
                }
            }
        }

        // 5. Notes
        val notes = rawText.trim()

        return ParsedVoiceResult(
            subject = foundSubject,
            dueDate = dueDate,
            difficulty = difficulty,
            isProject = isProject,
            notes = notes,
            rawText = rawText
        )
    }

    private fun getNextWeekday(from: LocalDate, targetDay: DayOfWeek): LocalDate {
        var date = from
        do {
            date = date.plusDays(1)
        } while (date.dayOfWeek != targetDay)
        return date
    }
}
