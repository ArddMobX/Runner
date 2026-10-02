package com.runner.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.data.AppSettings
import com.runner.app.data.AppThemeMode
import com.runner.app.data.ColorSource
import com.runner.app.data.Provider
import com.runner.app.data.ThemeConfig
import com.runner.app.ui.components.ProviderLogos
import com.runner.app.ui.components.RunnerIcons
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.ui.theme.StatusWarning
import com.runner.app.ui.theme.bounceClick
import java.net.URI
import java.util.Locale
import kotlin.math.roundToInt

private sealed interface SettingsRoute {
    data object Root : SettingsRoute
    data object Providers : SettingsRoute
    data class ProviderEdit(val providerId: String) : SettingsRoute
    data object Agent : SettingsRoute
    data object Access : SettingsRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onOpenStorageSettings: () -> Unit
) {
    var route by remember { mutableStateOf<SettingsRoute>(SettingsRoute.Root) }
    BackHandler(enabled = route != SettingsRoute.Root) { route = SettingsRoute.Root }

    val title = when (route) {
        SettingsRoute.Root -> "Настройки"
        SettingsRoute.Providers -> "Провайдеры"
        is SettingsRoute.ProviderEdit -> "Провайдер"
        SettingsRoute.Agent -> "Агент"
        SettingsRoute.Access -> "Доступы"
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

                SettingsRoute.Agent -> AgentSettings(viewModel)

                SettingsRoute.Access -> AccessSettings(viewModel, onOpenStorageSettings)
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
                value = "Ключей: $keysCount · Активен: ${activeProvider?.name ?: "—"}",
                onClick = { onNavigate(SettingsRoute.Providers) }
            )
            SettingsDivider()
            SettingsRow(
                label = "Модель",
                // Показываем человеческое имя, технический ID живёт в списке выбора
                value = formatModelName(activeProvider?.activeModel.orEmpty()),
                onClick = { onNavigate(SettingsRoute.Providers) }
            )
        }

        SettingsGroup("Агент") {
            SettingsRow(
                label = "Системный промпт",
                value = "${appSettings.systemPrompt.length} символов",
                onClick = { onNavigate(SettingsRoute.Agent) }
            )
            SettingsDivider()
            SettingsRow(
                label = "Температура и шаги",
                value = "${appSettings.temperature} · ${appSettings.maxSteps} шагов",
                onClick = { onNavigate(SettingsRoute.Agent) }
            )
        }

        SettingsGroup("Доступы") {
            SettingsRow(
                label = "Доступ ко всем файлам",
                value = if (hasStorage) "выдан" else "не выдан",
                valueColor = if (hasStorage) StatusSuccess else StatusWarning,
                onClick = { onNavigate(SettingsRoute.Access) }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    AppSettings.TEXT_SCALES.forEach { scale ->
                        val isSelected = scale == appSettings.textScale
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable {
                                    viewModel.updateSettings(appSettings.copy(textScale = scale))
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${(scale * 100).roundToInt()}%",
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            )
                        }
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
                        text = "Детали вызовов инструментов",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Показывать аргументы и вывод тулов",
                        color = MaterialTheme.colorScheme.outline,
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
                        color = MaterialTheme.colorScheme.outline,
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
                                        color = MaterialTheme.colorScheme.outline,
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

            // 2. Кнопка «Проверить соединение»
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Проверка связи",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (proxyUrl.isNotBlank()) "Тест пинга до ${activeProvider?.name ?: "API"} через reverse proxy" else "Тест пинга до ${activeProvider?.name ?: "API"}",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 11.5.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (connectionState.isTesting) MaterialTheme.colorScheme.surfaceContainerHighest
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (connectionState.isTesting) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = !connectionState.isTesting) {
                                viewModel.testConnection()
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (connectionState.isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Проверка...",
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Icon(
                                    imageVector = RunnerIcons.Activity,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Проверить",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                connectionState.result?.let { res ->
                    Spacer(modifier = Modifier.height(10.dp))
                    val errorColor = MaterialTheme.colorScheme.error
                    val badgeBg = when {
                        res.isSuccess -> StatusSuccess.copy(alpha = 0.12f)
                        res.isReachable -> StatusWarning.copy(alpha = 0.12f)
                        else -> errorColor.copy(alpha = 0.12f)
                    }
                    val badgeBorder = when {
                        res.isSuccess -> StatusSuccess.copy(alpha = 0.35f)
                        res.isReachable -> StatusWarning.copy(alpha = 0.35f)
                        else -> errorColor.copy(alpha = 0.35f)
                    }
                    val badgeTextColor = when {
                        res.isSuccess -> StatusSuccess
                        res.isReachable -> StatusWarning
                        else -> errorColor
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeBg)
                            .border(BorderStroke(1.dp, badgeBorder), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(badgeTextColor)
                            )
                            Text(
                                text = res.message,
                                color = badgeTextColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
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
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            RoundedCornerShape(10.dp)
                        ),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    AppSettings.CONNECT_TIMEOUT_PRESETS.forEach { sec ->
                        val isSelected = sec == appSettings.connectTimeoutSeconds
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
                                )
                                .clickable {
                                    viewModel.updateSettings(appSettings.copy(connectTimeoutSeconds = sec))
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${sec}с",
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                }
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
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            RoundedCornerShape(10.dp)
                        ),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    AppSettings.RESPONSE_TIMEOUT_PRESETS.forEach { sec ->
                        val isSelected = sec == appSettings.responseTimeoutSeconds
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
                                )
                                .clickable {
                                    viewModel.updateSettings(appSettings.copy(responseTimeoutSeconds = sec))
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${sec}с",
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                }
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
                            color = MaterialTheme.colorScheme.outline,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
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
                    color = MaterialTheme.colorScheme.outline,
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
                            color = MaterialTheme.colorScheme.outline,
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
                                color = MaterialTheme.colorScheme.outline,
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    val id = "custom_${System.currentTimeMillis()}"
                    viewModel.saveProvider(
                        Provider(id = id, name = "Новый провайдер", baseUrl = "")
                    )
                    onEdit(id)
                }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Добавить провайдера", color = MaterialTheme.colorScheme.primary, fontSize = 13.5.sp)
        }
    }
}

