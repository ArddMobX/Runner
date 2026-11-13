package com.runner.app.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runner.app.data.db.CustomCommandEntity
import com.runner.app.ui.commands.CustomCommandValidation
import com.runner.app.ui.components.RunnerIcons
import kotlinx.coroutines.launch

/**
 * Экран своих команд.
 *
 * У команды есть имя и шаблон промпта. В поле ввода она запускается через /,
 * а текст после имени подставляется вместо {{args}}.
 */
@Composable
fun CustomCommandsScreen(viewModel: MainViewModel) {
    val commands by viewModel.customCommands.collectAsState()
    val context = LocalContext.current
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CustomCommandEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<CustomCommandEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Шаблоны промптов, которые запускаются через / в поле ввода.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.5.sp,
            lineHeight = 18.sp
        )
        Text(
            text = "В промпте можно использовать {{args}}: сюда подставится текст после имени команды.",
            color = MaterialTheme.colorScheme.outline,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        )

        OutlinedButton(
            onClick = {
                editing = null
                showEditor = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Icon(
                imageVector = RunnerIcons.Add,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text("Добавить команду")
        }

        if (commands.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    0.7.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = RunnerIcons.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Пока нет своих команд",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Например, /review для проверки кода или /test для запуска тестов.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            commands.forEach { command ->
                CustomCommandCard(
                    command = command,
                    onEdit = {
                        editing = command
                        showEditor = true
                    },
                    onDelete = { deleteTarget = command }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showEditor) {
        CustomCommandEditorSheet(
            command = editing,
            onDismiss = {
                showEditor = false
                editing = null
            },
            onSave = { name, description, prompt, argsHint ->
                viewModel.saveCustomCommand(
                    id = editing?.id,
                    name = name,
                    description = description,
                    prompt = prompt,
                    argsHint = argsHint
                )
            },
            onSaved = {
                showEditor = false
                editing = null
                Toast.makeText(context, "Команда сохранена", Toast.LENGTH_SHORT).show()
            }
        )
    }

    deleteTarget?.let { command ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить /${command.name}?") },
            text = {
                Text("Команда пропадёт из палитры. История чатов не изменится.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCustomCommand(command.id)
                        deleteTarget = null
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun CustomCommandCard(
    command: CustomCommandEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            0.7.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 13.dp, end = 5.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "/${command.name}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (command.argsHint.isNotBlank()) {
                        Text(
                            text = " ${command.argsHint}",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                if (command.description.isNotBlank()) {
                    Text(
                        text = command.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = RunnerIcons.Edit,
                    contentDescription = "Изменить",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = RunnerIcons.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomCommandEditorSheet(
    command: CustomCommandEntity?,
    onDismiss: () -> Unit,
    onSave: suspend (name: String, description: String, prompt: String, argsHint: String) -> String?,
    onSaved: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var name by remember(command?.id) { mutableStateOf(command?.name.orEmpty()) }
    var description by remember(command?.id) {
        mutableStateOf(command?.description.orEmpty())
    }
    var prompt by remember(command?.id) { mutableStateOf(command?.prompt.orEmpty()) }
    var argsHint by remember(command?.id) { mutableStateOf(command?.argsHint.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = if (command == null) "Новая команда" else "Команда /${command.name}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Имя без слэша. В промпте {{args}} заменится текстом после команды.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(CustomCommandValidation.MAX_NAME_LENGTH)
                    error = null
                },
                label = { Text("Имя") },
                placeholder = { Text("review") },
                supportingText = { Text("Латиница, цифры, _ и -. До ${CustomCommandValidation.MAX_NAME_LENGTH} символов.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it.take(CustomCommandValidation.MAX_DESCRIPTION_LENGTH)
                    error = null
                },
                label = { Text("Описание") },
                placeholder = { Text("Проверить код на ошибки") },
                supportingText = { Text("Показывается в палитре и справке.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = argsHint,
                onValueChange = {
                    argsHint = it.take(CustomCommandValidation.MAX_ARGS_HINT_LENGTH)
                    error = null
                },
                label = { Text("Подсказка аргументов") },
                placeholder = { Text("файл или папка") },
                supportingText = { Text("Необязательно. Показывается после имени команды.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = prompt,
                onValueChange = {
                    prompt = it.take(CustomCommandValidation.MAX_PROMPT_LENGTH)
                    error = null
                },
                label = { Text("Промпт") },
                placeholder = { Text("Проверь код в {{args}} и найди ошибки...") },
                supportingText = {
                    Text(
                        "{{args}} заменится текстом после команды. " +
                                "Если плейсхолдера нет, аргументы добавятся в конец."
                    )
                },
                minLines = 5,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )

            error?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !saving
                ) {
                    Text("Отмена")
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            error = null
                            val result = onSave(name, description, prompt, argsHint)
                            saving = false
                            if (result == null) {
                                onSaved()
                            } else {
                                error = result
                            }
                        }
                    },
                    enabled = !saving && name.isNotBlank() && prompt.isNotBlank()
                ) {
                    Text(if (saving) "Сохраняю..." else "Сохранить")
                }
            }
        }
    }
}
