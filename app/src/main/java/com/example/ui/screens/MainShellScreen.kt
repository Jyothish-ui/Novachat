package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatEntity
import com.example.data.UserEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MainTabs {
    CHATS, GROUPS, AI, SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShellScreen(
    viewModel: ChatViewModel,
    onChatSelected: (String) -> Unit
) {
    var activeTab by remember { mutableStateOf(MainTabs.CHATS) }
    val chats by viewModel.chats.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (activeTab) {
                            MainTabs.CHATS -> "Direct Messages"
                            MainTabs.GROUPS -> "Group Channels"
                            MainTabs.AI -> "Gemini AI Space"
                            MainTabs.SETTINGS -> "My Profile"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleDarkMode() }) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Dark Mode",
                            tint = TealAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == MainTabs.CHATS,
                    onClick = { activeTab = MainTabs.CHATS },
                    icon = { Icon(imageVector = if (activeTab == MainTabs.CHATS) Icons.Default.ChatBubble else Icons.Outlined.ChatBubbleOutline, contentDescription = "Chats") },
                    label = { Text("Chats") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TealPrimary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = TealAccent.copy(alpha = 0.2f)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == MainTabs.GROUPS,
                    onClick = { activeTab = MainTabs.GROUPS },
                    icon = { Icon(imageVector = if (activeTab == MainTabs.GROUPS) Icons.Default.Groups else Icons.Outlined.Groups, contentDescription = "Groups") },
                    label = { Text("Groups") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TealPrimary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = TealAccent.copy(alpha = 0.2f)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == MainTabs.AI,
                    onClick = { activeTab = MainTabs.AI },
                    icon = { Icon(imageVector = if (activeTab == MainTabs.AI) Icons.Default.AutoAwesome else Icons.Outlined.AutoAwesome, contentDescription = "AI Assistant") },
                    label = { Text("AI Space") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TealPrimary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = TealAccent.copy(alpha = 0.2f)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == MainTabs.SETTINGS,
                    onClick = { activeTab = MainTabs.SETTINGS },
                    icon = { Icon(imageVector = if (activeTab == MainTabs.SETTINGS) Icons.Default.Settings else Icons.Outlined.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TealPrimary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = TealAccent.copy(alpha = 0.2f)
                    )
                )
            }
        },
        floatingActionButton = {
            if (activeTab == MainTabs.CHATS || activeTab == MainTabs.GROUPS) {
                FloatingActionButton(
                    onClick = {
                        // Quick entry into Gemini Assistant
                        onChatSelected("chat_gemini")
                    },
                    containerColor = TealPrimary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("fab_quick_chat")
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Chat with Gemini")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                MainTabs.CHATS -> ChatsTabScreen(
                    chats = chats.filter { !it.isGroup && it.id != "chat_gemini" },
                    contacts = contacts,
                    searchQuery = searchQuery,
                    onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                    onChatClick = onChatSelected
                )
                MainTabs.GROUPS -> GroupsTabScreen(
                    chats = chats.filter { it.isGroup },
                    contacts = contacts,
                    searchQuery = searchQuery,
                    onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                    onChatClick = onChatSelected
                )
                MainTabs.AI -> AiTabScreen(
                    onEnterAiSpace = { onChatSelected("chat_gemini") }
                )
                MainTabs.SETTINGS -> SettingsTabScreen(
                    currentUser = currentUser,
                    onUpdateName = { viewModel.updateProfile(it) }
                )
            }
        }
    }
}

// --- TAB SCREENS ---

