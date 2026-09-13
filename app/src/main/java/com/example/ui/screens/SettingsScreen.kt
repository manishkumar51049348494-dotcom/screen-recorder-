package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.SettingsManager
import com.example.model.*
import com.example.recording.AudioCaptureHelper
import com.example.ui.components.CallAudioDialog

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    freeStorageBytes: Long,
    totalStorageBytes: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val recordingConfig by settingsManager.recordingConfig.collectAsState()
    val audioConfig by settingsManager.audioConfig.collectAsState()
    val facecamConfig by settingsManager.facecamConfig.collectAsState()
    val controlConfig by settingsManager.controlConfig.collectAsState()
    val themeMode by settingsManager.themeMode.collectAsState()
    val githubRepo by settingsManager.githubRepo.collectAsState()

    var showCallAudioNotice by remember { mutableStateOf(false) }
    var repoInput by remember(githubRepo) { mutableStateOf(githubRepo) }
    var isEditingRepo by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )

        // 1. RECORDING SECTION
        SettingsSectionHeader(title = "Video Recording", icon = Icons.Default.Videocam)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Resolution
                DropdownSetting(
                    label = "Resolution",
                    selected = recordingConfig.resolution.label,
                    options = VideoResolution.values().map { it.label },
                    onSelect = { selectedLabel ->
                        val res = VideoResolution.values().first { it.label == selectedLabel }
                        settingsManager.updateRecordingConfig(recordingConfig.copy(resolution = res))
                    }
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Frame Rate
                DropdownSetting(
                    label = "Frame Rate (FPS)",
                    selected = recordingConfig.fps.label,
                    options = VideoFps.values().map { it.label },
                    onSelect = { selectedLabel ->
                        val fps = VideoFps.values().first { it.label == selectedLabel }
                        settingsManager.updateRecordingConfig(recordingConfig.copy(fps = fps))
                    }
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Bitrate
                DropdownSetting(
                    label = "Video Bitrate",
                    selected = recordingConfig.bitrate.label,
                    options = VideoBitrate.values().map { it.label },
                    onSelect = { selectedLabel ->
                        val bit = VideoBitrate.values().first { it.label == selectedLabel }
                        settingsManager.updateRecordingConfig(recordingConfig.copy(bitrate = bit))
                    }
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Orientation
                DropdownSetting(
                    label = "Orientation",
                    selected = recordingConfig.orientation.label,
                    options = VideoOrientation.values().map { it.label },
                    onSelect = { selectedLabel ->
                        val ori = VideoOrientation.values().first { it.label == selectedLabel }
                        settingsManager.updateRecordingConfig(recordingConfig.copy(orientation = ori))
                    }
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Countdown
                DropdownSetting(
                    label = "Countdown Timer",
                    selected = if (recordingConfig.countdownSeconds == 0) "None" else "${recordingConfig.countdownSeconds} Seconds",
                    options = listOf("None", "3 Seconds", "5 Seconds", "10 Seconds"),
                    onSelect = { selectedLabel ->
                        val sec = when (selectedLabel) {
                            "3 Seconds" -> 3
                            "5 Seconds" -> 5
                            "10 Seconds" -> 10
                            else -> 0
                        }
                        settingsManager.updateRecordingConfig(recordingConfig.copy(countdownSeconds = sec))
                    }
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Auto Stop
                DropdownSetting(
                    label = "Auto-Stop Timer",
                    selected = if (recordingConfig.autoStopMinutes == 0) "Disabled" else "${recordingConfig.autoStopMinutes} Minutes",
                    options = listOf("Disabled", "5 Minutes", "10 Minutes", "30 Minutes", "60 Minutes"),
                    onSelect = { selectedLabel ->
                        val mins = when (selectedLabel) {
                            "5 Minutes" -> 5
                            "10 Minutes" -> 10
                            "30 Minutes" -> 30
                            "60 Minutes" -> 60
                            else -> 0
                        }
                        settingsManager.updateRecordingConfig(recordingConfig.copy(autoStopMinutes = mins))
                    }
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Show Touches Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Touches Indicator", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Display touch circle feedback ripple on screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = recordingConfig.showTouches,
                        onCheckedChange = {
                            settingsManager.updateRecordingConfig(recordingConfig.copy(showTouches = it))
                        }
                    )
                }
            }
        }

        // 2. AUDIO SECTION
        SettingsSectionHeader(title = "Audio Capture", icon = Icons.Default.Mic)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Audio Source
                DropdownSetting(
                    label = "Audio Source",
                    selected = audioConfig.audioSource.label,
                    options = AudioSourceMode.values().map { it.label },
                    onSelect = { selectedLabel ->
                        val src = AudioSourceMode.values().first { it.label == selectedLabel }
                        settingsManager.updateAudioConfig(audioConfig.copy(audioSource = src))
                    }
                )

                Text(
                    text = audioConfig.audioSource.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Audio Quality
                DropdownSetting(
                    label = "Audio Bitrate & Sample Rate",
                    selected = audioConfig.audioQuality.label,
                    options = AudioQuality.values().map { it.label },
                    onSelect = { selectedLabel ->
                        val qual = AudioQuality.values().first { it.label == selectedLabel }
                        settingsManager.updateAudioConfig(audioConfig.copy(audioQuality = qual))
                    }
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Call Audio Compatibility Status
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            Text(
                                text = "Call Audio Capture Policy",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            text = "\"${AudioCaptureHelper.CALL_AUDIO_RESTRICTED_MESSAGE}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(
                            onClick = { showCallAudioNotice = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Learn why Android restricts call audio", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // 3. FACECAM SECTION
        SettingsSectionHeader(title = "Face Camera (Facecam)", icon = Icons.Default.Face)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Front Camera", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Display floating facecam overlay while recording", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = facecamConfig.enabled,
                        onCheckedChange = {
                            settingsManager.updateFacecamConfig(facecamConfig.copy(enabled = it))
                        }
                    )
                }

                if (facecamConfig.enabled) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Shape
                    DropdownSetting(
                        label = "Frame Shape",
                        selected = facecamConfig.shape.label,
                        options = FacecamShape.values().map { it.label },
                        onSelect = { selectedLabel ->
                            val shape = FacecamShape.values().first { it.label == selectedLabel }
                            settingsManager.updateFacecamConfig(facecamConfig.copy(shape = shape))
                        }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Size
                    DropdownSetting(
                        label = "Window Size",
                        selected = facecamConfig.size.label,
                        options = FacecamSize.values().map { it.label },
                        onSelect = { selectedLabel ->
                            val size = FacecamSize.values().first { it.label == selectedLabel }
                            settingsManager.updateFacecamConfig(facecamConfig.copy(size = size))
                        }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Mirror
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mirror Preview", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Flip selfie camera horizontally", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = facecamConfig.mirrorCamera,
                            onCheckedChange = {
                                settingsManager.updateFacecamConfig(facecamConfig.copy(mirrorCamera = it))
                            }
                        )
                    }
                }
            }
        }

        // 4. CONTROLS SECTION
        SettingsSectionHeader(title = "Controls & Overlays", icon = Icons.Default.TouchApp)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Floating Overlay Bar", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Show floating pause/stop bar on screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = controlConfig.floatingControls,
                        onCheckedChange = {
                            settingsManager.updateControlConfig(controlConfig.copy(floatingControls = it))
                        }
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                    Button(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grant System Overlay Permission")
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Persistent Notification Controls", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Display pause, resume, and stop buttons in notification tray", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = controlConfig.notificationControls,
                        onCheckedChange = {
                            settingsManager.updateControlConfig(controlConfig.copy(notificationControls = it))
                        }
                    )
                }
            }
        }

        // 5. STORAGE SECTION
        SettingsSectionHeader(title = "Storage & Files", icon = Icons.Default.Folder)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val freeGb = freeStorageBytes / (1024.0 * 1024.0 * 1024.0)
                val totalGb = totalStorageBytes.coerceAtLeast(1L) / (1024.0 * 1024.0 * 1024.0)
                val usedRatio = ((totalStorageBytes - freeStorageBytes).toFloat() / totalStorageBytes.toFloat()).coerceIn(0f, 1f)

                Text("Device Storage Space", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                LinearProgressIndicator(
                    progress = { usedRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${String.format("%.1f", freeGb)} GB Free", style = MaterialTheme.typography.bodySmall)
                    Text("${String.format("%.1f", totalGb)} GB Total", style = MaterialTheme.typography.bodySmall)
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Text("Saved Directory", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    text = "Movies/Pixelgram and Pictures/Pixelgram",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 6. APPEARANCE SECTION
        SettingsSectionHeader(title = "Appearance", icon = Icons.Default.Palette)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DropdownSetting(
                    label = "Theme",
                    selected = themeMode.label,
                    options = AppThemeMode.values().map { it.label },
                    onSelect = { selectedLabel ->
                        val mode = AppThemeMode.values().first { it.label == selectedLabel }
                        settingsManager.updateThemeMode(mode)
                    }
                )
            }
        }

        // 7. GITHUB & APK DOWNLOAD SECTION
        SettingsSectionHeader(title = "GitHub & APK Releases", icon = Icons.Default.CloudDownload)

        val cleanRepo = githubRepo.trim().removePrefix("https://github.com/").removeSuffix("/")
        val releasesUrl = "https://github.com/$cleanRepo/releases"
        val directApkUrl = "https://github.com/$cleanRepo/releases/latest/download/Pixelgram-latest.apk"
        val actionsUrl = "https://github.com/$cleanRepo/actions"

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Direct APK Download CTA
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(directApkUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.GetApp, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download Latest APK (.apk)", fontWeight = FontWeight.Bold)
                }

                // Secondary GitHub buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releasesUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Releases", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(actionsUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Builds (CI)", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Repo Configuration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GitHub Repo", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    TextButton(
                        onClick = { isEditingRepo = !isEditingRepo },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(if (isEditingRepo) "Done" else "Edit")
                    }
                }

                if (isEditingRepo) {
                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = {
                            repoInput = it
                            settingsManager.updateGithubRepo(it)
                        },
                        label = { Text("username/repository") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "https://github.com/$cleanRepo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Guide
                Text(
                    text = "Push karte hi GitHub Actions APK generate karke Releases me upload kar deta hai, jise aap direct install kar sakte hain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showCallAudioNotice) {
        CallAudioDialog(onDismiss = { showCallAudioNotice = false })
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSetting(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            Surface(
                modifier = Modifier.menuAnchor(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(selected, style = MaterialTheme.typography.labelMedium)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
