package com.block.goose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.block.goose.ui.screens.ChatScreen
import com.block.goose.ui.screens.HomeScreen
import com.block.goose.ui.screens.SettingsScreen
import com.block.goose.ui.theme.GooseTheme
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            GooseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GooseNavigation()
                }
            }
        }
    }
}

@Composable
fun GooseNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToChat = { sessionId, initialMessage ->
                    val route = if (sessionId != null) {
                        "chat/$sessionId"
                    } else if (initialMessage != null) {
                        val encoded = URLEncoder.encode(initialMessage, "UTF-8")
                        "chat/new?message=$encoded"
                    } else {
                        "chat/new"
                    }
                    navController.navigate(route)
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }
        
        composable(
            route = "chat/{sessionId}?message={message}",
            arguments = listOf(
                navArgument("sessionId") { 
                    type = NavType.StringType 
                },
                navArgument("message") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId")
            val encodedMessage = backStackEntry.arguments?.getString("message")
            val initialMessage = encodedMessage?.let { 
                URLDecoder.decode(it, "UTF-8") 
            }
            
            ChatScreen(
                sessionId = if (sessionId == "new") null else sessionId,
                initialMessage = initialMessage,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("settings") {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
