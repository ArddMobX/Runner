package com.runner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.runner.app.ui.ChatScreen
import com.runner.app.ui.MainViewModel
import com.runner.app.ui.SettingsScreen
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.RunnerTheme
import com.runner.app.ui.theme.SurfaceDark

enum class Screen {
    CHAT,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RunnerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SurfaceDark
                ) {
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check storage permission when user returns from system settings
        viewModel.checkStoragePermission()
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf(Screen.CHAT) }

    Crossfade(
        targetState = currentScreen,
        animationSpec = MotionTokens.fluidTween(380),
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            Screen.CHAT -> {
                ChatScreen(
                    viewModel = viewModel,
                    onOpenSettings = { currentScreen = Screen.SETTINGS }
                )
            }
            Screen.SETTINGS -> {
                SettingsScreen(
                    viewModel = viewModel,
                    onBackClick = { currentScreen = Screen.CHAT }
                )
            }
        }
    }
}
