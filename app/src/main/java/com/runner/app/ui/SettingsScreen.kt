package com.runner.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.data.AppSettings
import com.runner.app.data.AppThemeMode
import com.runner.app.data.ColorSource
import com.runner.app.data.ConnectionTestResult
import com.runner.app.data.Provider
import com.runner.app.data.ThemeConfig
import com.runner.app.tools.ToolDispatcher
import com.runner.app.tools.TrashStore
import com.runner.app.ui.components.ProviderLogos
import com.runner.app.ui.components.RunnerIcons
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.ui.theme.StatusWarning
import com.runner.app.ui.theme.bounceClick
import com.runner.app.util.PluralUtils
import java.net.URI
import java.util.Locale
import kotlin.math.roundToInt

private sealed interface SettingsRoute {
    data object Root : SettingsRoute
    data object Providers : SettingsRoute
    data class ProviderEdit(val providerId: String) : SettingsRoute
    data object Agent : SettingsRoute
    data object Access : SettingsRoute

    /** Журнал обратимых операций: удаления и перемещения. */
    data object Trash : SettingsRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    onOpenAppSettings: () -> Unit = {}
) {
    var route by remember { mutableStateOf<SettingsRoute>(SettingsRoute.Root) }
    BackHandler(enabled = route != SettingsRoute.Root) { route = SettingsRoute.Root }

    val title = when (route) {
        SettingsRoute.Root -> "Настройки"
        SettingsRoute.Providers -> "Провайдеры"
        is SettingsRoute.ProviderEdit -> "Провайдер"
        SettingsRoute.Agent -> "Агент"
        SettingsRoute.Access -> "Доступы"
        SettingsRoute.Trash -> "Корзина"
    }

    val goBack: () -> Unit = {
        if (route == SettingsRoute.Root) {
            onBackClick()
        } else {
            route = SettingsRoute.Root
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = goBack,
                        modifier = Modifier.bounceClick { goBack() }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .imePadding()
        ) {
            when (val current = route) {
                SettingsRoute.Root -> SettingsRoot(viewModel) { route = it }

                SettingsRoute.Providers -> ProvidersList(viewModel) { route = SettingsRoute.ProviderEdit(it) }

                is SettingsRoute.ProviderEdit -> ProviderEditScreen(
                    viewModel = viewModel,
                    providerId = current.providerId,
                    onDeleted = { route = SettingsRoute.Providers }
                )

                SettingsRoute.Agent -> AgentSettings(viewModel) { route = it }

                SettingsRoute.Access -> AccessSettings(viewModel, onOpenStorageSettings, onOpenAppSettings)

                SettingsRoute.Trash -> TrashListScreen(viewModel)
            }
        }
    }
}

