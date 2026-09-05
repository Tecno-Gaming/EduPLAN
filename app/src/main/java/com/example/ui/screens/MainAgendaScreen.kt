package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HomeworkEntity
import com.example.ui.components.AddEditHomeworkDialog
import com.example.ui.components.AddSubjectDialog
import com.example.ui.components.CalendarView
import com.example.ui.components.HomeworkItemCard
import com.example.ui.theme.EasyGreen
import com.example.ui.viewmodel.EduPlanViewModel
import com.example.util.Strings
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun MainAgendaScreen(
    viewModel: EduPlanViewModel,
    modifier: Modifier = Modifier
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val allHomeworks by viewModel.allHomeworks.collectAsState()
    val homeworksForSelectedDate by viewModel.homeworksForSelectedDate.collectAsState()
    val activeUpcomingHomeworks by viewModel.activeUpcomingHomeworks.collectAsState()
    val completedHomeworks by viewModel.completedHomeworks.collectAsState()
    val subjects by viewModel.allSubjects.collectAsState()
    val language by viewModel.language.collectAsState()

    var showAddHomeworkDialog by remember { mutableStateOf(false) }
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var homeworkToDelete by remember { mutableStateOf<HomeworkEntity?>(null) }

    val today = remember { LocalDate.now() }
    val isSelectedToday = selectedDate == today

    // Stats calculations
    val completedThisWeek = remember(allHomeworks) {
        val weekAgo = today.minusDays(7)
        allHomeworks.count { it.isCompletedEffective && !it.dueDate.isBefore(weekAgo) }
    }
    val pendingTotal = remember(allHomeworks) {
        allHomeworks.count { !it.isCompletedEffective && !it.dueDate.isBefore(today) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 1. Weekly Stats Summary Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = Strings.get("stats_title", language),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = EasyGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${Strings.get("completed_this_week", language)}: $completedThisWeek",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = pendingTotal.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = Strings.get("pending_total", language),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Main Calendar Component
            item {
                CalendarView(
                    selectedDate = selectedDate,
                    onDateSelected = { viewModel.selectDate(it) },
                    allHomeworks = allHomeworks,
                    language = language,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // 3. Selected Day Header & Add Button
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.EventAvailable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSelectedToday) Strings.get("today_homeworks", language)
                            else "${selectedDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))} ${Strings.get("homeworks_suffix", language)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 4. Homework list for selected date
            if (homeworksForSelectedDate.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = Strings.get("no_homeworks_day", language),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(homeworksForSelectedDate, key = { it.id }) { homework ->
                    HomeworkItemCard(
                        homework = homework,
                        onToggleCompletion = { viewModel.toggleHomeworkCompletion(it) },
                        onDelete = { homeworkToDelete = it },
                        onUpdate = { viewModel.updateHomework(it) },
                        language = language
                    )
                }
            }

            // 5. Section for All Upcoming Active Homeworks
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Strings.get("upcoming_homeworks", language),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            val upcomingFiltered = activeUpcomingHomeworks.filter { it.dueDate != selectedDate }
            if (upcomingFiltered.isEmpty()) {
                item {
                    Text(
                        text = Strings.get("no_active_homeworks", language),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 80.dp)
                    )
                }
            } else {
                items(upcomingFiltered, key = { "up_${it.id}" }) { homework ->
                    HomeworkItemCard(
                        homework = homework,
                        onToggleCompletion = { viewModel.toggleHomeworkCompletion(it) },
                        onDelete = { homeworkToDelete = it },
                        onUpdate = { viewModel.updateHomework(it) },
                        language = language
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp)) // Padding for FAB
                }
            }
        }

        // Floating Action Button: Add Homework
        ExtendedFloatingActionButton(
            onClick = { showAddHomeworkDialog = true },
            icon = { Icon(Icons.Outlined.Add, contentDescription = "Add Homework") },
            text = { Text(Strings.get("add_homework", language), fontWeight = FontWeight.Bold) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_homework_fab")
        )
    }

    if (showAddHomeworkDialog) {
        AddEditHomeworkDialog(
            initialDate = selectedDate,
            subjects = subjects,
            language = language,
            onDismiss = { showAddHomeworkDialog = false },
            onSave = { subject, dueDate, difficulty, isProject, notes ->
                viewModel.addHomework(subject, dueDate, difficulty, isProject, notes)
                showAddHomeworkDialog = false
            },
            onOpenAddSubjectDialog = {
                showAddSubjectDialog = true
            }
        )
    }

    if (showAddSubjectDialog) {
        AddSubjectDialog(
            language = language,
            onDismiss = { showAddSubjectDialog = false },
            onAddSubject = { newSub ->
                viewModel.addCustomSubject(newSub)
                showAddSubjectDialog = false
            }
        )
    }

    val targetHomework = homeworkToDelete
    if (targetHomework != null) {
        AlertDialog(
            onDismissRequest = { homeworkToDelete = null },
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(Strings.get("delete_homework_title", language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "'${targetHomework.subject}' - ${Strings.get("confirm_delete", language)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteHomework(targetHomework)
                        homeworkToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_delete_homework_btn")
                ) {
                    Text(Strings.get("yes_delete", language))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { homeworkToDelete = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(Strings.get("no_keep", language))
                }
            }
        )
    }
}

