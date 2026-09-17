package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.ChannelConfig
import com.example.security.ConnectionStatus
import com.example.security.NetworkMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretAreaScreen(
  viewModel: CalculatorViewModel,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsState()
  var passwordInput by remember { mutableStateOf("") }
  var isUnlocked by remember(state.isSecretUnlocked) { mutableStateOf(state.isSecretUnlocked) }
  var errorMsg by remember { mutableStateOf("") }

  // Auto-connect to secure channel when unlocked
  LaunchedEffect(isUnlocked) {
    if (isUnlocked) {
      viewModel.connectSecretChannel()
    }
  }

  // Tab state for unlocked area: 0: Profil, 1: Mesajlaşma, 2: Arama, 3: Loglar
  var selectedTab by remember { mutableIntStateOf(1) }

  // Profile local states
  var nicknameInput by remember { mutableStateOf(state.secretNickname) }
  var phoneInput by remember { mutableStateOf(state.secretPhoneNumber) }

  // Chat input state
  var messageInput by remember { mutableStateOf("") }

  // Channel configuration dialog state
  var showChannelDialog by remember { mutableStateOf(false) }
  var channelIdInput by remember { mutableStateOf(state.channelConfig.channelId) }
  var encryptionKeyInput by remember { mutableStateOf(state.channelConfig.encryptionKey) }
  var selectedNetworkMode by remember { mutableStateOf(state.channelConfig.networkMode) }
  var targetHostIpInput by remember { mutableStateOf(state.channelConfig.targetHostIp) }
  var isLocalHostInput by remember { mutableStateOf(state.channelConfig.isLocalHost) }

  // Incoming call dialog
  val incomingCall = state.incomingCall
  if (incomingCall != null) {
    AlertDialog(
      onDismissRequest = { viewModel.rejectIncomingCall() },
      icon = {
        Icon(
          imageVector = if (incomingCall.isVideo) Icons.Default.Videocam else Icons.Default.Call,
          contentDescription = "Gelen Arama",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(36.dp)
        )
      },
      title = { Text("🚨 Gelen Güvenli Arama") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "${incomingCall.callerName} (${incomingCall.callerPhone})",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
          )
          Text(
            text = if (incomingCall.isVideo) "Şifreli görüntülü arama çağrısı alınıyor..." else "Şifreli sesli arama çağrısı alınıyor...",
            fontSize = 14.sp
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { viewModel.acceptIncomingCall() },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) {
          Icon(Icons.Default.Call, contentDescription = "Kabul Et")
          Spacer(modifier = Modifier.width(4.dp))
          Text("Kabul Et")
        }
      },
      dismissButton = {
        Button(
          onClick = { viewModel.rejectIncomingCall() },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Icon(Icons.Default.CallEnd, contentDescription = "Reddet")
          Spacer(modifier = Modifier.width(4.dp))
          Text("Reddet")
        }
      }
    )
  }

  // Channel Settings Dialog
  if (showChannelDialog) {
    AlertDialog(
      onDismissRequest = { showChannelDialog = false },
      title = { Text("Canlı Hat & Şifreleme Ayarları") },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(
            "İki kişinin birbiriyle mesajlaşabilmesi için aynı Kanal Kodu ve Şifreleme Anahtarını kullanması gerekir.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          OutlinedTextField(
            value = channelIdInput,
            onValueChange = { channelIdInput = it },
            label = { Text("Kanal Kodu / Oda Adı") },
            placeholder = { Text("Örn: GIZLI-777") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )

          OutlinedTextField(
            value = encryptionKeyInput,
            onValueChange = { encryptionKeyInput = it },
            label = { Text("Uçtan Uca Şifreleme Anahtarı (E2EE)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Ağ Türü: ${if (selectedNetworkMode == NetworkMode.CLOUD_MQTT) "Bulut Canlı Ağ" else "Yerel Wi-Fi"}")
            Switch(
              checked = selectedNetworkMode == NetworkMode.CLOUD_MQTT,
              onCheckedChange = { isCloud ->
                selectedNetworkMode = if (isCloud) NetworkMode.CLOUD_MQTT else NetworkMode.LOCAL_P2P
              }
            )
          }

          if (selectedNetworkMode == NetworkMode.LOCAL_P2P) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Bu Cihaz Sunucu (Host)")
              Switch(
                checked = isLocalHostInput,
                onCheckedChange = { isLocalHostInput = it }
              )
            }

            if (!isLocalHostInput) {
              OutlinedTextField(
                value = targetHostIpInput,
                onValueChange = { targetHostIpInput = it },
                label = { Text("Hedef Cihaz IP Adresi") },
                placeholder = { Text("Örn: 192.168.1.50") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val updated = ChannelConfig(
              channelId = channelIdInput.ifBlank { "LANU-777" },
              encryptionKey = encryptionKeyInput.ifBlank { "admin2011" },
              networkMode = selectedNetworkMode,
              targetHostIp = targetHostIpInput,
              isLocalHost = isLocalHostInput
            )
            viewModel.updateChannelConfig(updated)
            showChannelDialog = false
            Toast.makeText(context, "Kanal ayarları güncellendi ve bağlanıldı", Toast.LENGTH_SHORT).show()
          }
        ) {
          Text("Kaydet & Bağlan")
        }
      },
      dismissButton = {
        TextButton(onClick = { showChannelDialog = false }) {
          Text("İptal")
        }
      }
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(if (!isUnlocked) "Güvenli Giriş" else "Gizli İletişim Merkezi") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("secret_back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
          }
        },
        actions = {
          if (isUnlocked) {
            IconButton(
              onClick = { showChannelDialog = true }
            ) {
              Icon(Icons.Default.SettingsEthernet, contentDescription = "Kanal Ayarları")
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
          titleContentColor = MaterialTheme.colorScheme.onErrorContainer
        )
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      if (!isUnlocked) {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("secret_login_card"),
          shape = RoundedCornerShape(16.dp)
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = "Güvenlik Kilidi",
              modifier = Modifier.size(48.dp),
              tint = MaterialTheme.colorScheme.error
            )
            Text("Ana Şifre Giriniz (Varsayılan: admin2011)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            OutlinedTextField(
              value = passwordInput,
              onValueChange = { passwordInput = it },
              label = { Text("Şifre") },
              visualTransformation = PasswordVisualTransformation(),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("secret_password_input"),
              shape = RoundedCornerShape(12.dp)
            )
            if (errorMsg.isNotEmpty()) {
              Text(errorMsg, color = MaterialTheme.colorScheme.error)
            }
            Button(
              onClick = {
                val success = viewModel.verifySecretPassword(passwordInput)
                if (success) {
                  isUnlocked = true
                  errorMsg = ""
                } else {
                  errorMsg = "Hatalı şifre!"
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("secret_login_button"),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text("Şifreli Alana Giriş Yap", fontSize = 16.sp)
            }
          }
        }
      } else {
        // Live Network Status Indicator Banner
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { showChannelDialog = true },
          colors = CardDefaults.cardColors(
            containerColor = when (state.connectionStatus) {
              ConnectionStatus.CONNECTED -> Color(0xFFE8F5E9)
              ConnectionStatus.CONNECTING -> Color(0xFFFFF9C4)
              else -> Color(0xFFFFEBEE)
            }
          ),
          shape = RoundedCornerShape(12.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(12.dp)
                  .clip(CircleShape)
                  .background(
                    when (state.connectionStatus) {
                      ConnectionStatus.CONNECTED -> Color(0xFF2E7D32)
                      ConnectionStatus.CONNECTING -> Color(0xFFFBC02D)
                      else -> Color(0xFFC62828)
                    }
                  )
              )
              Column {
                Text(
                  text = state.statusDetail,
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = Color.Black
                )
                Text(
                  text = "Kanal: ${state.channelConfig.channelId} | Uçtan Uca Şifreli (Dokunup Değiştir)",
                  fontSize = 11.sp,
                  color = Color.DarkGray
                )
              }
            }
            IconButton(
              onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Kanal Kodu", state.channelConfig.channelId)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Kanal Kodu Kopyalandı: ${state.channelConfig.channelId}", Toast.LENGTH_SHORT).show()
              }
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = "Kanal Kodunu Kopyala", tint = Color.DarkGray)
            }
          }
        }

        // Active Call Overlay Dialog or Banner
        if (state.isCallActive) {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("active_call_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp)
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Icon(
                imageVector = if (state.callType == CallType.Video) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = if (state.callType == CallType.Video) "Aktif Görüntülü Arama" else "Aktif Sesli Arama",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
              )
              Text(
                text = if (state.callType == CallType.Video) "Şifreli Görüntülü Görüşme Devam Ediyor..." else "Şifreli Sesli Görüşme Devam Ediyor...",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
              )
              Text(
                text = "Kanal: ${state.channelConfig.channelId} (Uçtan Uca Canlı Bağlantı)",
                fontSize = 14.sp
              )
              Button(
                onClick = { viewModel.endCall() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("end_call_button")
              ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Aramayı Sonlandır")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Görüşmeyi Sonlandır")
              }
            }
          }
        }

        // Navigation Tabs for Unlocked Area
        ScrollableTabRow(
          selectedTabIndex = selectedTab,
          edgePadding = 0.dp,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("secret_area_tabs")
        ) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text("Profil") },
            icon = { Icon(Icons.Default.Person, contentDescription = "Profil Sekmesi") },
            modifier = Modifier.testTag("tab_profile")
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text("Mesajlaşma") },
            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Mesajlaşma Sekmesi") },
            modifier = Modifier.testTag("tab_messaging")
          )
          Tab(
            selected = selectedTab == 2,
            onClick = { selectedTab = 2 },
            text = { Text("Arama") },
            icon = { Icon(Icons.Default.Call, contentDescription = "Arama Sekmesi") },
            modifier = Modifier.testTag("tab_call")
          )
          Tab(
            selected = selectedTab == 3,
            onClick = { selectedTab = 3 },
            text = { Text("Denetim Logları") },
            icon = { Icon(Icons.Default.Security, contentDescription = "Denetim Logları Sekmesi") },
            modifier = Modifier.testTag("tab_logs")
          )
        }

        when (selectedTab) {
          0 -> {
            // Profile Setup: 1- Takma Ad, 2- Telefon Numarası
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_card"),
              shape = RoundedCornerShape(16.dp)
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
              ) {
                Text("Gizli Kimlik & Profil Ayarları", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedTextField(
                  value = nicknameInput,
                  onValueChange = { nicknameInput = it },
                  label = { Text("1 - Takma Ad (Nickname)") },
                  leadingIcon = { Icon(Icons.Default.Badge, contentDescription = "Takma Ad İkonu") },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nickname_input"),
                  shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                  value = phoneInput,
                  onValueChange = { phoneInput = it },
                  label = { Text("2 - Telefon Numarası") },
                  leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Telefon Numarası İkonu") },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("phone_input"),
                  shape = RoundedCornerShape(12.dp)
                )
                Button(
                  onClick = {
                    viewModel.updateSecretProfile(nicknameInput, phoneInput)
                    Toast.makeText(context, "Profil kaydedildi", Toast.LENGTH_SHORT).show()
                  },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_profile_button")
                ) {
                  Text("Profili Kaydet")
                }
              }
            }
          }
          1 -> {
            if (state.channelConfig.networkMode == NetworkMode.LOCAL_P2P) {
              ChatInterface(
                viewModel = viewModel,
                modifier = Modifier.weight(1f)
              )
            } else {
              // Secure Live Messaging
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Card(
                  modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("messages_card"),
                  shape = RoundedCornerShape(16.dp)
                ) {
                  Column(modifier = Modifier.fillMaxSize()) {
                    // Channel Header Bar inside Chat
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                        text = "Hat: ${state.channelConfig.channelId} (${if (state.connectionStatus == ConnectionStatus.CONNECTED) "Bağlı" else "Bağlantı Bekleniyor"})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                      )
                      IconButton(
                        onClick = { viewModel.clearChatHistory() },
                        modifier = Modifier.size(28.dp)
                      ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Sohbeti Temizle", modifier = Modifier.size(20.dp))
                      }
                    }

                    LazyColumn(
                      modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("chat_messages_list"),
                      verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                      items(state.secretMessages) { msg ->
                        val isMe = msg.isMe
                        val alignment = if (isMe) Alignment.End else Alignment.Start
                        val bgColor = if (isMe) {
                          MaterialTheme.colorScheme.primaryContainer
                        } else {
                          MaterialTheme.colorScheme.surfaceVariant
                        }
                        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val timeStr = timeFormat.format(Date(msg.timestamp))

                        Column(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalAlignment = alignment
                        ) {
                          Column(
                            modifier = Modifier
                              .widthIn(max = 280.dp)
                              .background(color = bgColor, shape = RoundedCornerShape(14.dp))
                              .padding(12.dp)
                          ) {
                            Row(
                              modifier = Modifier.fillMaxWidth(),
                              horizontalArrangement = Arrangement.SpaceBetween,
                              verticalAlignment = Alignment.CenterVertically
                            ) {
                              Text(
                                text = if (isMe) "Siz (${msg.senderName})" else "${msg.senderName} (${msg.phoneNumber})",
                                fontWeight = FontWeight.Bold,
                                color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                fontSize = 12.sp
                              )
                              Text(
                                text = timeStr,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                              )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                              text = msg.message,
                              fontSize = 15.sp,
                              color = MaterialTheme.colorScheme.onSurface
                            )
                          }
                        }
                      }
                    }
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = { Text("Şifreli canlı mesaj yaz (${state.secretNickname})...") },
                    modifier = Modifier
                      .weight(1f)
                      .testTag("chat_message_input"),
                    shape = RoundedCornerShape(24.dp)
                  )
                  IconButton(
                    onClick = {
                      if (messageInput.isNotBlank()) {
                        viewModel.sendSecretMessage(messageInput)
                        messageInput = ""
                      }
                    },
                    modifier = Modifier
                      .size(48.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primary)
                      .testTag("send_message_button")
                  ) {
                    Icon(
                      Icons.AutoMirrored.Filled.Send,
                      contentDescription = "Mesaj Gönder",
                      tint = MaterialTheme.colorScheme.onPrimary
                    )
                  }
                }
              }
            }
          }
          2 -> {
            // Audio / Video Call
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("call_card"),
              shape = RoundedCornerShape(16.dp)
            ) {
              Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
              ) {
                Text("Şifreli Canlı İletişim Hattı", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                  "Kanal: ${state.channelConfig.channelId} | Kimlik: ${state.secretNickname} (${state.secretPhoneNumber})",
                  fontSize = 13.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  "Arama başlattığınızda aynı kanaldaki diğer cihaza anlık güvenli çağrı sinyali gönderilir.",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.outline
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                  Button(
                    onClick = { viewModel.startCall(CallType.Audio) },
                    modifier = Modifier
                      .weight(1f)
                      .height(50.dp)
                      .testTag("voice_call_button"),
                    shape = RoundedCornerShape(12.dp)
                  ) {
                    Icon(Icons.Default.Call, contentDescription = "Sesli Arama İkonu")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sesli Arama")
                  }
                  Button(
                    onClick = { viewModel.startCall(CallType.Video) },
                    modifier = Modifier
                      .weight(1f)
                      .height(50.dp)
                      .testTag("video_call_button"),
                    shape = RoundedCornerShape(12.dp)
                  ) {
                    Icon(Icons.Default.Videocam, contentDescription = "Görüntülü Arama İkonu")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Görüntülü")
                  }
                }
              }
            }
          }
          3 -> {
            // Audit Logs
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("logs_card"),
              shape = RoundedCornerShape(16.dp)
            ) {
              LazyColumn(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(16.dp)
                  .testTag("audit_logs_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                items(state.secretLogs) { log ->
                  Text(text = log, fontSize = 14.sp)
                }
                if (state.secretLogs.isEmpty()) {
                  item {
                    Text("Henüz kaydedilmiş işlem logu bulunmuyor.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