@Composable
private fun SettingsRoot(
    viewModel: MainViewModel,
    onNavigate: (SettingsRoute) -> Unit
) {
    val providers by viewModel.providers.collectAsState()
    val activeProvider by viewModel.activeProvider.collectAsState()
    val appSettings by viewModel.settings.collectAsState()
    val hasStorage by viewModel.hasStoragePermission.collectAsState()
    val hasContacts by viewModel.hasContactsPermission.collectAsState()
    val hasCall by viewModel.hasCallPermission.collectAsState()
    val hasAudio by viewModel.hasAudioPermission.collectAsState()
    val allPermissionsGranted = hasStorage && hasContacts && hasCall && hasAudio
    val connectionState by viewModel.connectionTestState.collectAsState()
    val themeConfig by viewModel.themeConfig.collectAsState()
    val context = LocalContext.current

    val proxyUrl = appSettings.reverseProxyUrl
    val isValidProxy = remember(proxyUrl) { isValidHttpUrl(proxyUrl) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsGroup("Провайдеры") {
            val keysCount = providers.count { it.apiKey.isNotBlank() }
            SettingsRow(
                label = "Провайдеры и ключи",
                value = "Ключей: $keysCount · Активен: ${activeProvider?.name ?: "не выбран"}",
                onClick = { onNavigate(SettingsRoute.Providers) },
                icon = Icons.Outlined.VpnKey
            )
            SettingsDivider()
            SettingsRow(
                label = "Модель",
                // Показываем человеческое имя, технический ID живёт в списке выбора
                value = formatModelName(activeProvider?.activeModel.orEmpty()),
                onClick = { onNavigate(SettingsRoute.Providers) },
                icon = Icons.Outlined.SmartToy
            )
        }

        SettingsGroup("Агент") {
            SettingsRow(
                label = "Доп. инструкции",
                value = appSettings.userInstructions.trim().takeIf { it.isNotEmpty() }?.let { "${it.length} символов" } ?: "Не заданы",
                onClick = { onNavigate(SettingsRoute.Agent) },
                icon = Icons.Outlined.Terminal
            )
            SettingsDivider()
            SettingsRow(
                label = "Температура и шаги",
                value = "${appSettings.temperature} · ${PluralUtils.steps(appSettings.maxSteps)}",
                onClick = { onNavigate(SettingsRoute.Agent) },
                icon = Icons.Outlined.Tune
            )
        }

        SettingsGroup("Доступы") {
            SettingsRow(
                label = "Разрешения и доступы",
                value = if (allPermissionsGranted) "все выданы" else "настроить",
                valueColor = if (allPermissionsGranted) StatusSuccess else StatusWarning,
                onClick = { onNavigate(SettingsRoute.Access) },
                icon = Icons.Outlined.FolderOpen
            )
        }

        SettingsGroup("Тема") {
            ThemeSettingsContent(
                themeConfig = themeConfig,
                onModeChange = { viewModel.setThemeMode(it) },
                onSourceChange = { viewModel.setColorSource(it) },
                onSeedColorChange = { viewModel.setCustomSeedColor(it) },
                onAmoledChange = { viewModel.setAmoled(it) }
            )
        }

        SettingsGroup("Интерфейс") {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Масштаб текста",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(10.dp))
                SegmentedChips(
                    options = AppSettings.TEXT_SCALES.map { scale ->
                        scale to "${(scale * 100).roundToInt()}%"
                    },
                    selected = appSettings.textScale,
                    onSelect = { viewModel.updateSettings(appSettings.copy(textScale = it)) }
                )
            }
            SettingsDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Детали вызовов инструментов",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Показывать аргументы и вывод тулов",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = appSettings.showToolDetails,
                    onCheckedChange = {
                        viewModel.updateSettings(appSettings.copy(showToolDetails = it))
                    },
                    colors = runnerSwitchColors()
                )
            }

            SettingsDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Время генерации и статистика",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Тайминги, токены и скорость ответа",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = appSettings.showStats,
                    onCheckedChange = {
                        viewModel.updateSettings(appSettings.copy(showStats = it))
                    },
                    colors = runnerSwitchColors()
                )
            }

            SettingsDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Планирование шагов",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Модель сначала составляет план, исполнение - после подтверждения",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = appSettings.planningEnabled,
                    onCheckedChange = {
                        viewModel.updateSettings(appSettings.copy(planningEnabled = it))
                    },
                    colors = runnerSwitchColors()
                )
            }
        }

        SettingsGroup("Сеть и прокси") {
            // 1. Поле Reverse Proxy
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Reverse proxy",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicTextField(
                    value = proxyUrl,
                    onValueChange = {
                        viewModel.updateSettings(appSettings.copy(reverseProxyUrl = it))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            RoundedCornerShape(10.dp)
                        ),
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (proxyUrl.isEmpty()) {
                                    Text(
                                        text = "https://my-proxy.workers.dev/v1",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp
                                    )
                                }
                                innerTextField()
                            }

                            // Точка статуса валидности адреса
                            if (proxyUrl.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isValidProxy) StatusSuccess else MaterialTheme.colorScheme.error
                                        )
                                )
                            }

                            IconButton(
                                onClick = {
                                    val text = readClipboard(context)
                                    if (text.isNotBlank()) {
                                        viewModel.updateSettings(
                                            appSettings.copy(reverseProxyUrl = text.trim())
                                        )
                                    }
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentPaste,
                                    contentDescription = "Вставить из буфера",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when {
                        proxyUrl.isBlank() -> "Если задан, запросы идут через него вместо прямого Base URL."
                        isValidProxy -> "Адрес корректен."
                        else -> "Нужен полный адрес вида https://host/path"
                    },
                    color = if (proxyUrl.isNotBlank() && !isValidProxy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                    fontSize = 11.5.sp
                )
            }

            SettingsDivider()

            // 2. Проверка соединения: статус и триггер — единый виджет.
            // Тап по плашке запускает проверку заново, отдельная кнопка не нужна.
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Проверка связи",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (proxyUrl.isNotBlank()) "Тест пинга до ${activeProvider?.name ?: "API"} через reverse proxy" else "Тест пинга до ${activeProvider?.name ?: "API"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                ConnectionStatusWidget(
                    isTesting = connectionState.isTesting,
                    result = connectionState.result,
                    onCheck = { viewModel.testConnection() }
                )
            }

            SettingsDivider()

            // 3a. Таймаут подключения
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Таймаут подключения",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${appSettings.connectTimeoutSeconds} с",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Время на установку сетевого соединения с сервером",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                SegmentedChips(
                    options = AppSettings.CONNECT_TIMEOUT_PRESETS.map { sec -> sec to "${sec}с" },
                    selected = appSettings.connectTimeoutSeconds,
                    onSelect = { viewModel.updateSettings(appSettings.copy(connectTimeoutSeconds = it)) }
                )
            }

            SettingsDivider()

            // 3b. Таймаут ответа
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Таймаут ответа",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${appSettings.responseTimeoutSeconds} с",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ожидание ответа и генерации токенов моделью",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                SegmentedChips(
                    options = AppSettings.RESPONSE_TIMEOUT_PRESETS.map { sec -> sec to "${sec}с" },
                    selected = appSettings.responseTimeoutSeconds,
                    onSelect = { viewModel.updateSettings(appSettings.copy(responseTimeoutSeconds = it)) }
                )
            }

            SettingsDivider()

            // Бюджет контекста: сколько истории реально уходит в запрос.
            // Старые реплики отбрасываются целиком, вместе с ответами инструментов.
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Бюджет контекста",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Сколько истории диалога уходит в запрос. Старые шаги отбрасываются, " +
                            "хвост и системный промпт сохраняются.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                SegmentedChips(
                    options = AppSettings.CONTEXT_BUDGET_PRESETS.map { budget ->
                        budget to when {
                            budget >= 1_000_000 -> "${budget / 1_000_000}M"
                            else -> "${budget / 1000}K"
                        }
                    },
                    selected = appSettings.contextBudgetChars,
                    onSelect = { viewModel.updateSettings(appSettings.copy(contextBudgetChars = it)) }
                )
            }

            SettingsDivider()

            // 4. Кастомные заголовки (Custom Headers)
            val customHeaders = appSettings.customHeaders
            var showSecrets by remember { mutableStateOf(false) }

            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Кастомные заголовки (Headers)",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Cloudflare Access, X-Api-Key, кастомный auth",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showSecrets = !showSecrets },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = if (showSecrets) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showSecrets) "Скрыть секреты" else "Показать секреты",
                                tint = if (showSecrets) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                val text = readClipboard(context)
                                if (text.isNotBlank()) {
                                    val current = if (customHeaders.isBlank()) text.trim() else "${customHeaders.trim()}\n${text.trim()}"
                                    viewModel.updateSettings(appSettings.copy(customHeaders = current))
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentPaste,
                                contentDescription = "Вставить заголовки из буфера",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                BasicTextField(
                    value = customHeaders,
                    onValueChange = {
                        viewModel.updateSettings(appSettings.copy(customHeaders = it))
                    },
                    visualTransformation = if (showSecrets) VisualTransformation.None else remember { SecretHeadersVisualTransformation() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp),
                    textStyle = TextStyle(
                        // Свой текст ярче подсказки: это содержимое, а не подпись
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    ),
                    minLines = 3,
                    maxLines = 6,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (customHeaders.isEmpty()) {
                                Text(
                                    text = "CF-Access-Client-Id: xxx\nCF-Access-Client-Secret: yyy\nX-Custom-Auth: zzz",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Формат: Header: Value (по одной строке) или JSON объект.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ProvidersList(
    viewModel: MainViewModel,
    onEdit: (String) -> Unit
) {
    val providers by viewModel.providers.collectAsState()
    val activeProviderId by viewModel.activeProviderId.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SettingsGroup {
            providers.forEachIndexed { index, provider ->
                if (index > 0) SettingsDivider()
                val providerLogo = remember(provider.id, provider.name) {
                    ProviderLogos.forProvider(provider.id, provider.name)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(provider.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (provider.id == activeProviderId) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (provider.id == activeProviderId) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                ),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = providerLogo ?: Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = if (provider.id == activeProviderId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = provider.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatModelName(provider.activeModel),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (provider.id == activeProviderId) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(StatusSuccess.copy(alpha = 0.12f))
                                .border(BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.25f)), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Активен",
                                color = StatusSuccess,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (provider.apiKey.isBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Не настроен",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // Кнопка в стиле карточек списка: та же ширина, фон и рамка.
        // Голая строка без фона терялась в пустоте под списком.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(
                    BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    ),
                    RoundedCornerShape(12.dp)
                )
                .clickable {
                    val id = "custom_${System.currentTimeMillis()}"
                    viewModel.saveProvider(
                        Provider(id = id, name = "Новый провайдер", baseUrl = "")
                    )
                    onEdit(id)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Добавить провайдера",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AgentSettings(viewModel: MainViewModel, onNavigate: (SettingsRoute) -> Unit) {
    val appSettings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SettingsGroup {
            UserInstructionsField(
                instructions = appSettings.userInstructions,
                onInstructionsChange = { viewModel.updateSettings(appSettings.copy(userInstructions = it)) }
            )

            SettingsDivider()

            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Температура",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingSlider(
                    value = appSettings.temperature,
                    onValueChange = {
                        // Округляем до десятых: 0.1 в float даёт хвост вида 0.30000000000000004
                        viewModel.updateSettings(
                            appSettings.copy(temperature = (it * 10).roundToInt() / 10f)
                        )
                    },
                    valueRange = AppSettings.TEMPERATURE_RANGE,
                    steps = 14,
                    valueLabel = { String.format(Locale.US, "%.1f", it) },
                    endDecimals = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ниже - предсказуемее, выше - креативнее.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp
                )
            }

            SettingsDivider()

            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Лимит шагов",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingSlider(
                    value = appSettings.maxSteps.toFloat(),
                    onValueChange = {
                        viewModel.updateSettings(appSettings.copy(maxSteps = it.roundToInt()))
                    },
                    valueRange = AppSettings.STEPS_RANGE.first.toFloat()..
                            AppSettings.STEPS_RANGE.last.toFloat(),
                    // 12 значений = 11 интервалов, в терминах слайдера это steps = 10
                    steps = AppSettings.STEPS_RANGE.last - AppSettings.STEPS_RANGE.first - 1,
                    valueLabel = { PluralUtils.steps(it.roundToInt()) },
                    endDecimals = 0
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Сколько раз модель может вызвать инструменты в одной задаче (хватает на scan → unpack → sort).",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp
                )
            }

            SettingsDivider()

            // Главный переключатель подтверждений. Выключение снимает диалог
            // для всех опасных действий, поэтому рядом предупреждение, а не
            // тихий тумблер.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Спрашивать перед опасными действиями",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (appSettings.confirmDangerous) {
                            "Удаление, запись, shell и запуск ждут подтверждения"
                        } else {
                            "Не спрашивает вообще: всё выполняется сразу"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = appSettings.confirmDangerous,
                    onCheckedChange = {
                        viewModel.updateSettings(appSettings.copy(confirmDangerous = it))
                    },
                    colors = runnerSwitchColors()
                )
            }
            if (!appSettings.confirmDangerous) {
                Surface(
                    color = StatusWarning.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(11.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = StatusWarning,
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(9.dp))
                        Text(
                            text = "Агент будет удалять и перезаписывать файлы, выполнять команды " +
                                    "оболочки и запускать приложения без вопросов. Удаление и " +
                                    "перемещение уходят в журнал и их можно вернуть, а перезапись, " +
                                    "копирование и команды отменить нечем.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            SettingsDivider()

            // Режим «только чтение»: жёсткая блокировка вместо подтверждений.
            // Нужен, когда агент работает с недоверенными файлами или на модели,
            // которая обучается на промптах, — тогда менять данные нельзя вообще.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Только чтение",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (appSettings.readOnlyMode) {
                            "Агент только читает и анализирует: действия запрещены"
                        } else {
                            "Выключено: агент может менять файлы"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = appSettings.readOnlyMode,
                    onCheckedChange = {
                        viewModel.updateSettings(appSettings.copy(readOnlyMode = it))
                    },
                    colors = runnerSwitchColors()
                )
            }
            if (appSettings.readOnlyMode) {
                Text(
                    text = "Удаление, запись, перемещение, архивы, shell, буфер и запуск " +
                            "приложений будут отклоняться без диалога. Чтение, поиск и " +
                            "аналитика работают как обычно.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                )
            }

            SettingsDivider()

            // Сухой прогон: сначала показать, что будет сделано, и только потом
            // делать. Стоит второго прогона, поэтому включается осознанно.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Сначала сухой прогон",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (appSettings.dryRunFirst) {
                            "Рискованные задачи агент сначала только описывает"
                        } else {
                            "Выключено: агент выполняет задачу сразу"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = appSettings.dryRunFirst,
                    onCheckedChange = {
                        viewModel.updateSettings(appSettings.copy(dryRunFirst = it))
                    },
                    colors = runnerSwitchColors()
                )
            }
            if (appSettings.dryRunFirst) {
                Text(
                    text = "Агент прочитает и посчитает, но ничего не изменит, а затем " +
                            "предложит выполнить задачу. Касается только задач, меняющих " +
                            "данные: прогон удваивает расход запросов к провайдеру.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                )
            }

            SettingsDivider()

            // Экономия лимита токенов. Схемы всех инструментов уходят в каждом
            // запросе, поэтому набор можно сузить до нужного.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Объявлять только нужные инструменты",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (appSettings.pruneTools) {
                            "Набор сужается по плану, остальное подключается по ходу"
                        } else {
                            "Выключено: в каждом запросе уходят все схемы"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = appSettings.pruneTools,
                    onCheckedChange = {
                        viewModel.updateSettings(appSettings.copy(pruneTools = it))
                    },
                    colors = runnerSwitchColors()
                )
            }
            if (appSettings.pruneTools) {
                Text(
                    text = "Схемы инструментов занимают заметную часть запроса, поэтому " +
                            "их список сужается до чтения и того, что назвал утверждённый " +
                            "план. Если понадобится что-то ещё, агент подключит это сам. " +
                            "Без плана список не сужается — иначе инструмента может " +
                            "не оказаться в нужный момент.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                )
            }

            SettingsDivider()

            // Авто-озвучка ответов через системный Text-to-Speech
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Озвучивать ответы голосом (TTS)",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (appSettings.autoSpeakResponses) {
                            "Агент автоматически читает ответы вслух"
                        } else {
                            "Выключено: озвучка только по кнопке у сообщения"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = appSettings.autoSpeakResponses,
                    onCheckedChange = {
                        viewModel.updateSettings(appSettings.copy(autoSpeakResponses = it))
                    },
                    colors = runnerSwitchColors()
                )
            }

            // Список запомненных разрешений: пользователь должен видеть, что уже
            // выполняется молча, и уметь это забыть.
            if (appSettings.approvedOperationSignatures.isNotEmpty()) {
                SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Запомненные разрешения",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                        Text(
                            text = PluralUtils.actions(appSettings.approvedOperationSignatures.size) +
                                    " выполняется без подтверждения",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp
                        )
                    }
                    TextButton(
                        onClick = {
                            viewModel.updateSettings(
                                appSettings.copy(approvedOperationSignatures = emptySet())
                            )
                        }
                    ) {
                        Text("Забыть все", fontSize = 13.sp)
                    }
                }
            }

            SettingsDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Спрашивать каждый шаг",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (appSettings.confirmEveryStep) {
                            "Спрашивать каждый шаг"
                        } else {
                            "Только опасные действия"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = appSettings.confirmEveryStep,
                    onCheckedChange = {
                        viewModel.updateSettings(appSettings.copy(confirmEveryStep = it))
                    },
                    colors = runnerSwitchColors()
                )
            }
            if (appSettings.confirmEveryStep) {
                Text(
                    text = "Безопасные чтение и поиск тоже будут ждать подтверждения.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                )
            }

            SettingsDivider()

            // Файловые инструменты заперты в /storage/emulated/0, а sh -c эту
            // песочницу обходит. По умолчанию обход выключен: команды вроде
            // df -h и ls /sdcard работают, а /data, /system и /proc — нет.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Shell вне памяти",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (appSettings.shellOutsideStorage) {
                            "Командам доступны любые пути"
                        } else {
                            "Только внутри /storage/emulated/0"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = appSettings.shellOutsideStorage,
                    onCheckedChange = {
                        viewModel.updateSettings(appSettings.copy(shellOutsideStorage = it))
                    },
                    colors = runnerSwitchColors()
                )
            }
            if (appSettings.shellOutsideStorage) {
                Text(
                    text = "Команды shell смогут читать системные разделы и данные других приложений. " +
                            "Включайте только для конкретной задачи.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                )
            }
        }

        TrashSettings(viewModel, onNavigate)

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * Журнал обратимых операций: что в нём лежит, сколько хранить и как откатить.
 *
 * Раздел появился вместе с обратимым удалением, а затем в него добавились
 * перемещения: у них файлы не теряются, но разложенное агентом по папкам
 * руками уже не собрать. Срок хранения — обещание, которое должно где-то
 * проверяться, а размер нужно видеть: иначе корзина тихо занимает память,
 * которую пользователь считает свободной.
 */
