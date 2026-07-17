package com.example.ui.viewmodel

import android.app.Application
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatEntity
import com.example.data.ChatRepository
import com.example.data.MessageEntity
import com.example.data.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed class CallState {
    object Idle : CallState()
    data class Ringing(val contact: UserEntity, val isVideo: Boolean) : CallState()
    data class Connected(val contact: UserEntity, val isVideo: Boolean, val durationSeconds: Int) : CallState()
    data class Ended(val contact: UserEntity, val isVideo: Boolean) : CallState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application, viewModelScope)

    // Reactive State Flows from Database
    val chats: StateFlow<List<ChatEntity>> = repository.allChats.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val contacts: StateFlow<List<UserEntity>> = repository.allUsers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Local UI State Flows
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    val currentMessages: StateFlow<List<MessageEntity>> = _selectedChatId
        .flatMapLatest { chatId ->
            if (chatId != null) {
                repository.getMessagesForChat(chatId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Calling State
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    // Recording State
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _amplitudeList = MutableStateFlow<List<Float>>(emptyList())
    val amplitudeList: StateFlow<List<Float>> = _amplitudeList.asStateFlow()

    // Voice Message Playing State
    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f) // 0.0 to 1.0
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    // Search Query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Dark Mode Setting (Persisted locally in memory for simplicity or logic can connect)
    private val _isDarkMode = MutableStateFlow(true) // Default to beautiful Neon Dark!
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // Private variables for audio recorder/player
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var recordingStartTime = 0L
    private var recordingJob: Job? = null

    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    private var callTimerJob: Job? = null

    init {
        viewModelScope.launch {
            repository.initializeDatabase()
            val me = repository.getUserById("user_me")
            _currentUser.value = me
        }
    }

    fun setSelectedChat(chatId: String?) {
        _selectedChatId.value = chatId
        if (chatId != null) {
            viewModelScope.launch {
                repository.markChatAsRead(chatId, "user_me")
            }
        }
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateProfile(name: String) {
        viewModelScope.launch {
            val updatedMe = _currentUser.value?.copy(name = name) ?: return@launch
            repository.chatDao.insertUser(updatedMe)
            _currentUser.value = updatedMe
        }
    }

    // --- Message Sending ---

    fun sendTextMessage(chatId: String, text: String) {
        if (text.trim().isEmpty()) return
        viewModelScope.launch {
            repository.sendMessage(
                chatId = chatId,
                senderId = "user_me",
                senderName = _currentUser.value?.name ?: "You",
                text = text,
                type = "TEXT"
            )
        }
    }

    fun sendImageMessage(chatId: String, base64Data: String) {
        viewModelScope.launch {
            // Save base64 image data to a local cache file for loading later
            val fileName = "img_${UUID.randomUUID()}.jpg"
            val file = File(getApplication<Application>().cacheDir, fileName)
            try {
                val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                file.writeBytes(bytes)
                repository.sendMessage(
                    chatId = chatId,
                    senderId = "user_me",
                    senderName = _currentUser.value?.name ?: "You",
                    text = "Shared a photo",
                    type = "IMAGE",
                    mediaPath = file.absolutePath
                )
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to save selected image", e)
            }
        }
    }

    // --- Voice Message Recording (Real Integration!) ---

    fun startRecording() {
        try {
            val context = getApplication<Application>()
            audioFile = File(context.cacheDir, "rec_${UUID.randomUUID()}.m4a")

            // Initialize MediaRecorder
            mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }

            recordingStartTime = System.currentTimeMillis()
            _isRecording.value = true
            _recordingDurationMs.value = 0L
            _amplitudeList.value = emptyList()

            // Poll recording duration and amplitude
            recordingJob = viewModelScope.launch {
                val amplitudes = mutableListOf<Float>()
                while (_isRecording.value) {
                    val elapsed = System.currentTimeMillis() - recordingStartTime
                    _recordingDurationMs.value = elapsed

                    // Fetch max amplitude to animate the visual waveform!
                    val maxAmp = try {
                        mediaRecorder?.maxAmplitude?.toFloat() ?: 0f
                    } catch (e: Exception) {
                        0f
                    }
                    val normalizedAmp = (maxAmp / 32767f).coerceIn(0f, 1f)
                    amplitudes.add(normalizedAmp)
                    if (amplitudes.size > 40) {
                        amplitudes.removeAt(0)
                    }
                    _amplitudeList.value = amplitudes.toList()

                    delay(100)
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Failed to start MediaRecorder", e)
            _isRecording.value = false
            Toast.makeText(getApplication(), "Microphone configuration failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopRecording(chatId: String) {
        if (!_isRecording.value) return
        _isRecording.value = false
        recordingJob?.cancel()

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "MediaRecorder stop failed", e)
        }
        mediaRecorder = null

        val duration = System.currentTimeMillis() - recordingStartTime
        val file = audioFile
        if (file != null && file.exists() && duration > 1000) {
            viewModelScope.launch {
                repository.sendMessage(
                    chatId = chatId,
                    senderId = "user_me",
                    senderName = _currentUser.value?.name ?: "You",
                    text = "🎤 Voice Message",
                    type = "VOICE",
                    mediaPath = file.absolutePath,
                    mediaDurationMs = duration
                )
            }
        } else {
            Toast.makeText(getApplication(), "Recording too short!", Toast.LENGTH_SHORT).show()
        }
        audioFile = null
    }

    fun cancelRecording() {
        if (!_isRecording.value) return
        _isRecording.value = false
        recordingJob?.cancel()

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Cancel MediaRecorder failed", e)
        }
        mediaRecorder = null

        audioFile?.delete()
        audioFile = null
    }

    // --- Voice Playback (Real Integration!) ---

    fun playVoiceMessage(messageId: String, path: String) {
        // If already playing this, pause it
        if (_playingMessageId.value == messageId) {
            stopVoiceMessage()
            return
        }

        stopVoiceMessage() // Stop any previous playback

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
            }
            _playingMessageId.value = messageId

            playbackJob = viewModelScope.launch {
                val player = mediaPlayer ?: return@launch
                while (player.isPlaying) {
                    val progress = player.currentPosition.toFloat() / player.duration.toFloat()
                    _playbackProgress.value = progress.coerceIn(0f, 1f)
                    delay(50)
                }
                // Done playing
                stopVoiceMessage()
            }

            mediaPlayer?.setOnCompletionListener {
                stopVoiceMessage()
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "MediaPlayer failed", e)
            Toast.makeText(getApplication(), "Failed to play audio", Toast.LENGTH_SHORT).show()
            stopVoiceMessage()
        }
    }

    fun stopVoiceMessage() {
        playbackJob?.cancel()
        _playbackProgress.value = 0f
        _playingMessageId.value = null

        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "MediaPlayer release failed", e)
        }
        mediaPlayer = null
    }

    // --- Calling System (Audio / Video Call Simulator with CameraX Preview) ---

    fun startCall(contact: UserEntity, isVideo: Boolean) {
        if (_callState.value != CallState.Idle) return
        _callState.value = CallState.Ringing(contact, isVideo)

        // Ring for 3 seconds, then connect!
        viewModelScope.launch {
            delay(3000)
            if (_callState.value is CallState.Ringing) {
                _callState.value = CallState.Connected(contact, isVideo, 0)
                startCallTimer(contact, isVideo)
            }
        }
    }

    private fun startCallTimer(contact: UserEntity, isVideo: Boolean) {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            var seconds = 0
            while (_callState.value is CallState.Connected) {
                delay(1000)
                seconds++
                _callState.value = CallState.Connected(contact, isVideo, seconds)
            }
        }
    }

    fun endCall() {
        val current = _callState.value
        if (current is CallState.Connected) {
            _callState.value = CallState.Ended(current.contact, current.isVideo)
            callTimerJob?.cancel()
            viewModelScope.launch {
                // Keep the call ended screen for 1.5 seconds, then dismiss
                delay(1500)
                _callState.value = CallState.Idle
            }
        } else if (current is CallState.Ringing) {
            _callState.value = CallState.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaRecorder?.release()
        mediaPlayer?.release()
        callTimerJob?.cancel()
        recordingJob?.cancel()
        playbackJob?.cancel()
    }
}