@Composable
fun ChatsTabScreen(
    chats: List<ChatEntity>,
    contacts: List<UserEntity>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onChatClick: (String) -> Unit
) {
    val filteredChats = chats.filter { chat ->
        chat.name.contains(searchQuery, ignoreCase = true) ||
                chat.lastMessageText.contains(searchQuery, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBarField(query = searchQuery, onQueryChange = onSearchQueryChanged, placeholder = "Search chats...")

        if (filteredChats.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.ChatBubbleOutline,
                title = "No Conversations Yet",
                desc = "Search for a contact or tap the AI Assistant button to start chatting."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredChats) { chat ->
                    // Find the contact to determine online status and typing status
                    val contactId = when (chat.id) {
                        "chat_alice" -> "user_alice"
                        "chat_bob" -> "user_bob"
                        "chat_charlie" -> "user_charlie"
                        else -> ""
                    }
                    val contact = contacts.find { it.id == contactId }
                    val isOnline = contact?.isOnline ?: false
                    val typingInChat = contact?.typingChatId == chat.id

                    ChatItemRow(
                        chat = chat,
                        isOnline = isOnline,
                        isTyping = typingInChat,
                        onClick = { onChatClick(chat.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun GroupsTabScreen(
    chats: List<ChatEntity>,
    contacts: List<UserEntity>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onChatClick: (String) -> Unit
) {
    val filteredChats = chats.filter { chat ->
        chat.name.contains(searchQuery, ignoreCase = true) ||
                chat.lastMessageText.contains(searchQuery, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBarField(query = searchQuery, onQueryChange = onSearchQueryChanged, placeholder = "Search group channels...")

        if (filteredChats.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Groups,
                title = "No Groups Found",
                desc = "Create a project or dev channel to coordinate with your team."
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredChats) { chat ->
                    // Check if anyone in the group is typing
                    val typingMembers = contacts.filter { it.typingChatId == chat.id && !it.isMe }
                    val isTyping = typingMembers.isNotEmpty()
                    val typingText = if (isTyping) {
                        "${typingMembers.joinToString { it.name.substringBefore(" ") }} is typing..."
                    } else null

                    ChatItemRow(
                        chat = chat,
                        isOnline = false, // groups don't have a single online state
                        isTyping = isTyping,
                        typingText = typingText,
                        onClick = { onChatClick(chat.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun AiTabScreen(onEnterAiSpace: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(TealAccent.copy(alpha = 0.25f), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(PulsePurple, TealAccent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Gemini Logo",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Gemini AI Assistant",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Enter a direct conversational workspace powered by Gemini 3.5 Flash. Send requests, outline code, compile tables, or upload custom imagery for multimodal analysis instantly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onEnterAiSpace,
            colors = ButtonDefaults.buttonColors(
                containerColor = TealPrimary,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("enter_ai_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(imageVector = Icons.Default.Bolt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Launch AI Space",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun SettingsTabScreen(
    currentUser: UserEntity?,
    onUpdateName: (String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var nameText by remember { mutableStateOf("") }

    LaunchedEffect(currentUser) {
        currentUser?.let { nameText = it.name }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // User Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(TealPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile Icon",
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isEditing) {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_name_input"),
                        trailingIcon = {
                            IconButton(onClick = {
                                if (nameText.trim().isNotEmpty()) {
                                    onUpdateName(nameText.trim())
                                    isEditing = false
                                }
                            }) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = "Save", tint = TealAccent)
                            }
                        }
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = currentUser?.name ?: "You",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { isEditing = true }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Name",
                                tint = TealAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Local Developer Account",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Infrastructure Status Cards
        Text(
            text = "NovaChat Service Status",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        StatusItemRow(
            icon = Icons.Default.Dns,
            title = "Local SQLite Room Engine",
            statusText = "Online",
            statusColor = GreenOnline
        )

        Spacer(modifier = Modifier.height(12.dp))

        StatusItemRow(
            icon = Icons.Default.SettingsCell,
            title = "Voice Recorder & Audio System",
            statusText = "Configured",
            statusColor = GreenOnline
        )

        Spacer(modifier = Modifier.height(12.dp))

        val hasApiKey = com.example.BuildConfig.GEMINI_API_KEY.isNotEmpty() && com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"
        StatusItemRow(
            icon = Icons.Default.AutoAwesome,
            title = "Gemini API Pipeline",
            statusText = if (hasApiKey) "Active Integration" else "Key Required (.env)",
            statusColor = if (hasApiKey) GreenOnline else OrangeWarning
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "NovaChat v1.2.0 • Native Compose Prototype",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// --- SUB-COMPONENTS ---

@Composable
fun ChatItemRow(
    chat: ChatEntity,
    isOnline: Boolean,
    isTyping: Boolean,
    typingText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar Badge
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        if (chat.isGroup) TealPrimary else {
                            when (chat.id) {
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
                    imageVector = if (chat.isGroup) Icons.Default.Groups else Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Online indicator dot
            if (isOnline && !chat.isGroup) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(2.dp)
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

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (chat.lastMessageTime > 0) {
                    Text(
                        text = formatTimestamp(chat.lastMessageTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (isTyping) {
                Text(
                    text = typingText ?: "Typing...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = TealAccent
                    )
                )
            } else {
                Text(
                    text = chat.lastMessageText.ifEmpty { "Tap to open conversational sandbox" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (chat.unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (chat.unreadCount > 0 && !isTyping) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(TealPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chat.unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.White
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TealAccent) },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TealPrimary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun EmptyStateView(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TealAccent.copy(alpha = 0.5f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun StatusItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    statusText: String,
    statusColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = TealAccent, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = statusColor
                )
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
