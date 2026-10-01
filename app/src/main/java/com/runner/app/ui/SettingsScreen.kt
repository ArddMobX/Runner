package com.runner.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.ui.theme.*

data class ProviderPreset(
    val title: String,
    val baseUrl: String,
    val defaultModel: String
)

val PROVIDER_PRESETS = listOf(
    ProviderPreset("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
    ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct"),
    ProviderPreset("DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
    ProviderPreset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val savedBaseUrl by viewModel.baseUrl.collectAsState()
    val savedApiKey by viewModel.apiKey.collectAsState()
    val savedModelName by viewModel.modelName.collectAsState()
    val hasStoragePermission by viewModel.hasStoragePermission.collectAsState()

    var baseUrlInput by remember(savedBaseUrl) { mutableStateOf(savedBaseUrl) }
    var apiKeyInput by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    var modelInput by remember(savedModelName) { mutableStateOf(savedModelName) }
    var keyVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Настройки",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.bounceClick { onBackClick() }
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Group 1: API Configuration
            PreferenceGroup(title = "ПАРАМЕТРЫ ПОДКЛЮЧЕНИЯ") {
                // Provider Presets
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Шаблоны провайдеров",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Быстрое переключение конфигурации в один клик",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PROVIDER_PRESETS.forEach { preset ->
                            val isSelected = baseUrlInput == preset.baseUrl
                            Surface(
                                color = if (isSelected) SurfaceContainerHighest else SurfaceContainer,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) AccentPrimary.copy(alpha = 0.5f) else OutlineSubtle
                                ),
                                modifier = Modifier
                                    .bounceClick {
                                        baseUrlInput = preset.baseUrl
                                        modelInput = preset.defaultModel
                                    }
                            ) {
                                Text(
                                    text = preset.title,
                                    color = if (isSelected) AccentPrimary else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = OutlineSubtle, thickness = 0.5.dp)

                // Base URL
                PreferenceInputRow(
                    label = "Base URL",
                    description = "Эндпоинт OpenAI-совместимого сервиса"
                ) {
                    OutlinedTextField(
                        value = baseUrlInput,
                        onValueChange = { baseUrlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://api.openai.com/v1", color = TextTertiary, fontSize = 13.sp) },
                        singleLine = true,
                        colors = preferenceFieldColors(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                HorizontalDivider(color = OutlineSubtle, thickness = 0.5.dp)

                // Model ID
                PreferenceInputRow(
                    label = "Идентификатор модели",
                    description = "Название модели для передачи в запросе (model ID)"
                ) {
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("llama-3.3-70b-versatile, gpt-4o-mini...", color = TextTertiary, fontSize = 13.sp) },
                        singleLine = true,
                        colors = preferenceFieldColors(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                HorizontalDivider(color = OutlineSubtle, thickness = 0.5.dp)

                // API Key
                PreferenceInputRow(
                    label = "API Key",
                    description = "Секретный ключ для авторизации (хранится локально)"
                ) {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("sk-... или gsk_...", color = TextTertiary, fontSize = 13.sp) },
                        singleLine = true,
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    imageVector = if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = "Видимость",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        colors = preferenceFieldColors(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                HorizontalDivider(color = OutlineSubtle, thickness = 0.5.dp)

                // Save button
                Box(modifier = Modifier.padding(14.dp)) {
                    Button(
                        onClick = {
                            viewModel.saveSettings(baseUrlInput, apiKeyInput, modelInput)
                            Toast.makeText(context, "Настройки сохранены", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .bounceClick {
                                viewModel.saveSettings(baseUrlInput, apiKeyInput, modelInput)
                                Toast.makeText(context, "Настройки сохранены", Toast.LENGTH_SHORT).show()
                            },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Сохранить параметры",
                            color = SurfaceDark,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Group 2: System Permissions
            PreferenceGroup(title = "СИСТЕМНЫЕ ДОСТУПЫ") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openStorageSettings(context) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderShared,
                        contentDescription = null,
                        tint = if (hasStoragePermission) StatusSuccess else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Управление файлами",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Право MANAGE_EXTERNAL_STORAGE для анализа и сортировки",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Switch(
                        checked = hasStoragePermission,
                        onCheckedChange = { openStorageSettings(context) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceDark,
                            checkedTrackColor = AccentPrimary,
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = SurfaceContainerHigh
                        )
                    )
                }
            }

            // Group 3: About
            PreferenceGroup(title = "О ПРИЛОЖЕНИИ") {
                PreferenceStaticRow(label = "Архитектура", value = "Kotlin + Jetpack Compose")
                HorizontalDivider(color = OutlineSubtle, thickness = 0.5.dp)
                PreferenceStaticRow(label = "Протокол", value = "OpenAI Tool Calling")
                HorizontalDivider(color = OutlineSubtle, thickness = 0.5.dp)
                PreferenceStaticRow(label = "Архиватор", value = "Zip4j 2.11.5")
                HorizontalDivider(color = OutlineSubtle, thickness = 0.5.dp)
                PreferenceStaticRow(label = "Версия", value = "1.1.0")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PreferenceGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp, bottom = 6.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
            border = BorderStroke(1.dp, OutlineSubtle),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
private fun PreferenceInputRow(
    label: String,
    description: String,
    inputContent: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(14.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))
        inputContent()
    }
}

@Composable
private fun PreferenceStaticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary
        )
    }
}

@Composable
private fun preferenceFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentPrimary,
    unfocusedBorderColor = OutlineSubtle,
    focusedContainerColor = SurfaceContainer,
    unfocusedContainerColor = SurfaceContainer,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)

fun openStorageSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            context.startActivity(intent)
        }
    } else {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}
