package com.runner.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.runner.app.data.db.SessionEntity
import com.runner.app.ui.theme.RobotoMono
import com.runner.app.ui.theme.RunnerShapes
import com.runner.app.ui.theme.TypeScale
import com.runner.app.ui.theme.SecondaryMint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.runner.app.ui.components.RunnerIcons

/** Секции истории. Заголовки неброские — они разделяют, а не кричат. */
private enum class SessionGroup(val title: String) {
    TODAY("Сегодня"),
    YESTERDAY("Вчера"),
    LAST_WEEK("Предыдущие 7 дней"),
    OLDER("Ранее")
}

private fun groupOf(timestamp: Long, zone: ZoneId): SessionGroup {
    val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when {
        date == today -> SessionGroup.TODAY
        date == today.minusDays(1) -> SessionGroup.YESTERDAY
        date.isAfter(today.minusDays(8)) -> SessionGroup.LAST_WEEK
        else -> SessionGroup.OLDER
    }
}

private val ruLocale = Locale.forLanguageTag("ru-RU")

/**
 * Компактная метка времени для строки истории: сегодня — «14:32»,
 * вчера — «Вчера», в пределах недели — день («пн»), в этом году — «5 мая»,
 * старше — «12.03.24».
 */
private fun formatSessionTime(timestamp: Long, zone: ZoneId): String {
    val zoned = Instant.ofEpochMilli(timestamp).atZone(zone)
    val date = zoned.toLocalDate()
    val today = LocalDate.now(zone)
    return when {
        date == today ->
            DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(Instant.ofEpochMilli(timestamp))
        date == today.minusDays(1) -> "Вчера"
        date.isAfter(today.minusDays(8)) ->
            DateTimeFormatter.ofPattern("EEE", ruLocale).withZone(zone).format(Instant.ofEpochMilli(timestamp))
        date.year == today.year ->
            DateTimeFormatter.ofPattern("d MMM", ruLocale).withZone(zone).format(Instant.ofEpochMilli(timestamp))
        else ->
            DateTimeFormatter.ofPattern("dd.MM.yy").withZone(zone).format(Instant.ofEpochMilli(timestamp))
    }
}

/**
 * Боковое меню: новый чат, поиск, закреплённые, история по секциям, настройки внизу.
 * Удаление, переименование и закрепление — через долгое нажатие на строку.
 */
@Composable
fun AppDrawerContent(
    sessions: List<SessionEntity>,
    currentSessionId: String?,
    query: String,
    /** id сессии → строка, в которой нашлось совпадение (поиск идёт и по тексту). */
    snippets: Map<String, String> = emptyMap(),
    onQueryChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onTogglePinSession: (String, Boolean) -> Unit,
    appVersion: String,
    footerLabel: String,
    onOpenSupport: () -> Unit = {},
    onOpenSettings: () -> Unit
) {
    var sessionToRename by remember { mutableStateOf<SessionEntity?>(null) }
    var sessionToDelete by remember { mutableStateOf<SessionEntity?>(null) }

    val zone = remember { ZoneId.systemDefault() }
    val pinnedSessions = remember(sessions) { sessions.filter { it.isPinned } }
    val restSessions = remember(sessions) { sessions.filterNot { it.isPinned } }
    val grouped = remember(restSessions, zone) {
        restSessions.groupBy { groupOf(it.updatedAt, zone) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Text(
            text = "Runner",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = TypeScale.title,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 14.dp)
        )

        NewChatButton(onClick = onNewChat)

        SlimSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.5.dp)

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (query.isBlank()) "Чатов пока нет" else "Ничего не найдено",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = TypeScale.label
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (pinnedSessions.isNotEmpty()) {
                    item(key = "header_pinned") {
                        Text(
                            text = "Закреплённые",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = TypeScale.caption,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)
                        )
                    }

                    itemsIndexed(pinnedSessions, key = { _, it -> "pinned_${it.id}" }) { index, session ->
                        SessionRow(
                            session = session,
                            isCurrent = session.id == currentSessionId,
                            snippet = snippets[session.id],
                            timeLabel = remember(session.id, session.updatedAt) {
                                formatSessionTime(session.updatedAt, zone)
                            },
                            onOpen = { onOpenSession(session.id) },
                            onRename = { sessionToRename = session },
                            onDelete = { sessionToDelete = session },
                            onTogglePin = { onTogglePinSession(session.id, !session.isPinned) }
                        )
                        if (index < pinnedSessions.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 1.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }

                if (pinnedSessions.isNotEmpty() && grouped.values.any { it.isNotEmpty() }) {
                    item(key = "divider_pinned_groups") {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    }
                }

                SessionGroup.values().forEach { group ->
                    val groupSessions = grouped[group].orEmpty()
                    if (groupSessions.isEmpty()) return@forEach

                    item(key = "header_${group.name}") {
                        Text(
                            text = group.title.uppercase(),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                            fontSize = TypeScale.micro,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)
                        )
                    }

                    itemsIndexed(groupSessions, key = { _, it -> it.id }) { _, session ->
                        SessionRow(
                            session = session,
                            isCurrent = session.id == currentSessionId,
                            snippet = snippets[session.id],
                            timeLabel = remember(session.id, session.updatedAt) {
                                formatSessionTime(session.updatedAt, zone)
                            },
                            onOpen = { onOpenSession(session.id) },
                            onRename = { sessionToRename = session },
                            onDelete = { sessionToDelete = session },
                            onTogglePin = { onTogglePinSession(session.id, !session.isPinned) }
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            thickness = 0.5.dp
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                onClick = onOpenSupport,
                shape = RunnerShapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = RunnerIcons.Favorite,
                        contentDescription = null,
                        tint = SecondaryMint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Поддержать проект",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = TypeScale.label,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Surface(
                onClick = onOpenSettings,
                shape = RunnerShapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = RunnerIcons.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Настройки",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = TypeScale.label,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        val cleanFooter = remember(footerLabel) {
            footerLabel.replace("models/", "")
        }
        Text(
            text = buildString {
                if (appVersion.isNotBlank()) append("v$appVersion")
                if (appVersion.isNotBlank() && cleanFooter.isNotBlank()) append(" · ")
                append(cleanFooter)
            },
            color = MaterialTheme.colorScheme.outline,
            fontSize = TypeScale.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 16.dp, top = 2.dp, bottom = 14.dp)
        )
    }

    sessionToRename?.let { session ->
        RenameSessionDialog(
            session = session,
            onDismiss = { sessionToRename = null },
            onConfirm = { newTitle ->
                onRenameSession(session.id, newTitle)
                sessionToRename = null
            }
        )
    }

    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Удалить чат?", color = MaterialTheme.colorScheme.onSurface, fontSize = TypeScale.title) },
            text = {
                Text(
                    text = "«${session.title.ifBlank { "Без названия" }}» и вся его история будут удалены.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = TypeScale.label
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSession(session.id)
                    sessionToDelete = null
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error, fontSize = TypeScale.body)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = TypeScale.body)
                }
            }
        )
    }
}

