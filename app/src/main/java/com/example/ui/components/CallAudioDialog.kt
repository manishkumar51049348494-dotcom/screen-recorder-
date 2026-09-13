package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.recording.AudioCaptureHelper

@Composable
fun CallAudioDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                "Call Audio Notice",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "\"${AudioCaptureHelper.CALL_AUDIO_RESTRICTED_MESSAGE}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Text(
                    text = "• Third-party VoIP applications (such as WhatsApp, Instagram, Telegram, and Messenger) and cellular phone calls explicitly designate their audio stream as non-capturable (ALLOW_CAPTURE_BY_NONE / USAGE_VOICE_COMMUNICATION).",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "• Pixelgram strictly adheres to legitimate Android Security Architecture. We never deploy root exploits, accessibility interception, or malicious hidden hooks.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "• When microphone recording is enabled, your own voice commentary will be recorded. If an app explicitly allows playback capture or speakerphone output, it will be captured.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Understood")
            }
        }
    )
}