@Composable
private fun AgentSettings(viewModel: MainViewModel) {
    val appSettings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SystemPromptBlock(
            prompt = appSettings.systemPrompt,
            onPromptChange = { viewModel.updateSettings(appSettings.copy(systemPrompt = it)) },
            onCopy = {
                copyText(context, appSettings.systemPrompt, "Промпт")
            },
            onReset = { viewModel.resetSystemPrompt() }
        )

        SettingsGroup {
            Column(modifier = Modifier.padding(14.dp)) {
                SliderHeader(
                    title = "Температура",
                    badge = String.format(Locale.US, "%.1f", appSettings.temperature)
                )
                Spacer(modifier = Modifier.height(10.dp))
                MinimalSlider(
                    value = appSettings.temperature,
                    onValueChange = {
                        viewModel.updateSettings(appSettings.copy(temperature = it))
                    },
                    valueRange = AppSettings.TEMPERATURE_RANGE,
                    steps = 14
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ниже — предсказуемее, выше — креативнее.",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.5.sp
                )
            }
        }

        SettingsGroup {
            Column(modifier = Modifier.padding(14.dp)) {
                SliderHeader(
                    title = "Лимит шагов",
                    badge = "${appSettings.maxSteps}"
                )
                Spacer(modifier = Modifier.height(10.dp))
                DiscreteStepsSlider(
                    value = appSettings.maxSteps,
                    onValueChange = {
                        viewModel.updateSettings(appSettings.copy(maxSteps = it))
                    },
                    range = AppSettings.STEPS_RANGE
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Сколько раз модель может вызвать инструменты в одной задаче (хватает на scan → unpack → sort).",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * Системный промпт как терминальный блок: моноширинный шрифт на тёмной плашке,
 * копирование в углу, свёрнуто до 6 строк с кнопкой разворота.
 */
@Composable
private fun SystemPromptBlock(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onCopy: () -> Unit,
    onReset: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    SettingsGroup("Системный промпт") {
        Column(modifier = Modifier.padding(14.dp)) {
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
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "system",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Копировать промпт",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    BasicTextField(
                        value = prompt,
                        onValueChange = onPromptChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        maxLines = if (expanded) Int.MAX_VALUE else 6,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (expanded) "Свернуть" else "Показать полностью",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.5.sp
                    )
                }
                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "Сбросить", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                }
            }
        }
    }
}

