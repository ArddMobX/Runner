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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.data.db.SessionEntity
import com.runner.app.ui.theme.AccentPrimary
import com.runner.app.ui.theme.MotionTokens
import com.runner.app.ui.theme.OutlineSubtle
import com.runner.app.ui.theme.StatusError
import com.runner.app.ui.theme.SurfaceContainer
import com.runner.app.ui.theme.SurfaceContainerHigh
import com.runner.app.ui.theme.SurfaceContainerLow
import com.runner.app.ui.theme.TextPrimary
import com.runner.app.ui.theme.TextSecondary
import com.runner.app.ui.theme.TextTertiary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

/**
 * Боковое меню: новый чат, поиск, история по секциям, настройки внизу.
 * Удаление и переименование — через долгое нажатие на строку.
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
    onOpenSettings: () -> Unit
) {
    var sessionToRename by remember { mutableStateOf<SessionEntity?>(null) }
    var sessionToDelete by remember { mutableStateOf<SessionEntity?>(null) }

    val zone = remember { ZoneId.systemDefault() }
    val grouped = remember(sessions, zone) {
        sessions.groupBy { groupOf(it.updatedAt, zone) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerLow)
    ) {
        Text(
            text = "Runner",
            color = TextPrimary,
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            placeholder = { Text("Поиск по чатам", color = TextTertiary, fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SurfaceContainer,
                unfocusedContainerColor = SurfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = AccentPrimary,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        HorizontalDivider(color = OutlineSubtle, thickness = 0.5.dp)

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (query.isBlank()) "Чатов пока нет" else "Ничего не найдено",
                    color = TextTertiary,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                SessionGroup.values().forEach { group ->
                    val groupSessions = grouped[group].orEmpty()
                    if (groupSessions.isEmpty()) return@forEach

                    item(key = "header_${group.name}") {
                        Text(
                            text = group.title,
                            color = TextTertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)
                        )
                    }

                    items(groupSessions, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            isCurrent = session.id == currentSessionId,
                            onOpen = { onOpenSession(session.id) },
                            onRename = { sessionToRename = session },
                            onDelete = { sessionToDelete = session }
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = OutlineSubtle, thickness = 0.5.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenSettings() }
                .padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Настройки", color = TextPrimary, fontSize = 14.sp)
        }
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
            containerColor = SurfaceContainerHigh,
            title = { Text("Удалить чат?", color = TextPrimary, fontSize = 16.sp) },
            text = {
                Text(
                    text = "«${session.title.ifBlank { "Без названия" }}» и вся его история будут удалены.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSession(session.id)
                    sessionToDelete = null
                }) {
                    Text("Удалить", color = StatusError, fontSize = 14.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Отмена", color = TextSecondary, fontSize = 14.sp)
                }
            }
        )
    }
}

/** Монолитная кнопка с тонким контуром и мягким затуханием при нажатии. */
@Composable
private fun NewChatButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = if (pressed) SurfaceContainerHigh else Color.Transparent,
        animationSpec = MotionTokens.fluidTween(180),
        label = "new_chat_press"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(BorderStroke(1.dp, OutlineSubtle), RoundedCornerShape(10.dp))
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
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Новый чат",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Строка истории: акцентная черта слева у активного, действия — по долгому нажатию. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: SessionEntity,
    isCurrent: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isCurrent) AccentPrimary.copy(alpha = 0.07f) else Color.Transparent
                )
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { menuExpanded = true }
                )
                .padding(end = 16.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Акцентная черта текущего чата
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (isCurrent) AccentPrimary else Color.Transparent)
            )

            Spacer(modifier = Modifier.width(18.dp))

            Text(
                text = session.title.ifBlank { "Без названия" },
                color = if (isCurrent) TextPrimary else TextSecondary,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Переименовать", color = TextPrimary, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                onClick = {
                    menuExpanded = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text("Удалить", color = StatusError, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = StatusError,
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
        containerColor = SurfaceContainerHigh,
        title = { Text("Переименовать чат", color = TextPrimary, fontSize = 16.sp) },
        text = {
            TextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceContainer,
                    unfocusedContainerColor = SurfaceContainer,
                    focusedIndicatorColor = OutlineSubtle,
                    unfocusedIndicatorColor = OutlineSubtle,
                    cursorColor = AccentPrimary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }) {
                Text("Сохранить", color = AccentPrimary, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = TextSecondary, fontSize = 14.sp)
            }
        }
    )
}
