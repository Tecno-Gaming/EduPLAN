package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DifficultyLevel
import com.example.data.model.HomeworkEntity
import com.example.ui.theme.EasyGreen
import com.example.ui.theme.EasyGreenLight
import com.example.ui.theme.HardRed
import com.example.ui.theme.HardRedLight
import com.example.ui.theme.NormalYellow
import com.example.ui.theme.NormalYellowLight
import com.example.ui.theme.ProjectPurple
import com.example.ui.theme.ProjectPurpleLight
import com.example.util.AppLanguage
import com.example.util.Strings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeworkItemCard(
    homework: HomeworkEntity,
    onToggleCompletion: (HomeworkEntity) -> Unit,
    onDelete: (HomeworkEntity) -> Unit,
    onUpdate: ((HomeworkEntity) -> Unit)? = null,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    // State for editing mode
    var editedSubject by remember(homework) { mutableStateOf(homework.subject) }
    var editedDueDate by remember(homework) { mutableStateOf(homework.dueDate) }
    var editedDifficulty by remember(homework) { mutableStateOf(homework.difficultyEnum) }
    var editedIsProject by remember(homework) { mutableStateOf(homework.isProject) }
    var editedNotes by remember(homework) { mutableStateOf(homework.notes) }

    var showDatePicker by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val isExpired = homework.isExpired
    val isCompleted = homework.isCompletedEffective

    val (diffColor, diffBgColor, diffTextKey) = when (homework.difficultyEnum) {
        DifficultyLevel.EASY -> Triple(EasyGreen, EasyGreenLight, "easy")
        DifficultyLevel.NORMAL -> Triple(NormalYellow, NormalYellowLight, "normal")
        DifficultyLevel.HARD -> Triple(HardRed, HardRedLight, "hard")
    }

    val daysLeft = ChronoUnit.DAYS.between(today, homework.dueDate)
    val dateFormatted = rememberDateFormatted(homework.dueDate)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
                width = if (isExpanded) 2.dp else 0.dp,
                color = if (isExpanded) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onLongClick = {
                    isExpanded = !isExpanded
                    if (!isExpanded) isEditing = false
                },
                onClick = {
                    if (isExpanded && !isEditing) {
                        isExpanded = false
                        isEditing = false
                    }
                }
            )
            .testTag("homework_item_${homework.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
            else if (isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else if (isExpired) HardRedLight.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 3.dp else 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Main Summary Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox completion toggle
                IconButton(
                    onClick = { onToggleCompletion(homework) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = "Toggle Complete",
                        tint = if (isCompleted) EasyGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Main Details
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Badges row: Difficulty + Project Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Difficulty Badge
                        Surface(
                            color = diffBgColor,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(diffColor)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = Strings.get(diffTextKey, language),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = diffColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Project Special Badge
                        if (homework.isProject) {
                            Surface(
                                color = ProjectPurpleLight,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Star,
                                        contentDescription = "Project",
                                        tint = ProjectPurple,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = Strings.get("project_badge", language),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ProjectPurple,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Expired Badge
                        if (isExpired) {
                            Surface(
                                color = HardRed,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(
                                    text = Strings.get("expired_badge", language),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subject Title
                    Text(
                        text = homework.subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    )

                    // Notes / Description Preview
                    if (homework.notes.isNotEmpty() && !isExpanded) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = homework.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Due Date & Days Left info
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = "Due Date",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = dateFormatted,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!isCompleted && !isExpired) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    daysLeft == 0L -> "• ${Strings.get("due_today", language)}"
                                    daysLeft > 0 -> "• $daysLeft ${Strings.get("days_left", language)}"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (daysLeft <= 1) HardRed else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Action Button in Header (Pencil Icon during Expanded View, Delete Trash Icon when Closed)
                if (isExpanded) {
                    IconButton(
                        onClick = {
                            isEditing = !isEditing
                            if (isEditing) {
                                // Reset editable variables to current homework state
                                editedSubject = homework.subject
                                editedDueDate = homework.dueDate
                                editedDifficulty = homework.difficultyEnum
                                editedIsProject = homework.isProject
                                editedNotes = homework.notes
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("edit_homework_btn_${homework.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit Homework",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    IconButton(
                        onClick = { onDelete(homework) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_homework_btn_${homework.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete Homework",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Expanded Detailed View
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    if (!isEditing) {
                        // READ-ONLY DETAILED VIEW
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Strings.get("detailed_view_title", language),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Surface(
                                onClick = {
                                    isExpanded = false
                                    isEditing = false
                                },
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = Strings.get("tap_to_close", language),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Detailed metadata block
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Subject
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${Strings.get("subject", language)}:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(homework.subject, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Status
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(Strings.get("status", language), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = when {
                                            isCompleted -> Strings.get("completed_status", language)
                                            isExpired -> Strings.get("expired_status", language)
                                            else -> "${Strings.get("active_hw_status", language)} ($daysLeft ${Strings.get("days_left", language)})"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isCompleted -> EasyGreen
                                            isExpired -> HardRed
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Difficulty & Type
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(Strings.get("diff_type", language), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${Strings.get(diffTextKey, language)}${if (homework.isProject) Strings.get("project_suffix", language) else ""}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = diffColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Due date full text
                                val locale = if (language == AppLanguage.TR) Locale("tr", "TR") else Locale.ENGLISH
                                val fullDateStr = homework.dueDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy, EEEE", locale))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${Strings.get("date", language)}:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(fullDateStr, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }

                                if (homework.createdAt > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val createdStr = Instant.ofEpochMilli(homework.createdAt)
                                        .atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(Strings.get("created_at", language), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(createdStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Full Description / Notes
                        Text(
                            text = "${Strings.get("notes", language)}:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (homework.notes.isNotBlank()) homework.notes else Strings.get("no_extra_notes", language),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Guidance hint
                        Text(
                            text = Strings.get("edit_hint", language),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        // EDIT FORM VIEW
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Strings.get("edit_homework", language),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 1. Ders Adı
                        Text(
                            text = Strings.get("subject", language),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = editedSubject,
                            onValueChange = { editedSubject = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_subject_field"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 2. Teslim Tarihi
                        Text(
                            text = Strings.get("date", language),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            onClick = { showDatePicker = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = editedDueDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Outlined.CalendarToday,
                                    contentDescription = Strings.get("date", language),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 3. Zorluk Seviyesi
                        Text(
                            text = Strings.get("difficulty", language),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DifficultyPill(
                                label = Strings.get("easy", language),
                                color = EasyGreen,
                                isSelected = editedDifficulty == DifficultyLevel.EASY,
                                onClick = { editedDifficulty = DifficultyLevel.EASY },
                                modifier = Modifier.weight(1f)
                            )
                            DifficultyPill(
                                label = Strings.get("normal", language),
                                color = NormalYellow,
                                isSelected = editedDifficulty == DifficultyLevel.NORMAL,
                                onClick = { editedDifficulty = DifficultyLevel.NORMAL },
                                modifier = Modifier.weight(1f)
                            )
                            DifficultyPill(
                                label = Strings.get("hard", language),
                                color = HardRed,
                                isSelected = editedDifficulty == DifficultyLevel.HARD,
                                onClick = { editedDifficulty = DifficultyLevel.HARD },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 4. Proje Ödevi mi?
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Star,
                                    contentDescription = null,
                                    tint = ProjectPurple,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Strings.get("is_project", language),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Switch(
                                checked = editedIsProject,
                                onCheckedChange = { editedIsProject = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 5. Notlar
                        Text(
                            text = Strings.get("notes", language),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = editedNotes,
                            onValueChange = { editedNotes = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_notes_field"),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 2,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Save & Cancel Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    // Cancel editing
                                    isEditing = false
                                    editedSubject = homework.subject
                                    editedDueDate = homework.dueDate
                                    editedDifficulty = homework.difficultyEnum
                                    editedIsProject = homework.isProject
                                    editedNotes = homework.notes
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("cancel_edit_homework_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Strings.get("cancel", language))
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    if (editedSubject.isNotBlank()) {
                                        val updatedHomework = homework.copy(
                                            subject = editedSubject.trim(),
                                            dueDateEpochDay = editedDueDate.toEpochDay(),
                                            difficulty = editedDifficulty.name,
                                            isProject = editedIsProject,
                                            notes = editedNotes.trim()
                                        )
                                        onUpdate?.invoke(updatedHomework)
                                        isEditing = false
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("save_edit_homework_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Strings.get("save", language))
                            }
                        }
                    }
                }
            }
        }
    }

    // Date Picker Dialog for Edit mode
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = editedDueDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            editedDueDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Tamam")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("İptal")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun DifficultyPill(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) color else color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else color
            )
        }
    }
}

@Composable
private fun rememberDateFormatted(date: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    return date.format(formatter)
}
