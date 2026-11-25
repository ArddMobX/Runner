package com.runner.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.data.ModelAccess
import com.runner.app.data.ModelCatalog
import com.runner.app.data.ModelInfo
import com.runner.app.data.ModelRecommendations
import com.runner.app.data.Provider
import com.runner.app.data.ProviderStore
import com.runner.app.ui.components.BrandLogoOrMonogram
import com.runner.app.ui.components.ProviderLogos
import com.runner.app.ui.components.RunnerIcons
import com.runner.app.ui.components.openExternalUrl
import com.runner.app.ui.theme.GoogleSansDisplay
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.RunnerShapes
import com.runner.app.ui.theme.SecondaryMint
import com.runner.app.ui.theme.Spacing
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.ui.theme.StatusWarning
import com.runner.app.ui.theme.TypeScale

private enum class OnboardingStep(val index: Int) {
    WELCOME(0),
    PROVIDER(1),
    KEY(2),
    MODEL(3);

    fun previous(): OnboardingStep = when (this) {
        WELCOME -> WELCOME
        PROVIDER -> WELCOME
        KEY -> PROVIDER
        MODEL -> KEY
    }
}

private data class OnboardingProviderOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String? = null,
    val keyUrl: String? = null,
    val icon: ImageVector,
    val custom: Boolean = false
)

/**
 * Мастер первого запуска. Показывается только когда в приложении ещё нет
 * ни одного ключа. Ведёт от выбора провайдера до первой рабочей модели.
 */
