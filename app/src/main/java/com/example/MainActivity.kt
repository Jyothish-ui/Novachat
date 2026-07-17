package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkMode by chatViewModel.isDarkMode.collectAsState()
            val currentUser by chatViewModel.currentUser.collectAsState()

            MyApplicationTheme(darkTheme = isDarkMode) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        val navController = rememberNavController()

                        NavHost(
                            navController = navController,
                            startDestination = "splash",
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable("splash") {
                                SplashOnboardingScreen(
                                    onGetStarted = {
                                        if (currentUser != null && currentUser?.name != "You" && currentUser?.name != "Developer") {
                                            navController.navigate("main") {
                                                popUpTo("splash") { inclusive = true }
                                            }
                                        } else {
                                            navController.navigate("login") {
                                                popUpTo("splash") { inclusive = true }
                                            }
                                        }
                                    }
                                )
                            }

                            composable("login") {
                                LoginScreen(
                                    currentName = currentUser?.name ?: "",
                                    onLoginSuccess = { username ->
                                        chatViewModel.updateProfile(username)
                                        navController.navigate("main") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable("main") {
                                MainShellScreen(
                                    viewModel = chatViewModel,
                                    onChatSelected = { chatId ->
                                        navController.navigate("chat/$chatId")
                                    }
                                )
                            }

                            composable("chat/{chatId}") { backStackEntry ->
                                val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                                ChatDetailScreen(
                                    chatId = chatId,
                                    viewModel = chatViewModel,
                                    onBack = { navController.popBackStack() },
                                    onStartCall = { contact, isVideo ->
                                        chatViewModel.startCall(contact, isVideo)
                                    }
                                )
                            }
                        }

                        // Encapsulated Call System Overlay: Displays dynamically over any screen!
                        ActiveCallScreen(viewModel = chatViewModel)
                    }
                }
            }
        }
    }
}
