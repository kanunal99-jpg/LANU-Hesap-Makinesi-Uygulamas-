package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.MessageState
import com.example.security.NearbyConnectionStatus
import com.example.security.PermissionManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * P2P Chat Interface integrated with CommunicationViewModel.
 * Supports manual handshake digit verification, E2E AEAD encryption, and per-message state tracking.
 */
@Composable
fun ChatInterface(
    viewModel: CommunicationViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var hasPermissions by remember { mutableStateOf(PermissionManager.hasAllPermissions(context)) }

    // Launcher for multi-permission runtime requests
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        if (hasPermissions) {
            Toast.makeText(context, "P2P İzinleri Onaylandı!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "P2P için tüm izinler gereklidir.", Toast.LENGTH_LONG).show()
        }
    }

    // Chat text input state
    var textInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Permission status & request card
        if (!hasPermissions) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Yakın Cihaz İletişimi (P2P) İzinleri Eksik",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "İki cihaz arasında doğrudan, internetsiz bağlantı kurmak için bluetooth ve yakındaki cihazlar tarama izinleri verilmelidir.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(
                        onClick = {
                            permissionLauncher.launch(PermissionManager.getRequiredPermissions().toTypedArray())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("İzinleri Yetkilendir")
                    }
                }
            }
        } else {
            // 2. Real-time P2P status bar with icon
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (state.p2pStatus) {
                        NearbyConnectionStatus.CONNECTED -> Color(0xFFE8F5E9)
                        NearbyConnectionStatus.CONNECTING -> Color(0xFFFFF9C4)
                        NearbyConnectionStatus.ADVERTISING, NearbyConnectionStatus.DISCOVERING -> Color(0xFFE3F2FD)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Connection State Status Icon
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    when (state.p2pStatus) {
                                        NearbyConnectionStatus.CONNECTED -> Color(0xFF2E7D32) // Green
                                        NearbyConnectionStatus.CONNECTING -> Color(0xFFFBC02D) // Yellow
                                        NearbyConnectionStatus.ADVERTISING, NearbyConnectionStatus.DISCOVERING -> Color(0xFF1976D2) // Blue
                                        NearbyConnectionStatus.ERROR -> Color(0xFFD32F2F) // Red
                                        else -> Color(0xFF757575) // Gray
                                    }
                                )
                                .testTag("p2p_status_indicator")
                        )

                        Column {
                            Text(
                                text = "P2P Durumu: ${state.p2pStatusDetail}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (state.connectedDevices.isNotEmpty()) {
                                Text(
                                    text = "Bağlı Cihaz: ${state.connectedDevices.firstOrNull()?.endpointName}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Reset / Disconnect Action Icon
                    if (state.p2pStatus != NearbyConnectionStatus.DISCONNECTED) {
                        IconButton(
                            onClick = {
                                viewModel.stopP2P()
                                Toast.makeText(context, "P2P Bağlantısı sıfırlandı", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sıfırla", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // 3. Handshake Digit Verification Request Dialog
            val pendingReq = state.pendingP2PRequest
            if (pendingReq != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.rejectP2PConnection(pendingReq.endpointId) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Handshake Verification",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = { Text("🔒 Güvenli Bağlantı Doğrulama") },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${pendingReq.endpointName} bağlantı isteği gönderiyor.",
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Her iki ekrandaki güvenlik kodunun eşleştiğini doğrulayın:",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                            // Strict authentic handshake verification digits
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = pendingReq.authCode,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.acceptP2PConnection(pendingReq.endpointId) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("Kodu Doğrula & Bağlan")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { viewModel.rejectP2PConnection(pendingReq.endpointId) }
                        ) {
                            Text("Reddet", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }

            // 4. Controller Actions Panel (Start Advertising / Discovery)
            if (state.p2pStatus == NearbyConnectionStatus.DISCONNECTED || state.p2pStatus == NearbyConnectionStatus.ERROR) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "P2P Canlı Bağlantıyı Başlat",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Bir cihazın Yayın Başlatması, diğerinin ise Cihaz Ara (Tarama) başlatması gerekir.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.startP2PAdvertising() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Yayın Başlat", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { viewModel.startP2PDiscovery() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cihaz Ara", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 5. Discovery Results (if scanning)
            if (state.p2pStatus == NearbyConnectionStatus.DISCOVERING) {
                if (state.discoveredDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Yakındaki yayınlar aranıyor...",
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 32.dp)
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Bulunan Cihazlar (Bağlanmak için dokunun):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            state.discoveredDevices.forEach { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable {
                                            viewModel.connectToP2PDevice(device.endpointId)
                                        }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Text(text = device.endpointName, fontWeight = FontWeight.Medium)
                                    }
                                    Text(
                                        text = "Bağlan",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Messages List Box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .testTag("p2p_chat_messages_list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.messages) { msg ->
                            val isMe = msg.isMe
                            val alignment = if (isMe) Alignment.End else Alignment.Start
                            val bgColor = if (isMe) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalAlignment = alignment
                            ) {
                                Text(
                                    text = if (isMe) "Siz (${msg.phoneNumber})" else "${msg.senderName} (${msg.phoneNumber})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 12.dp,
                                                topEnd = 12.dp,
                                                bottomStart = if (isMe) 12.dp else 0.dp,
                                                bottomEnd = if (isMe) 0.dp else 12.dp
                                            )
                                        )
                                        .background(bgColor)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = msg.message,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        // Status & Time Row
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                                            Text(
                                                text = sdf.format(Date(msg.timestamp)),
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                            
                                            // Message State Indicator Icon
                                            if (isMe) {
                                                val msgState = state.messageStates[msg.id] ?: MessageState.SENT
                                                val stateSymbol = when (msgState) {
                                                    MessageState.PENDING -> "⏱️"
                                                    MessageState.SENDING -> "⏳"
                                                    MessageState.SENT -> "✓"
                                                    MessageState.DELIVERED -> "✓✓"
                                                    MessageState.FAILED -> "❌"
                                                }
                                                val stateColor = when (msgState) {
                                                    MessageState.FAILED -> MaterialTheme.colorScheme.error
                                                    MessageState.DELIVERED -> Color(0xFF2E7D32)
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                                Text(
                                                    text = stateSymbol,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = stateColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 7. Input and Disconnect Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Güvenli mesaj yazın...", fontSize = 14.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("p2p_message_input"),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )

                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            viewModel.sendMessage(textInput)
                            textInput = ""
                        }
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("p2p_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Mesaj Gönder",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
