package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.components.AddSubjectDialog
import com.example.ui.components.ProRecordingsDialog
import com.example.ui.components.QRCodeImportDialog
import com.example.ui.components.QRCodeShareDialog
import com.example.ui.viewmodel.EduPlanViewModel
import com.example.util.AppLanguage
import com.example.util.Strings

@Composable
fun SettingsScreen(
    viewModel: EduPlanViewModel,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val allHomeworks by viewModel.allHomeworks.collectAsState()
    val allSubjects by viewModel.allSubjects.collectAsState()

    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val firstReminderHour by viewModel.firstReminderHour.collectAsState()
    val secondReminderHour by viewModel.secondReminderHour.collectAsState()
    val projectReminderDays by viewModel.projectReminderDays.collectAsState()
    val hardHomeworkReminderDays by viewModel.hardHomeworkReminderDays.collectAsState()

    val proModeEnabled by viewModel.proModeEnabled.collectAsState()
    val proModeAutoOffHours by viewModel.proModeAutoOffHours.collectAsState()
    val isProRecordingActive by viewModel.isProRecordingActive.collectAsState()
    val proRecordingElapsedSeconds by viewModel.proRecordingElapsedSeconds.collectAsState()
    val proRecordingsList by viewModel.proRecordingsList.collectAsState()

    val context = LocalContext.current
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }

    val proPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val recordAudioGranted = permissionsMap[Manifest.permission.RECORD_AUDIO] == true
        if (recordAudioGranted) {
            viewModel.toggleProMode(true)
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else true

            if (!isIgnoringBattery) {
                showBatteryOptimizationDialog = true
            }
        }
    }

    fun requestProModePermissionsAndActivate() {
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (!hasMic) {
            proPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            viewModel.toggleProMode(true)
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else true

            if (!isIgnoringBattery) {
                showBatteryOptimizationDialog = true
            }
        }
    }

    val customSubjects = remember(allSubjects) { allSubjects.filter { it.isCustom } }

    var showShareQRDialog by remember { mutableStateOf(false) }
    var showImportQRDialog by remember { mutableStateOf(false) }
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var showProModeWarningDialog by remember { mutableStateOf(false) }
    var showProRecordingsDialog by remember { mutableStateOf(false) }
    var subjectToDelete by remember { mutableStateOf<com.example.data.model.SubjectEntity?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 0. Pro Mode Card (Butonla Başlatılan Ses Kaydı / Transkripsiyon)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (language == com.example.util.AppLanguage.EN) "Pro Mode" else "Pro Mod",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (language == com.example.util.AppLanguage.EN) "Voice Recording / .md Transcription" else "Ses Kaydı / .md Transkripsiyon",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Switch(
                            checked = proModeEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    showProModeWarningDialog = true
                                } else {
                                    viewModel.toggleProMode(false)
                                }
                            },
                            modifier = Modifier.testTag("pro_mode_toggle_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = Strings.get("pro_mode_desc", language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (proModeEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Recording Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isProRecordingActive) {
                                Button(
                                    onClick = { viewModel.stopProRecording() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("settings_stop_pro_rec_btn")
                                ) {
                                    Icon(Icons.Outlined.MicOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("${Strings.get("stop_pro_recording", language)} (${proRecordingElapsedSeconds}s)", fontSize = 12.sp)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        requestProModePermissionsAndActivate()
                                        viewModel.startProRecording()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("settings_start_pro_rec_btn")
                                ) {
                                    Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(Strings.get("start_pro_recording", language), fontSize = 12.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = { showProRecordingsDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("settings_open_pro_recs_btn")
                            ) {
                                Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${Strings.get("recordings_btn", language)} (${proRecordingsList.size})", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = Strings.get("privacy_battery_note", language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Automatic Notification Settings Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = Strings.get("notifications_title", language),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (language == com.example.util.AppLanguage.EN) "Manage homework reminders" else "Ödev hatırlatmalarını düzenleyin",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { viewModel.setNotificationsEnabled(it) },
                            modifier = Modifier.testTag("notifications_enabled_switch")
                        )
                    }

                    if (notificationsEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(14.dp))

                        // 1. Hatırlatma
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (language == com.example.util.AppLanguage.EN) "1st Reminder" else "1. Hatırlatma",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { viewModel.setFirstReminderHour(firstReminderHour - 1) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(36.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("-", fontWeight = FontWeight.Bold) }
                                Text(
                                    text = "%02d:00".format(firstReminderHour),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(96.dp)
                                )
                                OutlinedButton(
                                    onClick = { viewModel.setFirstReminderHour(firstReminderHour + 1) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(36.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("+", fontWeight = FontWeight.Bold) }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 2. Hatırlatma
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (language == com.example.util.AppLanguage.EN) "2nd Reminder" else "2. Hatırlatma",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { viewModel.setSecondReminderHour(secondReminderHour - 1) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(36.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("-", fontWeight = FontWeight.Bold) }
                                Text(
                                    text = "%02d:00".format(secondReminderHour),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(96.dp)
                                )
                                OutlinedButton(
                                    onClick = { viewModel.setSecondReminderHour(secondReminderHour + 1) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(36.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("+", fontWeight = FontWeight.Bold) }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 3. Proje Bildirimi
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (language == com.example.util.AppLanguage.EN) "Project Reminder" else "Proje Bildirimi",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { viewModel.setProjectReminderDays(projectReminderDays - 1) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(36.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("-", fontWeight = FontWeight.Bold) }
                                Text(
                                    text = if (language == com.example.util.AppLanguage.EN) "$projectReminderDays Days Before" else "$projectReminderDays Gün Önce",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(96.dp)
                                )
                                OutlinedButton(
                                    onClick = { viewModel.setProjectReminderDays(projectReminderDays + 1) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(36.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("+", fontWeight = FontWeight.Bold) }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 4. Zor Ödev Bildirimi
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (language == com.example.util.AppLanguage.EN) "Hard Homework Reminder" else "Zor Ödev Bildirimi",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { viewModel.setHardHomeworkReminderDays(hardHomeworkReminderDays - 1) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(36.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("-", fontWeight = FontWeight.Bold) }
                                Text(
                                    text = if (language == com.example.util.AppLanguage.EN) "$hardHomeworkReminderDays Days Before" else "$hardHomeworkReminderDays Gün Önce",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(96.dp)
                                )
                                OutlinedButton(
                                    onClick = { viewModel.setHardHomeworkReminderDays(hardHomeworkReminderDays + 1) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(36.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("+", fontWeight = FontWeight.Bold) }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Varsayılan Moda Dön Butonu
                        OutlinedButton(
                            onClick = { viewModel.resetNotificationDefaults() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reset_notification_defaults_btn")
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (language == com.example.util.AppLanguage.EN) "Reset to Defaults" else "Varsayılan Moda Dön", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        // 1. Theme Selection Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = Strings.get("theme", language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeOptionButton(
                            label = Strings.get("theme_light", language),
                            isSelected = themeMode == "LIGHT",
                            onClick = { viewModel.setThemeMode("LIGHT") },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionButton(
                            label = Strings.get("theme_dark", language),
                            isSelected = themeMode == "DARK",
                            onClick = { viewModel.setThemeMode("DARK") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Language Selection Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = Strings.get("language", language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeOptionButton(
                            label = Strings.get("lang_tr", language),
                            isSelected = language == AppLanguage.TR,
                            onClick = { viewModel.setLanguage(AppLanguage.TR) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionButton(
                            label = Strings.get("lang_en", language),
                            isSelected = language == AppLanguage.EN,
                            onClick = { viewModel.setLanguage(AppLanguage.EN) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 3. QR Code Agenda Sharing Section (Paylaş / İçe Aktar)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.QrCode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = Strings.get("qr_share_title", language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (language == com.example.util.AppLanguage.EN)
                            "Share homeworks in your agenda as QR codes with others or load homeworks from an existing QR code."
                        else
                            "Ajandanızdaki ödevleri QR kod olarak başkalarıyla paylaşabilir veya var olan QR koddan ödev yükleyebilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showShareQRDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_share_qr_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Strings.get("qr_share_button", language), style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = { showImportQRDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_import_qr_btn")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Strings.get("qr_import_button", language), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // 4. Custom Subject Management Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Book,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = Strings.get("custom_subjects_title", language),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = { showAddSubjectDialog = true },
                            modifier = Modifier.testTag("add_custom_subject_icon_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = "Add Custom Subject",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (customSubjects.isEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = Strings.get("no_custom_subjects", language),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        customSubjects.forEach { sub ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sub.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                IconButton(
                                    onClick = { subjectToDelete = sub },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Automatic Notification System Info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = Strings.get("notifications_title", language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = Strings.get("notification_info", language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showShareQRDialog) {
        QRCodeShareDialog(
            homeworks = allHomeworks,
            language = language,
            onDismiss = { showShareQRDialog = false }
        )
    }

    if (showImportQRDialog) {
        QRCodeImportDialog(
            language = language,
            existingHomeworks = allHomeworks,
            onDismiss = { showImportQRDialog = false },
            onImportPayload = { importedList ->
                viewModel.importHomeworks(importedList)
            }
        )
    }

    if (showProModeWarningDialog) {
        AlertDialog(
            onDismissRequest = { showProModeWarningDialog = false },
            title = { Text(if (language == com.example.util.AppLanguage.EN) "Pro Mode & Battery Permissions Warning" else "Pro Mod & Pil İzinleri Uyarı") },
            text = {
                Text(
                    if (language == com.example.util.AppLanguage.EN)
                        "When Pro Mode is enabled, your device can continuously listen for up to 8 hours and save transcriptions to a timestamped .md file.\n\n" +
                                "🔑 Permissions: Microphone and Notification permissions are requested only when Pro Mode is enabled.\n" +
                                "🔋 Battery Optimization: Battery optimization exemption is recommended for uninterrupted 8-hour background execution.\n\n" +
                                "🔒 Privacy Note: All audio processing is done 100% On-Device and your audio data is never sent externally."
                    else
                        "Pro Mod açıldığında cihazınız 8 saat boyunca kesintisiz dinleme yapabilir ve konuşmaları zaman damgasıyla .md dosyasına kaydeder.\n\n" +
                                "🔑 İzinler: Sadece Pro Mod aktifleştirildiğinde Mikrofon ve Bildirim izinleri istenir.\n" +
                                "🔋 Pil Optimizasyonu: Arka planda 8 saat kesintisiz çalışması için pil optimizasyonu muafiyeti önerilir.\n\n" +
                                "🔒 Gizlilik Notu: Bütün ses işleme %100 Cihaz Üzerinde (On-Device) gerçekleştirilir ve ses verileriniz asla dışarıya gönderilmez."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showProModeWarningDialog = false
                        requestProModePermissionsAndActivate()
                    }
                ) {
                    Text(if (language == com.example.util.AppLanguage.EN) "Understood & Enable" else "Anladım & Etkinleştir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProModeWarningDialog = false }) {
                    Text(if (language == com.example.util.AppLanguage.EN) "Cancel" else "Vazgeç")
                }
            }
        )
    }

    if (showBatteryOptimizationDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryOptimizationDialog = false },
            icon = { Icon(Icons.Outlined.BatteryAlert, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(if (language == com.example.util.AppLanguage.EN) "Background Battery Optimization" else "Arka Plan Pil Optimizasyonu") },
            text = {
                Text(
                    if (language == com.example.util.AppLanguage.EN)
                        "To ensure 8 hours of continuous listening and transcription are not stopped by Android when the screen is off, it is recommended to exempt EduPLAN from battery optimization."
                    else
                        "8 saatlik kesintisiz dinleme ve ekran kapalıyken transkripsiyonun Android tarafından sonlandırılmaması için EduPLAN uygulamasını pil optimizasyonundan muaf tutmanız önerilir."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatteryOptimizationDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                    }
                ) {
                    Text(if (language == com.example.util.AppLanguage.EN) "Go to Settings & Exempt" else "Ayarlara Git & Muaf Tut")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryOptimizationDialog = false }) {
                    Text(if (language == com.example.util.AppLanguage.EN) "Skip for now" else "Şimdilik Atla")
                }
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

    if (showProRecordingsDialog) {
        ProRecordingsDialog(
            recordings = proRecordingsList,
            language = language,
            onGetContent = { fileName -> viewModel.getProRecordingContent(fileName) },
            onDeleteFile = { fileName -> viewModel.deleteProRecording(fileName) },
            onDismiss = { showProRecordingsDialog = false }
        )
    }

    val targetSub = subjectToDelete
    if (targetSub != null) {
        AlertDialog(
            onDismissRequest = { subjectToDelete = null },
            title = { Text(Strings.get("delete_subject_title", language), fontWeight = FontWeight.Bold) },
            text = { Text("'${targetSub.name}' ${Strings.get("delete_subject_confirm_suffix", language)}") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomSubject(targetSub)
                        subjectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_delete_subject_btn")
                ) {
                    Text(Strings.get("yes_delete", language))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { subjectToDelete = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(Strings.get("no", language))
                }
            }
        )
    }
}

@Composable
private fun ThemeOptionButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