@Composable
private fun SliderHeader(title: String, badge: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badge,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Минималистичный слайдер: трек 2dp и бегунок 12dp вместо громоздкого M3-ползунка.
 * Тап и протяжка обрабатываются одним жестом, чтобы не конфликтовали.
 */
@Composable
private fun MinimalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val thumbSize = 12.dp

    fun snap(raw: Float): Float {
        val clamped = raw.coerceIn(valueRange.start, valueRange.endInclusive)
        if (steps <= 0) return clamped
        val step = span / (steps + 1)
        val snapped = valueRange.start + Math.round((clamped - valueRange.start) / step) * step
        return snapped.coerceIn(valueRange.start, valueRange.endInclusive)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(valueRange, steps) {
                val trackWidth = size.width.toFloat().coerceAtLeast(1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onValueChange(snap(valueRange.start + (down.position.x / trackWidth) * span))
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )

        Box(
            modifier = Modifier
                .width((maxWidth - thumbSize) * fraction + thumbSize / 2)
                .height(2.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )

        Box(
            modifier = Modifier
                .offset(x = (maxWidth - thumbSize) * fraction)
                .size(thumbSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

/**
 * Дискретный слайдер для лимита шагов:
 * - С засечками (делениями) на шкале под каждый целый шаг.
 * - Принудительно целые значения с плавной привязкой (snap).
 * - Текущее число и диапазон выводятся прямо под шкалой.
 */
@Composable
private fun DiscreteStepsSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange = AppSettings.STEPS_RANGE
) {
    val totalSteps = (range.last - range.first).coerceAtLeast(1)
    val fraction = ((value - range.first).toFloat() / totalSteps).coerceIn(0f, 1f)
    val thumbSize = 14.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(range) {
                    val trackWidth = size.width.toFloat().coerceAtLeast(1f)
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val rawRatio = (down.position.x / trackWidth).coerceIn(0f, 1f)
                        val stepVal = (range.first + (rawRatio * totalSteps).roundToInt()).coerceIn(range.first, range.last)
                        onValueChange(stepVal)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val dragRatio = (change.position.x / trackWidth).coerceIn(0f, 1f)
                            val dragVal = (range.first + (dragRatio * totalSteps).roundToInt()).coerceIn(range.first, range.last)
                            onValueChange(dragVal)
                            change.consume()
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val availableWidth = maxWidth - thumbSize

            // Базовый трек
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )

            // Активный трек
            Box(
                modifier = Modifier
                    .width(availableWidth * fraction + thumbSize / 2)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )

            // Деления (tick marks) по всей длине трека
            for (step in range) {
                val stepFraction = (step - range.first).toFloat() / totalSteps
                val isPassed = step <= value
                val isMajor = step % 5 == 0 || step == range.first || step == range.last
                Box(
                    modifier = Modifier
                        .offset(x = availableWidth * stepFraction + (thumbSize - 2.dp) / 2)
                        .size(width = 2.dp, height = if (isMajor) 9.dp else 5.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(
                            if (isPassed) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                )
            }

            // Бегунок (Thumb)
            Box(
                modifier = Modifier
                    .offset(x = availableWidth * fraction)
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }

        // Подписи диапазона и текущее число рядом
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${range.first}",
                color = MaterialTheme.colorScheme.outline,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "$value шагов",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${range.last}",
                color = MaterialTheme.colorScheme.outline,
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
    onOpenStorageSettings: () -> Unit
) {
    val hasStorage by viewModel.hasStoragePermission.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SettingsGroup {
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
                        text = if (hasStorage) "Доступ выдан" else "Доступ не выдан",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Нужен, чтобы агент читал и раскладывал файлы. Тумблер сам право не даёт — " +
                            "включи «Доступ ко всем файлам» на системном экране.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onOpenStorageSettings()
                        viewModel.checkStoragePermission()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Открыть системные настройки",
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
    valueColor: Color = Color.Unspecified
) {
    val resolvedValueColor = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

private fun copyText(context: Context, text: String, label: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label скопирован в буфер", Toast.LENGTH_SHORT).show()
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    RoundedCornerShape(10.dp)
                )
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val modes = listOf(
                AppThemeMode.SYSTEM to "Системная",
                AppThemeMode.LIGHT to "Светлая",
                AppThemeMode.DARK to "Тёмная"
            )
            modes.forEach { (mode, label) ->
                val isSelected = themeConfig.themeMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            else Color.Transparent
                        )
                        .clickable { onModeChange(mode) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Источник цвета: Обои / Свой цвет
        Text(
            text = "Источник цвета",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    RoundedCornerShape(10.dp)
                )
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Опция "Обои"
            val isDynamicSelected = themeConfig.colorSource == ColorSource.DYNAMIC
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (isDynamicSelected && isDynamicAvailable) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable(enabled = isDynamicAvailable) {
                        onSourceChange(ColorSource.DYNAMIC)
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Обои",
                    color = when {
                        !isDynamicAvailable -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        isDynamicSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 12.5.sp,
                    fontWeight = if (isDynamicSelected && isDynamicAvailable) FontWeight.Medium else FontWeight.Normal
                )
            }

            // Опция "Свой цвет"
            val isCustomSelected = themeConfig.colorSource == ColorSource.CUSTOM || !isDynamicAvailable
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (isCustomSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else Color.Transparent
                    )
                    .clickable {
                        onSourceChange(ColorSource.CUSTOM)
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Свой цвет",
                    color = if (isCustomSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    fontWeight = if (isCustomSelected) FontWeight.Medium else FontWeight.Normal
                )
            }
        }

        if (!isDynamicAvailable) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "«Обои» недоступны: требуется Android 12+",
                color = MaterialTheme.colorScheme.outline,
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
                color = MaterialTheme.colorScheme.outline,
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
