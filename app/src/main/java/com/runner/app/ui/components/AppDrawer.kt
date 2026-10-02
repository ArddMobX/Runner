package com.runner.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.runner.app.data.db.SessionEntity
import com.runner.app.ui.theme.MotionTokens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    onQueryChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onTogglePinSession: (String, Boolean) -> Unit,
    appVersion: String,
    footerLabel: String,
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
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Text(
            text = "Runner",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 14.dp)
        )

        NewChatButton(onClick = onNewChat)

        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(10.dp)),
            placeholder = { Text("Поиск по чатам", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

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
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (pinnedSessions.isNotEmpty()) {
                    item(key = "header_pinned") {
                        Text(
                            text = "Закреплённые",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)
                        )
                    }

                    items(pinnedSessions, key = { "pinned_${it.id}" }) { session ->
                        SessionRow(
                            session = session,
                            isCurrent = session.id == currentSessionId,
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

                SessionGroup.values().forEach { group ->
                    val groupSessions = grouped[group].orEmpty()
                    if (groupSessions.isEmpty()) return@forEach

                    item(key = "header_${group.name}") {
                        Text(
                            text = group.title,
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)
                        )
                    }

                    items(groupSessions, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            isCurrent = session.id == currentSessionId,
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
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            thickness = 0.5.dp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenSettings() }
                .padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Настройки", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        }
        Text(
            text = buildString {
                if (appVersion.isNotBlank()) append("v$appVersion")
                if (appVersion.isNotBlank() && footerLabel.isNotBlank()) append(" · ")
                append(footerLabel)
            },
            color = MaterialTheme.colorScheme.outline,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, bottom = 14.dp)
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
            title = { Text("Удалить чат?", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp) },
            text = {
                Text(
                    text = "«${session.title.ifBlank { "Без названия" }}» и вся его история будут удалены.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSession(session.id)
                    sessionToDelete = null
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = if (pressed) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = MotionTokens.fluidTween(180),
        label = "new_chat_press"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Новый чат",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 14.sp,
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
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isCurrent) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    else Color.Transparent
                )
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { menuExpanded = true }
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (session.isPinned) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = session.title.ifBlank { "Без названия" },
                color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.5.sp,
                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = timeLabel,
                color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Действия с чатом",
                    tint = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (session.isPinned) "Открепить" else "Закрепить",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
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
                text = { Text("Переименовать", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
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
                text = { Text("Удалить", color = MaterialTheme.colorScheme.error, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
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
        title = { Text("Переименовать чат", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp) },
        text = {
            TextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
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
                Text("Сохранить", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        }
    )
}
