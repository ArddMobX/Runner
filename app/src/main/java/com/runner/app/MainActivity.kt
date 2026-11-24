package com.runner.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.util.Log
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.runner.app.ui.LiveAgentManager
import com.runner.app.ui.LiveFloatingOverlay
import com.runner.app.ui.MainViewModel
import com.runner.app.ui.OnboardingWizard
import com.runner.app.util.CrashReportManager
import com.runner.app.ui.SettingsScreen
import com.runner.app.ui.components.AppDrawerContent
import com.runner.app.ui.components.DisclaimerDialog
import com.runner.app.ui.components.SupportSheet
import com.runner.app.ui.formatModelName
import com.runner.app.ui.theme.ApplyAccessibilitySetting
import com.runner.app.ui.theme.ApplyMotionSetting
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.RunnerTheme
import com.runner.app.ui.theme.closeRespectingMotion
import com.runner.app.ui.theme.openRespectingMotion
import kotlinx.coroutines.launch

private enum class Screen {
    CHAT,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var showingCrashDialog: Boolean = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (!showingCrashDialog) {
            viewModel.checkAllPermissions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReportManager.init(applicationContext)

        // Если прошлый запуск упал, показываем стек нативным диалогом,
        // а не запускаем Compose и не трогаем ленивую MainViewModel: иначе
        // приложение повторно упадёт в onStart/onResume при сбое в init ViewModel.
        val pendingCrash = CrashReportManager.getPendingCrashReport(this)
        if (pendingCrash != null) {
            showingCrashDialog = true
            showCrashReportDialog(pendingCrash)
            return
        }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val prefs = getSharedPreferences("runner_permissions", Context.MODE_PRIVATE)
        val requestedOnce = prefs.getBoolean("initial_permissions_requested", false)
        if (!requestedOnce && permissions.isNotEmpty()) {
            prefs.edit().putBoolean("initial_permissions_requested", true).apply()
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    override fun onStart() {
        super.onStart()
        if (showingCrashDialog) return
        viewModel.setAppInForeground(true)
    }

    override fun onStop() {
        super.onStop()
        if (showingCrashDialog) return
        viewModel.setAppInForeground(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (showingCrashDialog) return
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
        if (showingCrashDialog) return
        // Пользователь мог вернуться с системного экрана выдачи прав
        viewModel.checkAllPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Не гасим плавающий оверлей Live-режима при повороте экрана или
        // выгрузке Activity системой, пока Live-задача ещё выполняется.
        if (isFinishing && !isChangingConfigurations && !LiveAgentManager.isExecuting) {
            LiveFloatingOverlay.dismiss()
        }
    }

    /**
     * Нативный диалог с прошлым стеком. Специально без Compose и без
     * кастомных шрифтов: если падение связано с ними, экран всё равно
     * должен открыться и дать скопировать отчёт.
     */
    private fun showCrashReportDialog(report: String) {
        val text = TextView(this).apply {
            text = report
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(32, 24, 32, 24)
        }
        val scroll = ScrollView(this).apply { addView(text) }
        var finished = false

        val dialog = AlertDialog.Builder(this)
            .setTitle("Runner упал в прошлый раз")
            .setView(scroll)
            .setPositiveButton("Скопировать") { _, _ ->
                finished = true
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Runner crash", report))
                CrashReportManager.clearPendingCrashReport(this)
                recreate()
            }
            .setNeutralButton("Поделиться") { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                startActivity(Intent.createChooser(send, "Отправить отчёт"))
            }
            .setNegativeButton("Продолжить") { _, _ ->
                finished = true
                CrashReportManager.clearPendingCrashReport(this)
                recreate()
            }
            .setOnCancelListener {
                finished = true
                CrashReportManager.clearPendingCrashReport(this)
                recreate()
            }
            .create()

        // «Поделиться» закрывает диалог, но приложение не должно остаться
        // на пустом экране: после отправки перезапускаем уже обычный UI.
        dialog.setOnDismissListener {
            if (!finished) {
                CrashReportManager.clearPendingCrashReport(this)
                recreate()
            }
        }
        dialog.show()
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf(Screen.CHAT) }
    var showSupportSheet by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val sessions by viewModel.sessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val sessionsQuery by viewModel.sessionsQuery.collectAsState()
    val searchSnippets by viewModel.searchSnippets.collectAsState()
    val drawerProvider by viewModel.activeProvider.collectAsState()
    val providers by viewModel.providers.collectAsState()
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
    ApplyMotionSetting(appSettings.motionEnabled)
    ApplyAccessibilitySetting(appSettings.accessibilityEnhanced)
    if (!appSettings.disclaimerAccepted) {
        DisclaimerDialog(
            onAccept = {
                viewModel.updateSettings(appSettings.copy(disclaimerAccepted = true))
            }
        )
    }

    // Экран не гаснет во время выполнения задачи агентом
    val isRunning by viewModel.isRunning.collectAsState()
    val keepScreenOn = appSettings.keepScreenOnWhileRunning
    DisposableEffect(isRunning, keepScreenOn) {
        val window = (context as? Activity)?.window
        if (isRunning && keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Скрытие в недавних задачах и блокировка скриншотов (FLAG_SECURE)
    val secureScreen = appSettings.secureScreenMode
    DisposableEffect(secureScreen) {
        val window = (context as? Activity)?.window
        if (secureScreen) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Мастер первого запуска: показываем только когда нет ни одного ключа.
    val hasConfiguredProvider = providers.any { it.apiKey.isNotBlank() }
    var onboardingVisible by remember { mutableStateOf(false) }
    LaunchedEffect(
        appSettings.disclaimerAccepted,
        appSettings.onboardingCompleted,
        hasConfiguredProvider
    ) {
        if (appSettings.onboardingCompleted) {
            onboardingVisible = false
        } else if (appSettings.disclaimerAccepted && !hasConfiguredProvider) {
            onboardingVisible = true
        }
    }
    if (onboardingVisible) {
        OnboardingWizard(
            viewModel = viewModel,
            onDone = {
                onboardingVisible = false
                viewModel.updateAppSettings { copy(onboardingCompleted = true) }
                if (providers.none { it.apiKey.isNotBlank() }) {
                    Toast.makeText(
                        context,
                        "Ключ можно добавить позже в настройках",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
        return
    }

    // Переход экранов по Material Shared Axis X на тягучей пружине.
    // При выключенных анимациях (или системном флаге) срабатывает мгновенно.
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            MotionTokens.sharedAxisXTransition(forward = targetState == Screen.SETTINGS)
        },
        label = "screen_shared_axis"
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
                            if (!isRunning) {
                                LiveAgentManager.reset()
                            }
                            viewModel.startNewChat()
                            scope.launch { drawerState.closeRespectingMotion() }
                        },
                        onOpenSession = { sessionId ->
                            if (!isRunning) {
                                LiveAgentManager.reset()
                            }
                            viewModel.openSession(sessionId)
                            scope.launch { drawerState.closeRespectingMotion() }
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
                        onOpenSupport = {
                            scope.launch { drawerState.closeRespectingMotion() }
                            showSupportSheet = true
                        },
                        onOpenSettings = {
                            scope.launch { drawerState.closeRespectingMotion() }
                            currentScreen = Screen.SETTINGS
                        }
                    )
                }
            }
        ) {
            ChatScreen(
                viewModel = viewModel,
                onOpenDrawer = { scope.launch { drawerState.openRespectingMotion() } },
                onOpenSettings = { currentScreen = Screen.SETTINGS },
                onOpenStorageSettings = { openStorageSettings(context) }
            )
        }

            Screen.SETTINGS -> SettingsScreen(
                viewModel = viewModel,
                onBackClick = { currentScreen = Screen.CHAT },
                onOpenStorageSettings = { openStorageSettings(context) },
                onOpenAppSettings = { openAppSettings(context) },
                onOpenSupport = { showSupportSheet = true }
            )
        }
    }

    if (showSupportSheet) {
        SupportSheet(onDismiss = { showSupportSheet = false })
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
    } catch (e: Exception) {
        // Кнопка «открыть настройки» ничего не делает — это стоит видеть в логе.
        Log.w("RunnerSettings", "не удалось открыть системные настройки", e)
    }
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