/**
 * Главное целевое действие: мягкая тональная заливка акцентным цветом темы,
 * чтобы сразу считывалась primary-кнопкой и не спорила с рамкой поиска.
 */
@Composable
private fun NewChatButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RunnerShapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = RunnerIcons.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Новый чат",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = TypeScale.body,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Строка истории: скруглённый pill-фон у активного, действия — по долгому нажатию. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: SessionEntity,
    isCurrent: Boolean,
    timeLabel: String,
    snippet: String? = null,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isLiveSession = remember(session.title, session.contextJson) {
        session.title.startsWith("⚡ Live") || session.title.contains("Live") || session.contextJson.contains("\"ui_")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.5.dp)
    ) {
        val itemBg = if (isCurrent) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)
        }
        val itemBorder = if (isCurrent) {
            BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        } else {
            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RunnerShapes.medium)
                .background(itemBg)
                .border(itemBorder, RunnerShapes.medium)
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { menuExpanded = true }
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    session.isPinned -> Icon(
                        imageVector = RunnerIcons.PushPin,
                        contentDescription = "Закреплено",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    isLiveSession -> Icon(
                        imageVector = RunnerIcons.TouchApp,
                        contentDescription = "Live-сессия",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    else -> Icon(
                        imageVector = RunnerIcons.ChatBubble,
                        contentDescription = null,
                        tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                val cleanTitle = remember(session.title) {
                    if (session.title.startsWith("⚡ Live: ")) {
                        session.title.removePrefix("⚡ Live: ").trim().ifBlank { "Live-сессия" }
                    } else {
                        session.title.ifBlank { "Без названия" }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = cleanTitle,
                        color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        fontSize = TypeScale.label,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isLiveSession) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RunnerShapes.tiny,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "Live",
                                fontSize = TypeScale.micro,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                val cleanModel = remember(session.lastModel) {
                    session.lastModel?.removePrefix("models/")?.removePrefix("accounts/fireworks/models/")?.trim().orEmpty()
                }
                if (cleanModel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cleanModel,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.outline,
                        fontSize = TypeScale.micro,
                        fontFamily = RobotoMono,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (!snippet.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = snippet,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = TypeScale.micro,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = timeLabel,
                color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.75f),
                fontSize = TypeScale.micro,
                fontFamily = RobotoMono,
                maxLines = 1
            )

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = RunnerIcons.MoreVert,
                    contentDescription = "Действия с чатом",
                    tint = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.clip(RunnerShapes.large)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (session.isPinned) "Открепить" else "Закрепить",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = TypeScale.body
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = RunnerIcons.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                },
                onClick = {
                    menuExpanded = false
                    onTogglePin()
                }
            )
            DropdownMenuItem(
                text = { Text("Переименовать", color = MaterialTheme.colorScheme.onSurface, fontSize = TypeScale.body) },
                leadingIcon = {
                    Icon(
                        imageVector = RunnerIcons.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                },
                onClick = {
                    menuExpanded = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text("Удалить", color = MaterialTheme.colorScheme.error, fontSize = TypeScale.body) },
                leadingIcon = {
                    Icon(
                        imageVector = RunnerIcons.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                },
                onClick = {
                    menuExpanded = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun RenameSessionDialog(
    session: SessionEntity,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember(session.id) { mutableStateOf(session.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Переименовать чат", color = MaterialTheme.colorScheme.onSurface, fontSize = TypeScale.title) },
        text = {
            TextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RunnerShapes.medium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }) {
                Text("Сохранить", color = MaterialTheme.colorScheme.primary, fontSize = TypeScale.body)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = TypeScale.body)
            }
        }
    )
}

/**
 * Тонкая аккуратная плашка поиска чатов с мягким контуром и быстрой очисткой.
 */
@Composable
private fun SlimSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp),
        shape = RunnerShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = RunnerIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Поиск по чатам",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = TypeScale.label
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = TypeScale.label,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = RunnerIcons.Close,
                        contentDescription = "Очистить поиск",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

