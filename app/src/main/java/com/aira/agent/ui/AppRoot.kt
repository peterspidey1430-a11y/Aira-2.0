package com.aira.agent.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aira.agent.ui.screens.ChatScreen
import com.aira.agent.ui.screens.HomeScreen
import com.aira.agent.ui.screens.MemoryScreen
import com.aira.agent.ui.screens.PermissionsScreen
import com.aira.agent.ui.screens.SettingsScreen
import com.aira.agent.ui.screens.ApiKeyScreen
import com.aira.agent.ui.theme.AiraTheme

object Routes {
    const val HOME = "home"
    const val CHAT = "chat"
    const val MEMORY = "memory"
    const val PERMISSIONS = "permissions"
    const val API_KEY = "apikey"
    const val SETTINGS = "settings"
}

@Composable
fun AppRoot() {
    AiraTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val nav = rememberNavController()
            NavHost(navController = nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) { HomeScreen(nav) }
                composable(Routes.CHAT) { ChatScreen(nav) }
                composable(Routes.MEMORY) { MemoryScreen(nav) }
                composable(Routes.PERMISSIONS) { PermissionsScreen(nav) }
                composable(Routes.API_KEY) { ApiKeyScreen(nav) }
                composable(Routes.SETTINGS) { SettingsScreen(nav) }
            }
        }
    }
}