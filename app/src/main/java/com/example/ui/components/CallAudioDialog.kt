package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CallAudioDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.PhoneInTalk,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                "WhatsApp & Call Recording Guide",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF10B981).copy(alpha = 0.5f))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "Speaker (Loudspeaker) ON Rakhein!",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF10B981)
                            )
                            Text(
                                text = "Dono taraf ki awaz (Aapki aur Samne wale ki) record karne ke liye call ko Speaker par daalna zaroori hai.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Text(
                    text = "Aapki aur samne wale ki awaz kaise record hogi:",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "1. Pixelgram me Audio Source ko 'Call Audio (WhatsApp/Insta)' ya 'Microphone' par rakhein.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "2. REC button daba kar screen recording shuru karein.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "3. WhatsApp, Instagram, Messenger ya Normal call lagayein aur call ka SPEAKER (Loudspeaker) ON karein.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "💡 Android System Note: Android OS privacy rules ke kaaran WhatsApp ya Instagram ka internal direct stream band hota hai. Lekin Speaker ON karne se recorder ka Voice Communication engine speaker ki awaz aur aapki awaz dono ko clear capture kar leta hai.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text("Samajh Gaya (Got it)", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}