@Composable
private fun TrashSettings(viewModel: MainViewModel, onNavigate: (SettingsRoute) -> Unit) {
    val trash by viewModel.trashState.collectAsState()
    val appSettings by viewModel.settings.collectAsState()
    var confirmEmpty by remember { mutableStateOf(false) }

    // Содержимое корзины меняется только действиями пользователя, поэтому
    // читаем его при входе на экран, а не держим в постоянном наблюдателе.
    LaunchedEffect(Unit) { viewModel.refreshTrash() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = StatusWarning.copy(alpha = 0.10f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.30f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(11.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = StatusWarning,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = "Удаление и перемещение обратимы. Удалённое занимает место, " +
                            "пока его не вернут или не очистят; перемещённое просто лежит " +
                            "на новом месте.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }

        SettingsGroup {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Корзина",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = when {
                        trash.loading -> "Читаю журнал…"
                        trash.count == 0 ->
                            "Пусто. Здесь будут появляться удалённое и перемещённое."
                        else -> buildString {
                            append("Удалено: ${trash.deletions.size}")
                            if (trash.totalBytes > 0) {
                                append(" (")
                                append(ToolDispatcher.formatFileSize(trash.totalBytes))
                                append(")")
                            }
                            append(" · Перемещено: ${trash.moves.size}")
                        }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }

            SettingsDivider()

            SettingsRow(
                label = "Список записей",
                value = if (trash.count == 0) {
                    "пусто"
                } else {
                    PluralUtils.pluralize(trash.count, "запись", "записи", "записей")
                },
                onClick = { onNavigate(SettingsRoute.Trash) },
                icon = Icons.Outlined.Restore
            )

            SettingsDivider()

            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Хранить записи",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(10.dp))
                SegmentedChips(
                    options = AppSettings.TRASH_RETENTION_PRESETS.map { days ->
                        days to if (days == 0) "Всегда" else "$days д"
                    },
                    selected = appSettings.trashRetentionDays,
                    onSelect = { days ->
                        viewModel.updateSettings(appSettings.copy(trashRetentionDays = days))
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "По истечении срока записи исчезают при следующем запуске. " +
                            "Для удалённого это означает безвозвратное стирание, для " +
                            "перемещённого — только потерю возможности откатить.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp
                )
            }

            SettingsDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { viewModel.restoreAllFromTrash() },
                    enabled = trash.count > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Вернуть всё", fontSize = 13.sp)
                }
                TextButton(
                    onClick = { confirmEmpty = true },
                    enabled = trash.count > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Очистить",
                        fontSize = 13.sp,
                        color = if (trash.count > 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                }
            }
        }
    }

    // Очистка корзины — единственное безвозвратное действие в приложении,
    // поэтому спрашиваем подтверждение, а не делаем по одному тапу.
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Очистить корзину?") },
            text = {
                Text(
                    "Удалённое будет стёрто навсегда, вернуть его больше не получится. " +
                            "Сейчас в корзине ${PluralUtils.files(trash.deletedFileCount)} " +
                            "на ${ToolDispatcher.formatFileSize(trash.totalBytes)}.\n\n" +
                            "Файлы, которые агент переместил, останутся на своих новых " +
                            "местах — но откатить эти перемещения станет нельзя."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEmpty = false
                        viewModel.emptyTrash()
                    }
                ) {
                    Text("Удалить навсегда", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

/**
 * Список записей журнала: что именно удалено и перемещено, когда и откуда,
 * с возможностью вернуть по одной.
 *
 * Раньше в настройках был только счётчик — по нему нельзя понять, что лежит
 * в корзине, а значит и решить, возвращать ли.
 */
@Composable
private fun TrashListScreen(viewModel: MainViewModel) {
    val trash by viewModel.trashState.collectAsState()
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmRestoreAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshTrash() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            trash.loading -> Text(
                text = "Читаю журнал…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            trash.count == 0 -> Text(
                text = "Журнал пуст. Удалённое и перемещённое будет появляться здесь.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            else -> {
                Text(
                    text = buildString {
                        append("Удалено: ${trash.deletions.size}")
                        if (trash.totalBytes > 0) {
                            append(" (")
                            append(ToolDispatcher.formatFileSize(trash.totalBytes))
                            append(")")
                        }
                        append(" · Перемещено: ${trash.moves.size}")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )

                SettingsGroup {
                    trash.entries.forEachIndexed { index, entry ->
                        if (index > 0) SettingsDivider()
                        TrashEntryRow(entry) { viewModel.restoreFromTrash(entry.id) }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { confirmRestoreAll = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Вернуть всё", fontSize = 13.sp)
                    }
                    TextButton(
                        onClick = { confirmEmpty = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Очистить",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (confirmRestoreAll) {
        AlertDialog(
            onDismissRequest = { confirmRestoreAll = false },
            title = { Text("Вернуть всё?") },
            text = {
                Text(
                    "Удалённое вернётся на исходные места, перемещённое — обратно туда, " +
                            "откуда его взяли. Если место занято, файл получит новое имя."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestoreAll = false
                        viewModel.restoreAllFromTrash()
                    }
                ) {
                    Text("Вернуть")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestoreAll = false }) { Text("Отмена") }
            }
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Очистить журнал?") },
            text = {
                Text(
                    "Удалённое будет стёрто навсегда, вернуть его больше не получится. " +
                            "Перемещённые файлы останутся на новых местах, но откатить " +
                            "эти перемещения станет нельзя."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEmpty = false
                        viewModel.emptyTrash()
                    }
                ) {
                    Text("Удалить навсегда", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text("Отмена") }
            }
        )
    }
}

/** Одна запись журнала: вид операции, имя объекта, когда и откуда. */
@Composable
private fun TrashEntryRow(entry: TrashStore.TrashEntry, onRestore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (entry.isMove) "ПЕРЕМЕЩЕНИЕ" else "УДАЛЕНИЕ",
                color = if (entry.isMove) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = trashEntryTitle(entry),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = trashEntrySubtitle(entry),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onRestore) {
            Text("Вернуть", fontSize = 13.sp)
        }
    }
}

private fun trashEntryTitle(entry: TrashStore.TrashEntry): String =
    if (entry.items.size == 1) {
        entry.items.first().name
    } else {
        PluralUtils.pluralize(entry.items.size, "объект", "объекта", "объектов")
    }

private fun trashEntrySubtitle(entry: TrashStore.TrashEntry): String {
    val first = entry.items.firstOrNull() ?: return TrashStore.formatDate(entry.at)
    val date = TrashStore.formatDate(entry.at)

    if (!entry.isMove) {
        return if (entry.items.size == 1) {
            "$date · из ${folderOf(first.originalPath)}"
        } else {
            "$date · из разных папок"
        }
    }
    return if (entry.items.size == 1) {
        "$date · ${folderOf(first.originalPath)} → ${folderOf(first.currentPath)}"
    } else {
        "$date · разложено по папкам"
    }
}

/** Папка пути без java.io.File: нужен только текст до последнего слэша. */
private fun folderOf(path: String): String {
    val cut = path.lastIndexOf('/')
    if (cut <= 0) return path
    return path.substring(0, cut)
}

/**
 * Пользовательские доп. инструкции: многострочное поле в терминальном стиле,
 * всегда видно целиком. Кнопка очистки — только когда текст введён.
 * Базовый промпт зашит в код (CORE_SYSTEM_PROMPT) и здесь не показывается.
 */
@Composable
private fun UserInstructionsField(
    instructions: String,
    onInstructionsChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Дополнительные инструкции",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            if (instructions.isNotBlank()) {
                TextButton(
                    onClick = { onInstructionsChange("") },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Очистить",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    RoundedCornerShape(10.dp)
                )
        ) {
            BasicTextField(
                value = instructions,
                onValueChange = onInstructionsChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 96.dp)
                    .padding(12.dp),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (instructions.isEmpty()) {
                        Text(
                            text = "Задайте стиль общения, язык или дополнительные правила поведения агента...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}

/**
 * Компактный сегментный переключатель: равные сегменты, выбранный залит
 * сплошным primary с текстом onPrimary. Раньше активный отличался только
 * тонкой рамкой и оттенком 14% — на свету было не понять, что включено.
 */
@Composable
private fun <T> SegmentedChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    isEnabled: (T) -> Boolean = { true }
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            val enabled = isEnabled(value)
            val shape = RoundedCornerShape(9.dp)

            // Перетекание цвета вместо рывка: переключатель читается как один
            // контрол, а не как набор независимых кнопок
            val background by animateColorAsState(
                targetValue = when {
                    !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.3f)
                    active -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
                },
                animationSpec = MotionTokens.fluidTween(200),
                label = "segment_background"
            )
            val labelColor by animateColorAsState(
                targetValue = when {
                    !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    active -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = MotionTokens.fluidTween(200),
                label = "segment_label"
            )
            val borderColor by animateColorAsState(
                targetValue = when {
                    !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    active -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                },
                animationSpec = MotionTokens.fluidTween(200),
                label = "segment_border"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(background)
                    .border(BorderStroke(1.dp, borderColor), shape)
                    .clickable(enabled = enabled) { onSelect(value) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = labelColor,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Единый слайдер настроек: трек 3dp с засечками по шагам, бегунок 14dp
 * с кольцом цвета фона, снизу — границы диапазона и текущее значение по центру.
 *
 * Раньше температура и лимит шагов выглядели по-разному (гладкая полоска против
 * засечек, значение сбоку против подписи снизу), хотя это один и тот же контрол.
 */
@Composable
private fun SettingSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: (Float) -> String,
    endDecimals: Int = 0
) {
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val intervals = (steps + 1).coerceAtLeast(1)
    val stepSize = span / intervals
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val thumbSize = 14.dp

    fun snap(raw: Float): Float {
        val clamped = raw.coerceIn(valueRange.start, valueRange.endInclusive)
        val snapped = valueRange.start +
                Math.round((clamped - valueRange.start) / stepSize) * stepSize
        return snapped.coerceIn(valueRange.start, valueRange.endInclusive)
    }

    fun formatEnd(edge: Float): String =
        String.format(Locale.US, "%.${endDecimals}f", edge)

    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(valueRange, steps) {
                    val trackWidth = size.width.toFloat().coerceAtLeast(1f)
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onValueChange(
                            snap(valueRange.start + (down.position.x / trackWidth) * span)
                        )
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            onValueChange(
                                snap(valueRange.start + (change.position.x / trackWidth) * span)
                            )
                            change.consume()
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val availableWidth = maxWidth - thumbSize

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )

            Box(
                modifier = Modifier
                    .width(availableWidth * fraction + thumbSize / 2)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )

            // Засечки по каждому шагу: короткие обычные, длинные — каждые пять
            for (index in 0..intervals) {
                val tickFraction = index.toFloat() / intervals
                val isPassed = tickFraction <= fraction + 0.001f
                val isMajor = index == 0 || index == intervals || index % 5 == 0
                Box(
                    modifier = Modifier
                        .offset(x = availableWidth * tickFraction + (thumbSize - 2.dp) / 2)
                        .size(width = 2.dp, height = if (isMajor) 9.dp else 5.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(
                            if (isPassed) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            }
                        )
                )
            }

            Box(
                modifier = Modifier
                    .offset(x = availableWidth * fraction)
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatEnd(valueRange.start),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = valueLabel(value),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = formatEnd(valueRange.endInclusive),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Маскирует значения секретов (Authorization, *-Secret, *-Key, *-Token) символами •
 * Сохраняет длину 1-в-1, чтобы курсор и редактирование работали без искажения смещений.
 */
private class SecretHeadersVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        if (original.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val masked = buildString {
            val lines = original.split("\n")
            lines.forEachIndexed { lineIndex, line ->
                if (lineIndex > 0) append("\n")

                val colonIndex = line.indexOf(':')
                if (colonIndex > 0) {
                    val keyPart = line.substring(0, colonIndex)
                    if (isSecretHeaderName(keyPart)) {
                        append(keyPart)
                        append(':')
                        val valuePart = line.substring(colonIndex + 1)
                        var inQuotes = false
                        var quoteStart = -1
                        var quoteEnd = -1
                        val firstNonSpace = valuePart.indexOfFirst { !it.isWhitespace() }
                        if (firstNonSpace >= 0 && valuePart[firstNonSpace] == '"') {
                            val lastQuote = valuePart.lastIndexOf('"')
                            if (lastQuote > firstNonSpace) {
                                inQuotes = true
                                quoteStart = firstNonSpace
                                quoteEnd = lastQuote
                            }
                        }

                        if (inQuotes) {
                            for (i in valuePart.indices) {
                                if (i <= quoteStart || i >= quoteEnd || valuePart[i].isWhitespace()) {
                                    append(valuePart[i])
                                } else {
                                    append('•')
                                }
                            }
                        } else {
                            for (ch in valuePart) {
                                if (ch.isWhitespace()) {
                                    append(ch)
                                } else {
                                    append('•')
                                }
                            }
                        }
                    } else {
                        append(line)
                    }
                } else {
                    append(line)
                }
            }
        }

        return TransformedText(AnnotatedString(masked), OffsetMapping.Identity)
    }

    private fun isSecretHeaderName(name: String): Boolean {
        val clean = name.trim().removeSurrounding("\"").lowercase()
        return clean == "authorization" ||
                clean.endsWith("-secret") || clean.endsWith("_secret") || clean == "secret" ||
                clean.endsWith("-key") || clean.endsWith("_key") || clean == "key" ||
                clean.endsWith("-token") || clean.endsWith("_token") || clean == "token"
    }
}

@Composable
private fun AccessSettings(
    viewModel: MainViewModel,
    onOpenStorageSettings: () -> Unit,
    onOpenAppSettings: () -> Unit = {}
) {
    val hasStorage by viewModel.hasStoragePermission.collectAsState()
    val hasContacts by viewModel.hasContactsPermission.collectAsState()
    val hasCall by viewModel.hasCallPermission.collectAsState()
    val hasAudio by viewModel.hasAudioPermission.collectAsState()
    val hasPhoneAccess = hasContacts && hasCall

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Файлы и память
        SettingsGroup("Файлы и память") {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasStorage) {
                            Icons.Outlined.CheckCircle
                        } else {
                            Icons.Outlined.ErrorOutline
                        },
                        contentDescription = null,
                        tint = if (hasStorage) StatusSuccess else StatusWarning,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (hasStorage) "Доступ ко всем файлам выдан" else "Доступ к файлам не выдан",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Нужен, чтобы агент читал, перемещал и анализировал файлы. " +
                            "Включите «Доступ ко всем файлам» на системном экране.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onOpenStorageSettings()
                        viewModel.checkAllPermissions()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Настройки доступа к памяти",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 2. Телефон и контакты
        SettingsGroup("Телефонные звонки и контакты") {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasPhoneAccess) {
                            Icons.Outlined.CheckCircle
                        } else {
                            Icons.Outlined.ErrorOutline
                        },
                        contentDescription = null,
                        tint = if (hasPhoneAccess) StatusSuccess else StatusWarning,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when {
                            hasPhoneAccess -> "Доступ к звонкам и контактам выдан"
                            hasContacts && !hasCall -> "Доступ к контактам выдан (звонки через диалер)"
                            !hasContacts && hasCall -> "Звонки разрешены, контакты не выданы"
                            else -> "Разрешения не выданы"
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Нужно, чтобы агент мгновенно совершал прямые телефонные звонки " +
                            "по вашим запросам («Позвони Маме») и искал контакты в телефонной книге.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onOpenAppSettings()
                        viewModel.checkAllPermissions()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Открыть разрешения приложения",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 3. Микрофон и голос
        SettingsGroup("Голосовой ввод (микрофон)") {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasAudio) {
                            Icons.Outlined.CheckCircle
                        } else {
                            Icons.Outlined.ErrorOutline
                        },
                        contentDescription = null,
                        tint = if (hasAudio) StatusSuccess else StatusWarning,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (hasAudio) "Доступ к микрофону выдан" else "Доступ к микрофону не выдан",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Нужен для распознавания голосовых команд и диктовки сообщений через кнопку микрофона.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onOpenAppSettings()
                        viewModel.checkAllPermissions()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Открыть разрешения приложения",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        if (title != null) {
            Text(
                text = title,
                // TextSecondary вместо TextTertiary: на чистом чёрном Tertiary почти растворяется
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    mono: Boolean = false,
    valueColor: Color = Color.Unspecified,
    icon: ImageVector? = null
) {
    val resolvedValueColor = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = resolvedValueColor,
                fontSize = 11.5.sp,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.5.dp)
}

/**
 * Статус проверки связи + триггер в одном виджете: тап по плашке запускает
 * проверку заново. Состояния: ожидание (приглашение), тест (спиннер),
 * результат (цветной бейдж — успех/доступен/ошибка вроде 403).
 */
@Composable
private fun ConnectionStatusWidget(
    isTesting: Boolean,
    result: ConnectionTestResult?,
    onCheck: () -> Unit
) {
    val errorColor = MaterialTheme.colorScheme.error
    val bg = when {
        isTesting -> MaterialTheme.colorScheme.surfaceContainerHighest
        result == null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        result.isSuccess -> StatusSuccess.copy(alpha = 0.12f)
        result.isReachable -> StatusWarning.copy(alpha = 0.12f)
        else -> errorColor.copy(alpha = 0.12f)
    }
    val border = when {
        isTesting -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        result == null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        result.isSuccess -> StatusSuccess.copy(alpha = 0.35f)
        result.isReachable -> StatusWarning.copy(alpha = 0.35f)
        else -> errorColor.copy(alpha = 0.35f)
    }
    val accent = when {
        isTesting -> MaterialTheme.colorScheme.outline
        result == null -> MaterialTheme.colorScheme.primary
        result.isSuccess -> StatusSuccess
        result.isReachable -> StatusWarning
        else -> errorColor
    }
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.dp, border), shape)
            .clickable(enabled = !isTesting) { onCheck() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            when {
                isTesting -> CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )

                result == null -> Icon(
                    imageVector = RunnerIcons.Activity,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp)
                )

                else -> Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isTesting -> "Проверка..."
                        result == null -> "Проверить связь"
                        else -> result.message
                    },
                    color = if (result == null && !isTesting) MaterialTheme.colorScheme.onSurface else accent,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (!isTesting && result == null) {
                    Text(
                        text = "Нажмите, чтобы запустить тест пинга",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            if (!isTesting) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = if (result == null) null else "Проверить заново",
                    tint = accent.copy(alpha = 0.75f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

private fun isValidHttpUrl(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return false
    return try {
        val uri = URI(trimmed)
        val scheme = uri.scheme?.lowercase()
        (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    } catch (e: Exception) {
        false
    }
}

private fun readClipboard(context: Context): String {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
}

@Composable
private fun runnerSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
)

@Composable
private fun ThemeSettingsContent(
    themeConfig: ThemeConfig,
    onModeChange: (AppThemeMode) -> Unit,
    onSourceChange: (ColorSource) -> Unit,
    onSeedColorChange: (Int) -> Unit,
    onAmoledChange: (Boolean) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val isDynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(modifier = Modifier.padding(14.dp)) {
        // 1. Режим: Системная / Светлая / Тёмная
        Text(
            text = "Режим",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(10.dp))
        SegmentedChips(
            options = listOf(
                AppThemeMode.SYSTEM to "Системная",
                AppThemeMode.LIGHT to "Светлая",
                AppThemeMode.DARK to "Тёмная"
            ),
            selected = themeConfig.themeMode,
            onSelect = onModeChange
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Источник цвета: Обои / Свой цвет
        Text(
            text = "Источник цвета",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(10.dp))
        SegmentedChips(
            options = listOf(
                ColorSource.DYNAMIC to "Обои",
                ColorSource.CUSTOM to "Свой цвет"
            ),
            // Без Android 12+ «Обои» недоступны — показываем выбранным «Свой цвет».
            selected = if (isDynamicAvailable) themeConfig.colorSource else ColorSource.CUSTOM,
            onSelect = onSourceChange,
            isEnabled = { it != ColorSource.DYNAMIC || isDynamicAvailable }
        )

        if (!isDynamicAvailable) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "«Обои» недоступны: требуется Android 12+",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp
            )
        }

        // 3. Палитра из 10-12 цветов + кастомный пикер
        if (themeConfig.colorSource == ColorSource.CUSTOM || !isDynamicAvailable) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Палитра акцента",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(10.dp))

            val presets = ThemeConfig.PRESET_COLORS
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    presets.take(6).forEach { colorInt ->
                        ColorCircle(
                            colorInt = colorInt,
                            isSelected = themeConfig.colorSource == ColorSource.CUSTOM && themeConfig.customSeedColor == colorInt,
                            onClick = { onSeedColorChange(colorInt) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    presets.drop(6).take(5).forEach { colorInt ->
                        ColorCircle(
                            colorInt = colorInt,
                            isSelected = themeConfig.colorSource == ColorSource.CUSTOM && themeConfig.customSeedColor == colorInt,
                            onClick = { onSeedColorChange(colorInt) }
                        )
                    }
                    // Кнопка кастомного пикера (плюс / палитра)
                    val isCustomPickerActive = themeConfig.colorSource == ColorSource.CUSTOM &&
                            !presets.contains(themeConfig.customSeedColor)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCustomPickerActive) Color(themeConfig.customSeedColor)
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .border(
                                BorderStroke(
                                    if (isCustomPickerActive) 2.dp else 1.dp,
                                    if (isCustomPickerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                CircleShape
                            )
                            .clickable { showColorPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Выбрать свой цвет",
                            tint = if (isCustomPickerActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    SettingsDivider()

    // 4. Тумблер «AMOLED чёрный»
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AMOLED чёрный",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )
            Text(
                text = "В тёмной теме чистый чёрный фон (#000000)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp
            )
        }
        Switch(
            checked = themeConfig.isAmoled,
            onCheckedChange = onAmoledChange,
            colors = runnerSwitchColors()
        )
    }

    if (showColorPicker) {
        CustomColorPickerDialog(
            initialColor = themeConfig.customSeedColor,
            onDismiss = { showColorPicker = false },
            onApply = {
                onSeedColorChange(it)
                showColorPicker = false
            }
        )
    }
}

@Composable
private fun ColorCircle(
    colorInt: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = Color(colorInt)
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                BorderStroke(
                    if (isSelected) 2.5.dp else 1.dp,
                    if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Black.copy(alpha = 0.2f)
                ),
                CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = if (isColorDark(colorInt)) Color.White else Color.Black,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun isColorDark(colorInt: Int): Boolean {
    val r = (colorInt shr 16) and 0xFF
    val g = (colorInt shr 8) and 0xFF
    val b = colorInt and 0xFF
    return (0.299 * r + 0.587 * g + 0.114 * b) < 128
}

@Composable
private fun CustomColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit
) {
    var hexInput by remember {
        mutableStateOf(String.format("%06X", 0xFFFFFF and initialColor))
    }
    val parsedColor = remember(hexInput) {
        runCatching {
            val clean = hexInput.trim().removePrefix("#")
            if (clean.length == 6) {
                (0xFF000000.toInt()) or clean.toLong(16).toInt()
            } else null
        }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Свой цвет темы",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Введите 6-значный HEX-код цвета (например 4CAF50):",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                parsedColor?.let { Color(it) }
                                    ?: MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                CircleShape
                            )
                    )
                    BasicTextField(
                        value = hexInput,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == '#' }.take(7)
                            hexInput = filtered
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (parsedColor != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
                if (hexInput.isNotBlank() && parsedColor == null) {
                    Text(
                        text = "Некорректный HEX-код",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    parsedColor?.let { onApply(it) }
                },
                enabled = parsedColor != null
            ) {
                Text(
                    "Применить",
                    color = if (parsedColor != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp)
    )
}
