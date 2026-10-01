package com.runner.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.data.AppSettings
import com.runner.app.data.Provider
import com.runner.app.ui.theme.AccentPrimary
import com.runner.app.ui.theme.OutlineSubtle
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.ui.theme.StatusWarning
import com.runner.app.ui.theme.SurfaceContainer
import com.runner.app.ui.theme.SurfaceContainerHigh
import com.runner.app.ui.theme.SurfaceContainerLow
import com.runner.app.ui.theme.SurfaceDark
import com.runner.app.ui.theme.TextPrimary
import com.runner.app.ui.theme.TextSecondary
import com.runner.app.ui.theme.TextTertiary
import com.runner.app.ui.theme.bounceClick
import kotlin.math.roundToInt

private sealed interface SettingsRoute {
    data object Root : SettingsRoute
    data object Providers : SettingsRoute
    data class ProviderEdit(val providerId: String) : SettingsRoute
    data object Agent : SettingsRoute
    data object Access : SettingsRoute
    data object Appearance : SettingsRoute
    data object Advanced : SettingsRoute
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
        SettingsRoute.Appearance -> "Внешний вид"
        SettingsRoute.Advanced -> "Дополнительно"
    }

    val goBack = {
        if (route == SettingsRoute.Root) onBackClick() else route = SettingsRoute.Root
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
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
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = SurfaceDark
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

                SettingsRoute.Appearance -> AppearanceSettings(viewModel)

                SettingsRoute.Advanced -> AdvancedSettings(viewModel)
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SettingsGroup("Провайдеры") {
            SettingsRow(
                label = "Провайдеры и ключи",
                value = "${providers.size} · активен ${activeProvider?.name ?: "—"}",
                onClick = { onNavigate(SettingsRoute.Providers) }
            )
            SettingsDivider()
            SettingsRow(
                label = "Модель",
                value = activeProvider?.activeModel.orEmpty().ifBlank { "не выбрана" },
                mono = true,
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
                label = "Температура и лимит шагов",
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

        SettingsGroup("Внешний вид") {
            SettingsRow(
                label = "Масштаб текста",
                value = "${(appSettings.textScale * 100).roundToInt()}%",
                onClick = { onNavigate(SettingsRoute.Appearance) }
            )
            SettingsDivider()
            SettingsRow(
                label = "Детали вызовов инструментов",
                value = if (appSettings.showToolDetails) "показывать" else "скрывать",
                onClick = { onNavigate(SettingsRoute.Appearance) }
            )
        }

        SettingsGroup("Дополнительно") {
            SettingsRow(
                label = "Прокси для запросов",
                value = appSettings.reverseProxyUrl.ifBlank { "не задан" },
                mono = appSettings.reverseProxyUrl.isNotBlank(),
                onClick = { onNavigate(SettingsRoute.Advanced) }
            )
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(provider.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = provider.name,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (provider.id == activeProviderId) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "активен", color = AccentPrimary, fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = provider.activeModel.ifBlank { "модель не выбрана" },
                            color = TextTertiary,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = if (provider.apiKey.isNotBlank()) {
                            Icons.Outlined.CheckCircle
                        } else {
                            Icons.Outlined.ErrorOutline
                        },
                        contentDescription = null,
                        tint = if (provider.apiKey.isNotBlank()) StatusSuccess else TextTertiary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TextTertiary,
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
                tint = AccentPrimary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Добавить провайдера", color = AccentPrimary, fontSize = 13.5.sp)
        }
    }
}

@Composable
private fun AgentSettings(viewModel: MainViewModel) {
    val appSettings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SettingsGroup("Системный промпт") {
            Column(modifier = Modifier.padding(14.dp)) {
                TextField(
                    value = appSettings.systemPrompt,
                    onValueChange = { viewModel.updateSettings(appSettings.copy(systemPrompt = it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                    shape = RoundedCornerShape(10.dp),
                    colors = settingFieldColors()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Сбросить к стандартному",
                    color = AccentPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { viewModel.resetSystemPrompt() }
                        .padding(vertical = 4.dp)
                )
            }
        }

        SettingsGroup("Температура") {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "%.1f".format(appSettings.temperature),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = appSettings.temperature,
                    onValueChange = {
                        viewModel.updateSettings(
                            appSettings.copy(temperature = (it * 10).roundToInt() / 10f)
                        )
                    },
                    valueRange = AppSettings.TEMPERATURE_RANGE,
                    colors = settingSliderColors()
                )
                Text(
                    text = "Ниже — предсказуемее, выше — креативнее.",
                    color = TextTertiary,
                    fontSize = 11.5.sp
                )
            }
        }

        SettingsGroup("Лимит шагов агента") {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "${appSettings.maxSteps}",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = appSettings.maxSteps.toFloat(),
                    onValueChange = {
                        viewModel.updateSettings(appSettings.copy(maxSteps = it.roundToInt()))
                    },
                    valueRange = AppSettings.STEPS_RANGE.first.toFloat()..
                            AppSettings.STEPS_RANGE.last.toFloat(),
                    steps = 10,
                    colors = settingSliderColors()
                )
                Text(
                    text = "Сколько раз модель может вызвать инструменты в одной задаче.",
                    color = TextTertiary,
                    fontSize = 11.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
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
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Нужен, чтобы агент читал и раскладывал файлы. Тумблер сам право не даёт — " +
                            "включи «Доступ ко всем файлам» на системном экране.",
                    color = TextSecondary,
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
                        containerColor = AccentPrimary,
                        contentColor = SurfaceDark
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
private fun AppearanceSettings(viewModel: MainViewModel) {
    val appSettings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SettingsGroup("Масштаб текста") {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppSettings.TEXT_SCALES.forEach { scale ->
                    val isSelected = scale == appSettings.textScale
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) SurfaceContainerHigh else SurfaceContainer)
                            .clickable {
                                viewModel.updateSettings(appSettings.copy(textScale = scale))
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${(scale * 100).roundToInt()}%",
                            color = if (isSelected) AccentPrimary else TextPrimary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        SettingsGroup {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Детали вызовов инструментов",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Аргументы и полный вывод инструмента",
                        color = TextTertiary,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = appSettings.showToolDetails,
                    onCheckedChange = {
                        viewModel.updateSettings(appSettings.copy(showToolDetails = it))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SurfaceDark,
                        checkedTrackColor = AccentPrimary,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = SurfaceContainerHigh
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun AdvancedSettings(viewModel: MainViewModel) {
    val appSettings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SettingsGroup("Reverse proxy") {
            Column(modifier = Modifier.padding(14.dp)) {
                TextField(
                    value = appSettings.reverseProxyUrl,
                    onValueChange = {
                        viewModel.updateSettings(appSettings.copy(reverseProxyUrl = it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("https://my-proxy.workers.dev/v1", color = TextTertiary, fontSize = 13.sp)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = settingFieldColors()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Если задан, запросы идут через него вместо Base URL провайдера.",
                    color = TextTertiary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun settingFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = SurfaceContainer,
    unfocusedContainerColor = SurfaceContainer,
    focusedIndicatorColor = OutlineSubtle,
    unfocusedIndicatorColor = OutlineSubtle,
    cursorColor = AccentPrimary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)

@Composable
private fun settingSliderColors() = SliderDefaults.colors(
    thumbColor = AccentPrimary,
    activeTrackColor = AccentPrimary,
    inactiveTrackColor = SurfaceContainerHigh
)

@Composable
private fun SettingsGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        if (title != null) {
            Text(
                text = title,
                color = TextTertiary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
            border = BorderStroke(0.5.dp, OutlineSubtle),
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
    valueColor: Color = TextSecondary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = TextPrimary, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 11.5.sp,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = OutlineSubtle, thickness = 0.5.dp)
}