@Composable
fun OnboardingWizard(
    viewModel: MainViewModel,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val providers by viewModel.providers.collectAsState()
    val providerOptions = listOf(
        OnboardingProviderOption(
            id = "openrouter",
            title = "OpenRouter",
            subtitle = "Много моделей, есть бесплатные",
            badge = "Рекомендуем",
            keyUrl = "https://openrouter.ai/keys",
            icon = ProviderLogos.OpenRouter
        ),
        OnboardingProviderOption(
            id = "gemini",
            title = "Google AI Studio",
            subtitle = "Gemini с бесплатным лимитом",
            badge = "Бесплатно",
            keyUrl = "https://aistudio.google.com/app/apikey",
            icon = ProviderLogos.Gemini
        ),
        OnboardingProviderOption(
            id = "groq",
            title = "Groq",
            subtitle = "Быстрые модели, есть бесплатный лимит",
            badge = "Бесплатно",
            keyUrl = "https://console.groq.com/keys",
            icon = ProviderLogos.Groq
        ),
        OnboardingProviderOption(
            id = "deepseek",
            title = "DeepSeek",
            subtitle = "Недорогие модели, ключ на платформе",
            keyUrl = "https://platform.deepseek.com/api_keys",
            icon = ProviderLogos.DeepSeek
        ),
        OnboardingProviderOption(
            id = "custom",
            title = "Свой провайдер",
            subtitle = "OpenAI-совместимый Base URL",
            icon = RunnerIcons.Settings,
            custom = true
        )
    )

    var step by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var providerId by remember { mutableStateOf<String?>(null) }
    var providerName by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var isCustom by remember { mutableStateOf(false) }
    var showBaseUrl by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedModel by remember { mutableStateOf("") }
    var isTestingModel by remember { mutableStateOf(false) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var requestGeneration by remember { mutableStateOf(0) }

    val currentOption = providerOptions.firstOrNull { it.id == providerId }
    val currentProvider = providers.firstOrNull { it.id == providerId }

    fun cancelPendingRequests() {
        requestGeneration++
        isChecking = false
        isTestingModel = false
    }

    fun finishWizard() {
        cancelPendingRequests()
        onDone()
    }

    fun goBack() {
        cancelPendingRequests()
        error = null
        modelError = null
        if (step == OnboardingStep.WELCOME) {
            onDone()
        } else {
            step = step.previous()
        }
    }

    fun chooseProvider(option: OnboardingProviderOption) {
        cancelPendingRequests()
        providerId = option.id
        providerName = option.title
        isCustom = option.custom
        val existing = providers.firstOrNull { it.id == option.id }
        val preset = ProviderStore.PRESETS.firstOrNull { it.id == option.id }
        baseUrl = existing?.baseUrl?.takeIf { it.isNotBlank() }
            ?: preset?.baseUrl.orEmpty()
        apiKey = ""
        error = null
        selectedModel = ""
        modelError = null
        showBaseUrl = option.custom
        step = OnboardingStep.KEY
    }

    BackHandler { goBack() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            WizardHeader(
                step = step,
                onBack = ::goBack,
                onSkip = ::finishWizard
            )

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    MotionTokens.sharedAxisXTransition(
                        forward = targetState.ordinal >= initialState.ordinal
                    )
                },
                modifier = Modifier.weight(1f),
                label = "onboarding_step"
            ) { currentStep ->
            when (currentStep) {
                OnboardingStep.WELCOME -> WizardWelcome(
                    onNext = { step = OnboardingStep.PROVIDER }
                )

                OnboardingStep.PROVIDER -> WizardProviderStep(
                    options = providerOptions,
                    onSelect = ::chooseProvider
                )

                OnboardingStep.KEY -> WizardKeyStep(
                    providerName = currentProvider?.name ?: providerName,
                    option = currentOption,
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    baseUrl = baseUrl,
                    onBaseUrlChange = { baseUrl = it },
                    showBaseUrl = showBaseUrl,
                    onToggleBaseUrl = { showBaseUrl = !showBaseUrl },
                    isCustom = isCustom,
                    isChecking = isChecking,
                    error = error,
                    onPasteKey = {
                        readClipboardText(context)?.let {
                            apiKey = it
                            error = null
                        }
                    },
                    onCheck = {
                        if (isChecking) return@WizardKeyStep
                        val id = providerId ?: return@WizardKeyStep
                        val provider = currentProvider ?: Provider(
                            id = id,
                            name = providerName.ifBlank { "Провайдер" },
                            baseUrl = baseUrl.trim()
                        )
                        val reqGen = ++requestGeneration
                        isChecking = true
                        error = null
                        viewModel.connectProviderForOnboarding(
                            provider = provider,
                            baseUrl = baseUrl,
                            apiKey = apiKey
                        ) { ok, message ->
                            if (reqGen != requestGeneration || step != OnboardingStep.KEY) {
                                return@connectProviderForOnboarding
                            }
                            isChecking = false
                            if (!ok) {
                                error = message
                            } else {
                                val updated = viewModel.providers.value
                                    .firstOrNull { it.id == id }
                                val models = updated?.models.orEmpty()
                                selectedModel = ModelRecommendations
                                    .preferred(id, models)
                                    .orEmpty()
                                    .ifBlank { updated?.selectedModel.orEmpty() }
                                modelError = null
                                step = OnboardingStep.MODEL
                            }
                        }
                    }
                )

                OnboardingStep.MODEL -> WizardModelStep(
                    provider = currentProvider,
                    selectedModel = selectedModel,
                    onSelectModel = {
                        selectedModel = it
                        modelError = null
                    },
                    isChecking = isTestingModel,
                    error = modelError,
                    onOpenChat = {
                        if (isTestingModel) return@WizardModelStep
                        val id = providerId
                        val provider = currentProvider
                        val model = selectedModel.ifBlank {
                            ModelRecommendations
                                .preferred(id.orEmpty(), provider?.models.orEmpty())
                                .orEmpty()
                        }
                        if (id != null && provider != null && model.isNotBlank()) {
                            val reqGen = ++requestGeneration
                            isTestingModel = true
                            modelError = null
                            viewModel.testOnboardingModel(provider, model) { ok, message ->
                                if (reqGen != requestGeneration || step != OnboardingStep.MODEL) {
                                    return@testOnboardingModel
                                }
                                isTestingModel = false
                                if (ok) {
                                    viewModel.selectModel(id, model)
                                    onDone()
                                } else {
                                    modelError = message
                                }
                            }
                        }
                    }
                )
            }
            }
        }
    }
}

