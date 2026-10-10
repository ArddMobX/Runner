package com.runner.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.runner.app.ui.ChatScreen
import com.runner.app.ui.MainViewModel
import com.runner.app.ui.SettingsScreen
import com.runner.app.ui.components.AppDrawerContent
import com.runner.app.ui.components.DisclaimerDialog
import com.runner.app.ui.formatModelName
import com.runner.app.ui.theme.RunnerTheme
import kotlinx.coroutines.launch

private enum class Screen {
    CHAT,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.checkAllPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        maybeRequestInitialPermissions()
        setContent {
            val themeConfig by viewModel.themeConfig.collectAsState()
            RunnerTheme(themeConfig = themeConfig) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }

    private fun maybeRequestInitialPermissions() {
        val permissions = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS)
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CALL_PHONE)
        }
        val prefs = getSharedPreferences("runner_permissions", Context.MODE_PRIVATE)
        val requestedOnce = prefs.getBoolean("initial_permissions_requested", false)
        if (!requestedOnce && permissions.isNotEmpty()) {
            prefs.edit().putBoolean("initial_permissions_requested", true).apply()
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val sessionId = intent?.getStringExtra("SESSION_ID")
        if (!sessionId.isNullOrBlank()) {
            viewModel.openSession(sessionId)
        }
    }

    override fun onResume() {
        super.onResume()
        // Пользователь мог вернуться с системного экрана выдачи прав
        viewModel.checkAllPermissions()
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf(Screen.CHAT) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val sessions by viewModel.sessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val sessionsQuery by viewModel.sessionsQuery.collectAsState()
    val searchSnippets by viewModel.searchSnippets.collectAsState()
    val drawerProvider by viewModel.activeProvider.collectAsState()
    val provider = drawerProvider
    val formattedModel = if (provider != null) formatModelName(provider.activeModel) else ""
    val drawerFooter = if (provider != null && provider.apiKey.isNotBlank()) {
        if (formattedModel.isNotBlank() && formattedModel != "Выбрать модель") {
            "${provider.name} · $formattedModel"
        } else {
            "${provider.name} · модель не выбрана"
        }
    } else {
        "Провайдер не настроен"
    }

    // Системный Back на экране настроек возвращает в чат, а не сворачивает приложение:
    // без обработчика backstack пуст и системный экран считает экран единственным.
    BackHandler(enabled = currentScreen == Screen.SETTINGS) {
        currentScreen = Screen.CHAT
    }

    // Предупреждение о рисках показываем один раз, до всего остального
    val appSettings by viewModel.settings.collectAsState()
    if (!appSettings.disclaimerAccepted) {
        DisclaimerDialog(
            onAccept = {
                viewModel.updateSettings(appSettings.copy(disclaimerAccepted = true))
            }
        )
    }

    // Плавная смена экранов. Только прозрачность: слайд вместе с ящиком
    // и шторками даёт дёргание, а на плотном контенте кроссфейд незаметен
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(150))
        },
        label = "screen_fade"
    ) { screen ->
        when (screen) {
            Screen.CHAT -> ModalNavigationDrawer(
                drawerState = drawerState,
                scrimColor = Color.Black.copy(alpha = 0.72f),
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        drawerTonalElevation = 6.dp,
                    drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                    modifier = Modifier
                        .width(310.dp)
                        .border(
                            BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            ),
                            RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                        )
                ) {
                    AppDrawerContent(
                        sessions = sessions,
                        currentSessionId = currentSessionId,
                        query = sessionsQuery,
                        snippets = searchSnippets,
                        onQueryChange = { viewModel.setSessionsQuery(it) },
                        onNewChat = {
                            viewModel.startNewChat()
                            scope.launch { drawerState.close() }
                        },
                        onOpenSession = { sessionId ->
                            viewModel.openSession(sessionId)
                            scope.launch { drawerState.close() }
                        },
                        onRenameSession = { sessionId, title ->
                            viewModel.renameSession(sessionId, title)
                        },
                        onDeleteSession = { viewModel.deleteSession(it) },
                        onTogglePinSession = { sessionId, pinned ->
                            viewModel.togglePinSession(sessionId, pinned)
                        },
                        appVersion = viewModel.appVersion,
                        footerLabel = drawerFooter,
                        onOpenSettings = {
                            scope.launch { drawerState.close() }
                            currentScreen = Screen.SETTINGS
                        }
                    )
                }
            }
        ) {
            ChatScreen(
                viewModel = viewModel,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onOpenSettings = { currentScreen = Screen.SETTINGS },
                onOpenStorageSettings = { openStorageSettings(context) }
            )
        }

            Screen.SETTINGS -> SettingsScreen(
                viewModel = viewModel,
                onBackClick = { currentScreen = Screen.CHAT },
                onOpenStorageSettings = { openStorageSettings(context) },
                onOpenAppSettings = { openAppSettings(context) }
            )
        }
    }
}

/**
 * Открывает системный экран настроек приложения (разрешения, микрофон, контакты, телефон).
 */
fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

/**
 * Открывает системный экран «Доступ ко всем файлам».
 * Тумблер в приложении право не выдаёт — только этот экран.
 */
fun openStorageSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    } else {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
    }
}
