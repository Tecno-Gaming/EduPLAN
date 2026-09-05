package com.example.ui.components

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.HomeworkEntity
import com.example.util.AppLanguage
import com.example.util.MultiPartQRAccumulator
import com.example.util.QRCodeUtil
import com.example.util.Strings
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, filename: String): Boolean {
    val resolver = context.contentResolver
    val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$filename.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EduPLAN")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val imageUri = resolver.insert(imageCollection, contentValues) ?: return false

    return try {
        resolver.openOutputStream(imageUri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(imageUri, contentValues, null, null)
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun shareBitmapViaIntent(context: Context, bitmap: Bitmap, title: String, language: AppLanguage) {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "eduplan_qr_share.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val contentUri: Uri? = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        if (contentUri != null) {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, Strings.get("share_subject", language))
                putExtra(Intent.EXTRA_TEXT, Strings.get("share_body", language))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, title))
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "${Strings.get("share_error", language)}: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun QRCodeShareDialog(
    homeworks: List<HomeworkEntity>,
    language: AppLanguage,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isSaved by remember { mutableStateOf(false) }

    val chunkPayloads = remember(homeworks) {
        if (homeworks.isEmpty()) emptyList()
        else QRCodeUtil.encodeHomeworksToChunks(homeworks, maxItemsPerChunk = 4)
    }

    var currentChunkIndex by remember { mutableIntStateOf(0) }
    val currentPayload = chunkPayloads.getOrNull(currentChunkIndex) ?: ""

    val qrBitmap: Bitmap? = remember(currentPayload) {
        if (currentPayload.isEmpty()) null
        else try {
            QRCodeUtil.generateQRCodeBitmap(currentPayload, 512, 512)
        } catch (e: Exception) {
            null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Strings.get("qr_share_title", language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Text("✕", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (homeworks.isEmpty()) {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = Strings.get("no_homework_to_share", language),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                            Text(Strings.get("ok", language))
                        }
                    }
                } else {
                    Text(
                        text = "${homeworks.size} ${Strings.get("sharing_homework_count", language)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    if (chunkPayloads.size > 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${Strings.get("multi_qr_series", language)} ${currentChunkIndex + 1} / ${chunkPayloads.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // QR Image Card
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .background(Color.White, shape = RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.size(206.dp)
                            )
                        } else {
                            Text(Strings.get("qr_create_failed", language), color = MaterialTheme.colorScheme.error)
                        }
                    }

                    if (chunkPayloads.size > 1) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { if (currentChunkIndex > 0) { currentChunkIndex--; isSaved = false } },
                                enabled = currentChunkIndex > 0
                            ) {
                                Text(Strings.get("prev_qr", language))
                            }
                            TextButton(
                                onClick = { if (currentChunkIndex < chunkPayloads.size - 1) { currentChunkIndex++; isSaved = false } },
                                enabled = currentChunkIndex < chunkPayloads.size - 1
                            ) {
                                Text(Strings.get("next_qr", language))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Primary Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Direct Share Button
                        Button(
                            onClick = {
                                if (qrBitmap != null) {
                                    shareBitmapViaIntent(context, qrBitmap, Strings.get("qr_share_title", language), language)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_qr_intent_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Strings.get("share", language), fontSize = 13.sp)
                        }

                        // Save to Gallery
                        OutlinedButton(
                            onClick = {
                                if (qrBitmap != null) {
                                    val filename = "EduPLAN_QR_Part_${currentChunkIndex + 1}_${System.currentTimeMillis()}"
                                    val success = saveBitmapToGallery(context, qrBitmap, filename)
                                    if (success) {
                                        Toast.makeText(context, Strings.get("qr_saved_gallery", language), Toast.LENGTH_SHORT).show()
                                        isSaved = true
                                    } else {
                                        Toast.makeText(context, Strings.get("qr_save_failed", language), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_qr_gallery_btn")
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Outlined.CheckCircle else Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isSaved) Strings.get("saved", language) else Strings.get("save_to_gallery", language), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Copy raw text payload option
                    TextButton(
                        onClick = {
                            if (currentPayload.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(currentPayload))
                                Toast.makeText(context, Strings.get("copied", language), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("copy_qr_text_btn")
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(Strings.get("copy_qr_text", language), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun QRCodeImportDialog(
    language: AppLanguage,
    existingHomeworks: List<HomeworkEntity> = emptyList(),
    onDismiss: () -> Unit,
    onImportPayload: (List<HomeworkEntity>) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val accumulator = remember { MultiPartQRAccumulator() }

    var selectedImportMode by remember { mutableIntStateOf(0) } // 0 = Camera, 1 = Gallery, 2 = Text Paste
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        cameraPermissionGranted = isGranted
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var chunkStatusText by remember { mutableStateOf<String?>(null) }
    var previewHomeworks by remember { mutableStateOf<List<HomeworkEntity>?>(null) }
    var pastedText by remember { mutableStateOf("") }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val qrString = QRCodeUtil.readQRCodeFromBitmap(bitmap)
                    if (!qrString.isNullOrBlank()) {
                        errorMessage = null
                        val result = accumulator.processChunk(qrString)
                        if (result.errorMessage != null) {
                            errorMessage = result.errorMessage
                        } else if (!result.isComplete) {
                            chunkStatusText = "Parça ${result.chunkIndex + 1} / ${result.totalChunks} Alındı. Lütfen sonraki QR görselini seçin."
                        } else {
                            previewHomeworks = result.homeworks
                        }
                    } else {
                        errorMessage = "Seçilen görselde geçerli EduPLAN QR kodu bulunamadı."
                    }
                } else {
                    errorMessage = "Görsel okunamadı."
                }
            } catch (e: Exception) {
                errorMessage = "Görsel işlenirken hata oluştu."
            }
        }
    }

    var duplicateItemsCount by remember { mutableIntStateOf(0) }
    var showDuplicateWarningDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedImportMode) {
        if (selectedImportMode == 0 && !cameraPermissionGranted) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Strings.get("qr_import_button", language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Text("✕", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (previewHomeworks == null) {
                    // Mode Selector Tabs (3 options: Camera, Gallery, Text Paste)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Camera Tab
                        Surface(
                            onClick = { selectedImportMode = 0 },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedImportMode == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CameraAlt,
                                    contentDescription = null,
                                    tint = if (selectedImportMode == 0) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = Strings.get("camera", language),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedImportMode == 0) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Gallery Tab
                        Surface(
                            onClick = { selectedImportMode = 1 },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedImportMode == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    tint = if (selectedImportMode == 1) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = Strings.get("gallery", language),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedImportMode == 1) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Text Paste Tab
                        Surface(
                            onClick = { selectedImportMode = 2 },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedImportMode == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    tint = if (selectedImportMode == 2) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = Strings.get("text_tab", language),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedImportMode == 2) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Main Content Area based on state
                if (previewHomeworks != null) {
                    // Preview List View
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "🎉 ${previewHomeworks!!.size} ${Strings.get("hw_decoded_title", language)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = Strings.get("hw_decoded_sub", language),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(modifier = Modifier.height(210.dp)) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(previewHomeworks!!) { item ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = item.subject, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = item.difficulty,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${Strings.get("date", language)}: ${item.dueDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (item.notes.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${Strings.get("notes", language)}: ${item.notes}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { previewHomeworks = null; accumulator.reset(); errorMessage = null; chunkStatusText = null }) {
                                Text(Strings.get("rescan", language))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val itemsToImport = previewHomeworks!!
                                    val duplicates = itemsToImport.count { imp ->
                                        existingHomeworks.any { exist ->
                                            exist.subject.equals(imp.subject, ignoreCase = true) &&
                                                    exist.dueDate == imp.dueDate &&
                                                    exist.notes == imp.notes
                                        }
                                    }
                                    if (duplicates > 0) {
                                        duplicateItemsCount = duplicates
                                        showDuplicateWarningDialog = true
                                    } else {
                                        onImportPayload(itemsToImport)
                                        Toast.makeText(context, "${itemsToImport.size} ${Strings.get("imported_success", language)}", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("confirm_import_preview_btn")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Strings.get("import_to_agenda", language))
                            }
                        }
                    }
                } else if (selectedImportMode == 0) {
                    // Camera Scan Mode
                    if (!cameraPermissionGranted) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = Strings.get("camera_perm_denied", language),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { selectedImportMode = 1 },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(Strings.get("load_gallery_text", language))
                            }
                        }
                    } else {
                        // Live Camera Scanner
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(240.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                CameraScannerPreview(
                                    onQRCodeScanned = { rawText ->
                                        errorMessage = null
                                        val result = accumulator.processChunk(rawText)
                                        if (result.errorMessage != null) {
                                            errorMessage = result.errorMessage
                                        } else if (!result.isComplete) {
                                            chunkStatusText = "${Strings.get("chunk_received_prefix", language)} ${result.chunkIndex + 1} / ${result.totalChunks} ${Strings.get("chunk_received_next_qr", language)}"
                                        } else {
                                            previewHomeworks = result.homeworks
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = Strings.get("align_qr_center", language),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (selectedImportMode == 1) {
                    // Gallery Mode
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = Strings.get("gallery_import_desc", language),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        errorMessage = null
                                        chunkStatusText = null
                                        galleryPickerLauncher.launch("image/*")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("select_qr_gallery_btn")
                                ) {
                                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(Strings.get("select_qr_gallery", language))
                                }
                            }
                        }
                    }
                } else {
                    // Text / Clipboard Paste Mode
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        OutlinedTextField(
                            value = pastedText,
                            onValueChange = { pastedText = it },
                            label = { Text(Strings.get("qr_text_label", language)) },
                            placeholder = { Text(Strings.get("qr_text_placeholder", language)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .testTag("qr_text_input_field"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    val clipText = clipboardManager.getText()?.text
                                    if (!clipText.isNullOrBlank()) {
                                        pastedText = clipText
                                    } else {
                                        Toast.makeText(context, Strings.get("no_text_clipboard", language), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Strings.get("paste_from_clipboard", language), style = MaterialTheme.typography.labelSmall)
                            }

                            Button(
                                onClick = {
                                    if (pastedText.isBlank()) {
                                        errorMessage = Strings.get("paste_qr_first", language)
                                    } else {
                                        errorMessage = null
                                        val result = accumulator.processChunk(pastedText)
                                        if (result.errorMessage != null) {
                                            errorMessage = result.errorMessage
                                        } else if (!result.isComplete) {
                                            chunkStatusText = "${Strings.get("chunk_received_prefix", language)} ${result.chunkIndex + 1} / ${result.totalChunks} ${Strings.get("chunk_received_next_paste", language)}"
                                            pastedText = ""
                                        } else {
                                            previewHomeworks = result.homeworks
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                enabled = pastedText.isNotBlank(),
                                modifier = Modifier.testTag("process_pasted_qr_btn")
                            ) {
                                Text(Strings.get("decode_preview", language))
                            }
                        }
                    }
                }

                // Error / Status Alerts
                if (chunkStatusText != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = chunkStatusText!!,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(Strings.get("cancel", language))
                    }
                }
            }
        }
    }

    // Duplicate Homeworks Warning Dialog
    if (showDuplicateWarningDialog && previewHomeworks != null) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarningDialog = false },
            title = { Text(Strings.get("duplicate_title", language), fontWeight = FontWeight.Bold) },
            text = {
                Text("${Strings.get("duplicate_body_prefix", language)} $duplicateItemsCount ${Strings.get("duplicate_body_suffix", language)}")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val itemsToImport = previewHomeworks!!
                        onImportPayload(itemsToImport)
                        Toast.makeText(context, "${itemsToImport.size} ${Strings.get("imported_success", language)}", Toast.LENGTH_SHORT).show()
                        showDuplicateWarningDialog = false
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(Strings.get("add_all", language))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val nonDuplicates = previewHomeworks!!.filter { imp ->
                            !existingHomeworks.any { exist ->
                                exist.subject.equals(imp.subject, ignoreCase = true) &&
                                        exist.dueDate == imp.dueDate &&
                                        exist.notes == imp.notes
                            }
                        }
                        if (nonDuplicates.isNotEmpty()) {
                            onImportPayload(nonDuplicates)
                            Toast.makeText(context, "${nonDuplicates.size} ${Strings.get("import_non_dup_success", language)}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, Strings.get("all_dup_no_import", language), Toast.LENGTH_SHORT).show()
                        }
                        showDuplicateWarningDialog = false
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(Strings.get("skip_duplicates", language))
                }
            }
        )
    }
}

@Composable
fun CameraScannerPreview(
    onQRCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastScannedTime by remember { mutableStateOf(0L) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val mainExecutor = ContextCompat.getMainExecutor(ctx)
                    imageAnalysis.setAnalyzer(mainExecutor) { imageProxy ->
                        val now = System.currentTimeMillis()
                        if (now - lastScannedTime > 700) {
                            val bitmap = try {
                                imageProxy.toBitmap()
                            } catch (e: Exception) {
                                null
                            }
                            if (bitmap != null) {
                                val qrStr = QRCodeUtil.readQRCodeFromBitmap(bitmap)
                                if (!qrStr.isNullOrBlank()) {
                                    lastScannedTime = now
                                    onQRCodeScanned(qrStr)
                                }
                            }
                        }
                        imageProxy.close()
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier
    )
}