@Composable
private fun WizardHeader(
    step: OnboardingStep,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step != OnboardingStep.WELCOME) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = RunnerIcons.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
            Text(
                text = "Runner",
                fontFamily = GoogleSansDisplay,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = TypeScale.title,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onSkip) {
                Text(
                    text = "Пропустить",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = TypeScale.label
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OnboardingStep.entries.forEach { item ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(
                            if (item.index <= step.index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun WizardWelcome(onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = "Помощник для Android",
                fontFamily = GoogleSansDisplay,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Runner работает с файлами, командами, интернетом и экраном. " +
                        "Ключ API хранится только на этом устройстве.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = TypeScale.body,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(26.dp))

            WelcomePoint(
                icon = RunnerIcons.FolderOpen,
                title = "Файлы",
                text = "Найдёт, прочитает и разложит файлы"
            )
            Spacer(modifier = Modifier.height(16.dp))
            WelcomePoint(
                icon = RunnerIcons.Terminal,
                title = "Команды",
                text = "Запустит shell, Termux и Shizuku"
            )
            Spacer(modifier = Modifier.height(16.dp))
            WelcomePoint(
                icon = RunnerIcons.Public,
                title = "Интернет",
                text = "Найдёт страницу и разберёт её содержимое"
            )
            Spacer(modifier = Modifier.height(16.dp))
            WelcomePoint(
                icon = RunnerIcons.TouchApp,
                title = "Экран",
                text = "Live-режим для действий в приложениях"
            )

            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = "Доступ к файлам попросим позже, когда он действительно понадобится. " +
                        "Сейчас нужен только ключ провайдера.",
                color = MaterialTheme.colorScheme.outline,
                fontSize = TypeScale.caption,
                lineHeight = 17.sp
            )
        }

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(50.dp),
            shape = RunnerShapes.medium
        ) {
            Text("Начать настройку", fontSize = TypeScale.body, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun WelcomePoint(
    icon: ImageVector,
    title: String,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RunnerShapes.medium)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = TypeScale.body,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = TypeScale.caption
            )
        }
    }
}

@Composable
private fun WizardProviderStep(
    options: List<OnboardingProviderOption>,
    onSelect: (OnboardingProviderOption) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Откуда взять ключ?",
            fontFamily = GoogleSansDisplay,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Выберите сервис. Адрес API подставится сам, менять его не нужно.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = TypeScale.body,
            lineHeight = 19.sp
        )
        Spacer(modifier = Modifier.height(18.dp))

        options.forEach { option ->
            ProviderOptionCard(option = option, onClick = { onSelect(option) })
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Ключ можно получить на сайте провайдера. Если не хотите настраивать сейчас, " +
                    "нажмите «Пропустить» и добавьте ключ позже в настройках.",
            color = MaterialTheme.colorScheme.outline,
            fontSize = TypeScale.caption,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun ProviderOptionCard(
    option: OnboardingProviderOption,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RunnerShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (option.badge == "Рекомендуем") {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RunnerShapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    tint = if (option.badge == "Рекомендуем") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = option.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = TypeScale.body,
                        fontWeight = FontWeight.Medium
                    )
                    if (option.badge != null) {
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = option.badge,
                            color = if (option.badge == "Рекомендуем") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            fontSize = TypeScale.micro,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Text(
                    text = option.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = TypeScale.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = RunnerIcons.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun WizardKeyStep(
    providerName: String,
    option: OnboardingProviderOption?,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    showBaseUrl: Boolean,
    onToggleBaseUrl: () -> Unit,
    isCustom: Boolean,
    isChecking: Boolean,
    error: String?,
    onPasteKey: () -> Unit,
    onCheck: () -> Unit
) {
    val context = LocalContext.current
    var keyVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Ключ $providerName",
                fontFamily = GoogleSansDisplay,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ключ хранится только на устройстве и не отправляется никуда, кроме " +
                        "самого провайдера.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = TypeScale.body,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API-ключ") },
                placeholder = { Text("Вставьте ключ") },
                singleLine = true,
                visualTransformation = if (keyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            imageVector = if (keyVisible) {
                                RunnerIcons.VisibilityOff
                            } else {
                                RunnerIcons.Visibility
                            },
                            contentDescription = if (keyVisible) "Скрыть ключ" else "Показать ключ",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                },
                shape = RunnerShapes.small
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onPasteKey) {
                    Text("Вставить из буфера", fontSize = TypeScale.label)
                }
                Spacer(modifier = Modifier.weight(1f))
                if (option?.keyUrl != null) {
                    TextButton(onClick = { openExternalUrl(context, option.keyUrl) }) {
                        Text("Где взять ключ", fontSize = TypeScale.label)
                    }
                }
            }

            if (!isCustom) {
                TextButton(onClick = onToggleBaseUrl) {
                    Text(
                        text = if (showBaseUrl) "Скрыть адрес API" else "Изменить адрес API",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = TypeScale.caption
                    )
                }
            }

            if (showBaseUrl || isCustom) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.example.com/v1") },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = TypeScale.label,
                        fontFamily = FontFamily.Monospace
                    ),
                    shape = RunnerShapes.small
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Полный адрес OpenAI-совместимого API, обычно заканчивается на /v1.",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = TypeScale.caption
                )
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = TypeScale.caption,
                    lineHeight = 17.sp
                )
            }
        }

        Button(
            onClick = onCheck,
            enabled = !isChecking && apiKey.isNotBlank() && baseUrl.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(50.dp),
            shape = RunnerShapes.medium
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Проверяю ключ…", fontSize = TypeScale.body)
            } else {
                Text("Проверить и продолжить", fontSize = TypeScale.body, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun WizardModelStep(
    provider: Provider?,
    selectedModel: String,
    onSelectModel: (String) -> Unit,
    isChecking: Boolean,
    error: String?,
    onOpenChat: () -> Unit
) {
    val models = remember(provider?.models) {
        val all = provider?.models.orEmpty()
        val toolModels = all.filter { ModelCatalog.isChatAndToolModel(it.id, it.name) }
        toolModels.ifEmpty { all }
    }
    val recommended = remember(provider?.id, models) {
        provider?.let { ModelRecommendations.preferred(it.id, it.models) }
    }
    val providerId = provider?.id.orEmpty()
    val providerName = provider?.name.orEmpty()
    val providerBaseUrl = provider?.baseUrl.orEmpty()

    var query by remember(providerId) { mutableStateOf("") }
    var showAll by remember(providerId) { mutableStateOf(false) }

    val freeModels = remember(models, providerId, recommended) {
        models
            .filter { ModelAccess.isFreeWithLimits(providerId, it.id) }
            .recommendedFirst(recommended)
    }
    val paidModels = remember(models, providerId, recommended) {
        models
            .filterNot { ModelAccess.isFreeWithLimits(providerId, it.id) }
            .recommendedFirst(recommended)
    }
    val isSearching = query.isNotBlank()
    val freeResults = if (isSearching) freeModels.filter { it.matches(query) } else freeModels
    val paidResults = if (isSearching) paidModels.filter { it.matches(query) } else paidModels
    val freeVisible = if (isSearching || showAll) freeResults else freeResults.take(FREE_PREVIEW)
    val paidVisible = if (isSearching || showAll) paidResults else paidResults.take(PAID_PREVIEW)
    val hiddenCount =
        (freeResults.size - freeVisible.size) + (paidResults.size - paidVisible.size)

    LaunchedEffect(models, recommended, selectedModel) {
        if (selectedModel.isBlank()) {
            onSelectModel(recommended ?: models.firstOrNull()?.id.orEmpty())
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = RunnerIcons.CheckCircle,
                    contentDescription = null,
                    tint = StatusSuccess,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Модели загружены",
                    color = StatusSuccess,
                    fontSize = TypeScale.body,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Выберите модель. Перед открытием чата проверим, что она отвечает " +
                        "и принимает инструменты.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = TypeScale.body,
                lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Поиск по названию или ID",
                        fontSize = TypeScale.label
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = RunnerIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { query = "" },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = RunnerIcons.Close,
                                contentDescription = "Очистить поиск",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RunnerShapes.medium,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = TypeScale.label)
            )
        }

        if (models.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Провайдер не вернул ни одной модели. Вернитесь назад и проверьте ключ.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = TypeScale.label
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (freeVisible.isNotEmpty()) {
                    item(key = "free_header") {
                        WizardModelGroupHeader(
                            title = "Бесплатные с ограничениями",
                            count = freeResults.size
                        )
                    }
                    items(freeVisible, key = { "model_${it.id}" }) { model ->
                        WizardModelRow(
                            model = model,
                            providerId = providerId,
                            providerName = providerName,
                            providerBaseUrl = providerBaseUrl,
                            isSelected = model.id == selectedModel,
                            isRecommended = model.id == recommended,
                            onSelect = { onSelectModel(model.id) }
                        )
                    }
                }

                if (paidVisible.isNotEmpty()) {
                    item(key = "paid_header") {
                        WizardModelGroupHeader(
                            title = "Платные",
                            count = paidResults.size
                        )
                    }
                    items(paidVisible, key = { "model_${it.id}" }) { model ->
                        WizardModelRow(
                            model = model,
                            providerId = providerId,
                            providerName = providerName,
                            providerBaseUrl = providerBaseUrl,
                            isSelected = model.id == selectedModel,
                            isRecommended = model.id == recommended,
                            onSelect = { onSelectModel(model.id) }
                        )
                    }
                }

                if (freeResults.isEmpty() && paidResults.isEmpty()) {
                    item(key = "empty_search") {
                        Text(
                            text = "Ничего не нашлось. Попробуйте другое название или ID.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = TypeScale.label,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        )
                    }
                }

                if (!isSearching && hiddenCount > 0) {
                    item(key = "toggle_all") {
                        TextButton(
                            onClick = { showAll = !showAll },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (showAll) {
                                    "Свернуть список"
                                } else {
                                    "Показать все ($hiddenCount)"
                                },
                                fontSize = TypeScale.label
                            )
                        }
                    }
                }

                if (!isSearching && showAll && hiddenCount == 0) {
                    item(key = "collapse_all") {
                        TextButton(
                            onClick = { showAll = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Свернуть список", fontSize = TypeScale.label)
                        }
                    }
                }
            }
        }

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = TypeScale.caption,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        Button(
            onClick = onOpenChat,
            enabled = selectedModel.isNotBlank() && !isChecking,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(50.dp),
            shape = RunnerShapes.medium
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Проверяю модель…", fontSize = TypeScale.body)
            } else {
                Text("Проверить и открыть", fontSize = TypeScale.body, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private const val FREE_PREVIEW = 3
private const val PAID_PREVIEW = 4

private fun ModelInfo.matches(query: String): Boolean {
    val normalized = query.trim()
    if (normalized.isBlank()) return true
    return label.contains(normalized, ignoreCase = true) ||
        id.contains(normalized, ignoreCase = true)
}

/** Рекомендованную модель всегда держим первой в своей группе. */
private fun List<ModelInfo>.recommendedFirst(recommended: String?): List<ModelInfo> {
    if (recommended.isNullOrBlank()) return this
    val match = firstOrNull { it.id == recommended } ?: return this
    return listOf(match) + filterNot { it.id == recommended }
}

@Composable
private fun WizardModelGroupHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = TypeScale.caption,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = count.toString(),
            color = MaterialTheme.colorScheme.outline,
            fontSize = TypeScale.micro
        )
    }
}

@Composable
private fun WizardModelRow(
    model: ModelInfo,
    providerId: String,
    providerName: String,
    providerBaseUrl: String = "",
    isSelected: Boolean,
    isRecommended: Boolean,
    onSelect: () -> Unit
) {
    val modelLogo = remember(model.id, providerId, providerName, providerBaseUrl) {
        ProviderLogos.forModelOrProvider(
            modelId = model.id,
            providerId = providerId,
            providerName = providerName,
            baseUrl = providerBaseUrl
        )
    }

    Surface(
        onClick = onSelect,
        shape = RunnerShapes.medium,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModelAvatar(
                modelId = model.id,
                providerName = providerName,
                providerBaseUrl = providerBaseUrl,
                logo = modelLogo
            )
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.label,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontSize = TypeScale.body,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isRecommended || model.id != model.label) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRecommended) {
                            Box(
                                modifier = Modifier
                                    .clip(RunnerShapes.pill)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "Рекомендуем",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = TypeScale.micro,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        if (model.id != model.label) {
                            Text(
                                text = model.id,
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = TypeScale.micro,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
            }

            model.contextBadge?.let { badge ->
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = badge,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = TypeScale.micro,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        }
                    )
                    .border(
                        1.dp,
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = RunnerIcons.Check,
                        contentDescription = "Выбрана",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelAvatar(
    modelId: String,
    providerName: String,
    providerBaseUrl: String,
    logo: ImageVector?
) {
    val tint = modelAvatarColor(modelId)
    val monogram = remember(modelId, providerName, providerBaseUrl) {
        ProviderLogos.monogramFor(
            modelId = modelId,
            providerName = providerName,
            baseUrl = providerBaseUrl
        )
    }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        BrandLogoOrMonogram(
            logo = logo,
            monogram = monogram,
            tint = tint,
            iconSize = 18.dp,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun modelAvatarColor(key: String): Color {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        SecondaryMint,
        StatusSuccess,
        StatusWarning,
        Color(0xFFB39DDB),
        Color(0xFFF48FB1),
        Color(0xFF4DD0E1)
    )
    return palette[key.hashCode().mod(palette.size)]
}

private fun readClipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = manager?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0)
        .coerceToText(context)
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
