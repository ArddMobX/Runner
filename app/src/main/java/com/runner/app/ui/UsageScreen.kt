package com.runner.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.data.ProviderBalanceChecker
import com.runner.app.data.ProviderBalanceResult
import com.runner.app.data.RateLimitInfo
import com.runner.app.data.RateLimitStore
import com.runner.app.data.UsageEvent
import com.runner.app.data.UsageTotals
import com.runner.app.data.totals
import com.runner.app.ui.theme.RunnerShapes
import com.runner.app.ui.theme.Spacing
import com.runner.app.ui.theme.StatusSuccess
import com.runner.app.ui.theme.TypeScale
import com.runner.app.util.PluralUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Расход токенов и остатки лимитов. Всё считается локально, без отправки
 * статистики наружу. Баланс проверяется только у провайдеров, которые
 * реально отдают такой эндпоинт.
 */
@Composable
fun UsageSettings(viewModel: MainViewModel) {
    val events by viewModel.usageEvents.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val provider by viewModel.activeProvider.collectAsState()
    val rateLimits by RateLimitStore.byHost.collectAsState()

    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val todayEvents = remember(events, today) {
        events.filter { it.localDate(zone) == today }
    }
    val todayTotals = remember(todayEvents) { todayEvents.totals() }
    val sessionTotals = remember(events, currentSessionId) {
        if (currentSessionId == null) {
            UsageTotals()
        } else {
            events.filter { it.sessionId == currentSessionId }.totals()
        }
    }
    val providerTotals = remember(todayEvents) {
        todayEvents
            .groupBy { it.providerId to it.providerName }
            .mapValues { (_, items) -> items.totals() }
            .toList()
            .sortedByDescending { it.second.totalTokens }
    }
    val lastDays = remember(events, today) {
        (0..6).map { offset -> today.minusDays(offset.toLong()) }.map { day ->
            day to events.filter { it.localDate(zone) == day }.totals()
        }
    }
    val rateLimit = remember(provider?.baseUrl, rateLimits) {
        provider?.let { RateLimitStore.forBaseUrl(it.baseUrl) }
    }
    val supportsBalance = provider?.let { ProviderBalanceChecker.supports(it) } == true

    var balanceResult by remember { mutableStateOf<ProviderBalanceResult?>(null) }
    var isCheckingBalance by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Text(
            text = "Данные считаются только на этом устройстве. Текст сообщений и ключи " +
                    "в статистику не попадают.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = TypeScale.caption,
            lineHeight = 17.sp
        )

        UsageGroup("Сегодня") {
            Text(
                text = formatTokens(todayTotals.totalTokens),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = TypeScale.headline,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "токенов · ${PluralUtils.pluralize(todayTotals.requests, "запрос", "запроса", "запросов")}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = TypeScale.caption
            )
            Spacer(modifier = Modifier.height(10.dp))
            UsageRow("Вход", formatTokens(todayTotals.promptTokens))
            UsageRow("Выход", formatTokens(todayTotals.completionTokens))
        }

        UsageGroup("Этот чат") {
            if (currentSessionId == null || sessionTotals.requests == 0) {
                Text(
                    text = "В этом чате пока нет данных о расходе.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = TypeScale.label
                )
            } else {
                Text(
                    text = formatTokens(sessionTotals.totalTokens),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = TypeScale.headline,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "токенов · ${PluralUtils.pluralize(sessionTotals.requests, "запрос", "запроса", "запросов")}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = TypeScale.caption
                )
            }
        }

        UsageGroup("По провайдерам сегодня") {
            if (providerTotals.isEmpty()) {
                Text(
                    text = "Пока нет расходов за сегодня.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = TypeScale.label
                )
            } else {
                providerTotals.forEachIndexed { index, (providerInfo, totals) ->
                    if (index > 0) UsageDivider()
                    UsageRow(
                        label = providerInfo.second.ifBlank { providerInfo.first },
                        value = "${formatTokensShort(totals.totalTokens)} · " +
                                PluralUtils.pluralize(totals.requests, "запрос", "запроса", "запросов")
                    )
                }
            }
        }

        UsageGroup("Последние 7 дней") {
            lastDays.forEachIndexed { index, (day, totals) ->
                if (index > 0) UsageDivider()
                UsageRow(
                    label = day.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))),
                    value = if (totals.requests == 0) {
                        "нет данных"
                    } else {
                        "${formatTokensShort(totals.totalTokens)} · " +
                                PluralUtils.pluralize(totals.requests, "запрос", "запроса", "запросов")
                    }
                )
            }
        }

        UsageGroup("Лимиты") {
            if (rateLimit == null) {
                Text(
                    text = "Провайдер не сообщает остаток лимита. Это нормально для Gemini " +
                            "и части прокси. Расход токенов выше считается по факту ответов.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = TypeScale.label,
                    lineHeight = 18.sp
                )
            } else {
                RateLimitContent(rateLimit)
            }
        }

        if (supportsBalance) {
            UsageGroup("Баланс провайдера") {
                Text(
                    text = "OpenRouter и DeepSeek отдают точный баланс по ключу. " +
                            "У остальных провайдеров такого эндпоинта нет.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = TypeScale.label,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val current = provider
                        if (current != null) {
                            isCheckingBalance = true
                            balanceResult = null
                            viewModel.checkProviderBalance(current) { result ->
                                isCheckingBalance = false
                                balanceResult = result
                            }
                        }
                    },
                    enabled = !isCheckingBalance,
                    shape = RunnerShapes.medium
                ) {
                    if (isCheckingBalance) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Проверяю…", fontSize = TypeScale.label)
                    } else {
                        Text("Проверить баланс", fontSize = TypeScale.label)
                    }
                }
                balanceResult?.let { result ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = result.title,
                        color = if (result.success) StatusSuccess else MaterialTheme.colorScheme.error,
                        fontSize = TypeScale.label,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = result.detail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = TypeScale.caption,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun RateLimitContent(rateLimit: RateLimitInfo) {
    Column {
        rateLimit.remainingRequests?.let { remaining ->
            UsageRow(
                label = "Запросы",
                value = buildString {
                    append(remaining)
                    rateLimit.limitRequests?.let { append(" из $it") }
                    rateLimit.resetRequests?.let { append(" · сброс через $it") }
                }
            )
        }
        rateLimit.remainingTokens?.let { remaining ->
            if (rateLimit.remainingRequests != null) UsageDivider()
            UsageRow(
                label = "Токены",
                value = buildString {
                    append(formatTokensShort(remaining.toLong()))
                    rateLimit.limitTokens?.let { append(" из ${formatTokensShort(it.toLong())}") }
                    rateLimit.resetTokens?.let { append(" · сброс через $it") }
                }
            )
        }
        if (!rateLimit.hasAny) {
            Text(
                text = "Провайдер не сообщил остаток лимита.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = TypeScale.label
            )
        } else {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Это остаток текущего окна, а не баланс аккаунта.",
                color = MaterialTheme.colorScheme.outline,
                fontSize = TypeScale.caption
            )
        }
    }
}

@Composable
private fun UsageGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = TypeScale.caption,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.sm)
        )
        Surface(
            shape = RunnerShapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                0.6.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun UsageRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = TypeScale.label,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = TypeScale.label,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun UsageDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(vertical = 5.dp)
    )
}

private fun UsageEvent.localDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

private fun formatTokens(value: Long): String =
    String.format(Locale.US, "%,d", value).replace(',', ' ')

private fun formatTokensShort(value: Long): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}M"
    value >= 1_000 -> "${value / 1_000}K"
    else -> value.toString()
}
