package com.example.ui.screens

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.data.MessageEntity
import com.example.data.UserEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chatId: String,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onStartCall: (UserEntity, Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val chats by viewModel.chats.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDurationMs by viewModel.recordingDurationMs.collectAsState()
    val amplitudeList by viewModel.amplitudeList.collectAsState()
    val playingMsgId by viewModel.playingMessageId.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()

    val chat = chats.find { it.id == chatId }
    val contactId = when (chatId) {
        "chat_gemini" -> "user_gemini"
        "chat_alice" -> "user_alice"
        "chat_bob" -> "user_bob"
        "chat_charlie" -> "user_charlie"
        else -> ""
    }
    val contact = contacts.find { it.id == contactId }
    val isOnline = contact?.isOnline ?: false
    val typingChatId = contact?.typingChatId
    val isTyping = typingChatId == chatId

    var textInput by remember { mutableStateOf("") }

    // Scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Set active chat context
    DisposableEffect(chatId) {
        viewModel.setSelectedChat(chatId)
        onDispose {
            viewModel.setSelectedChat(null)
            viewModel.stopVoiceMessage()
        }
    }

    // Photo picker configuration
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    val base64Data = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                    viewModel.sendImageMessage(chatId, base64Data)
                }
            } catch (e: Exception) {
                Log.e("ChatDetailScreen", "Failed to resolve chosen photo", e)
                Toast.makeText(context, "Failed to load selected photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Header Avatar
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(
                                        if (chat?.isGroup == true) TealPrimary else {
                                            when (chatId) {
                                                "chat_alice" -> Color(0xFF8B5CF6)
                                                "chat_bob" -> Color(0xFFEF4444)
                                                "chat_charlie" -> Color(0xFF10B981)
                                                else -> TealAccent
                                            }
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (chat?.isGroup == true) Icons.Default.Groups else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            if (isOnline && chat?.isGroup == false) {
                                Box(
                                    modifier = Modifier
                                        .size(11.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(1.5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(GreenOnline)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chat?.name ?: "Conversational Sandbox",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isTyping) {
                                Text(
                                    text = "typing...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = TealAccent
                                    )
                                )
                            } else {
                                Text(
                                    text = if (chat?.isGroup == true) "Group Channel" else if (isOnline) "Active" else "Offline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TealAccent
                        )
                    }
                },
                actions = {
                    if (chat?.isGroup == false && chatId != "chat_gemini") {
                        IconButton(onClick = {
                            contact?.let { onStartCall(it, false) }
                        }) {
                            Icon(imageVector = Icons.Default.Phone, contentDescription = "Audio Call", tint = TealAccent)
                        }
                        IconButton(onClick = {
                            contact?.let { onStartCall(it, true) }
                        }) {
                            Icon(imageVector = Icons.Default.Videocam, contentDescription = "Video Call", tint = TealAccent)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Chat Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(
                        message = message,
                        isPlaying = playingMsgId == message.id,
                        progress = if (playingMsgId == message.id) playbackProgress else 0f,
                        onPlayClick = {
                            message.mediaPath?.let { path ->
                                viewModel.playVoiceMessage(message.id, path)
                            }
                        }
                    )
                }

                if (isTyping) {
                    item {
                        TypingBubbleIndicator()
                    }
                }
            }

            // Input / Media Drawer Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!isRecording) {
                        IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                            Icon(
                                imageVector = Icons.Outlined.Photo,
                                contentDescription = "Share Image",
                                tint = TealAccent
                            )
                        }
                    }

                    // Dynamic Composer: Swaps text field with voice recording wave layout
                    Box(modifier = Modifier.weight(1f)) {
                        if (isRecording) {
                            VoiceRecordWaveLayout(
                                durationMs = recordingDurationMs,
                                amplitudes = amplitudeList,
                                onCancel = { viewModel.cancelRecording() }
                            )
                        } else {
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                placeholder = { Text("Message...") },
                                maxLines = 4,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TealPrimary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("chat_text_input")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Composer Trigger Button: Send Text or Record Voice
                    if (textInput.trim().isNotEmpty() && !isRecording) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(TealPrimary)
                                .clickable {
                                    viewModel.sendTextMessage(chatId, textInput)
                                    textInput = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send text",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        // Voice Recorder Trigger (Tap and hold or simple toggle for simplicity & ergonomics)
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(if (isRecording) RedLiveCall else TealPrimary)
                                .clickable {
                                    if (isRecording) {
                                        viewModel.stopRecording(chatId)
                                    } else {
                                        viewModel.startRecording()
                                    }
                                }
                                .testTag("record_voice_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Record Voice message",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    isPlaying: Boolean,
    progress: Float,
    onPlayClick: () -> Unit
) {
    val isMe = message.senderId == "user_me"
    val align = if (isMe) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
        ) {
            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isMe -> TealPrimary
                        message.senderId == "user_gemini" -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (!isMe) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = TealAccent,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    when (message.type) {
                        "IMAGE" -> {
                            Box(
                                modifier = Modifier
                                    .sizeIn(maxWidth = 240.dp, maxHeight = 180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(model = message.mediaPath),
                                    contentDescription = "Shared Image attachment",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (message.text.isNotEmpty() && message.text != "Shared a photo") {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        "VOICE" -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.width(200.dp)
                            ) {
                                IconButton(onClick = onPlayClick) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                        contentDescription = "Voice player control",
                                        tint = if (isMe) Color.White else TealAccent,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                    LinearProgressIndicator(
                                        progress = progress,
                                        color = if (isMe) Color.White else TealPrimary,
                                        trackColor = (if (isMe) Color.White else TealAccent).copy(alpha = 0.25f),
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Voice message • ${(message.mediaDurationMs ?: 0L) / 1000}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = (if (isMe) Color.White else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        else -> { // TEXT
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Read receipts / Checkmarks row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            if (isMe) {
                Icon(
                    imageVector = if (message.status == "READ") Icons.Default.DoneAll else Icons.Default.Done,
                    contentDescription = message.status,
                    tint = if (message.status == "READ") TealAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun VoiceRecordWaveLayout(
    durationMs: Long,
    amplitudes: List<Float>,
    onCancel: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RedLiveCall.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Discard Recording", tint = RedLiveCall)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = RedLiveCall
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Visual Waveform Simulator
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            amplitudes.forEach { amp ->
                val barHeight = ((amp * 28).dp).coerceAtLeast(3.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight)
                        .clip(CircleShape)
                        .background(RedLiveCall)
                )
            }
            if (amplitudes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(RedLiveCall.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Composable
fun TypingBubbleIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Thinking...",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TealAccent
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .offset(y = dot1Offset.dp)
                        .clip(CircleShape)
                        .background(TealAccent)
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .offset(y = (dot1Offset * 0.7f).dp)
                        .clip(CircleShape)
                        .background(TealAccent)
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .offset(y = (dot1Offset * 0.4f).dp)
                        .clip(CircleShape)
                        .background(TealAccent)
                )
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / (1000 * 60)) % 60
    return String.format("%02d:%02d", min, sec)
}
