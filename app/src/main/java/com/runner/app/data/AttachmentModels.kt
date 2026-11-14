package com.runner.app.data

import android.net.Uri

/**
 * Файл, который пользователь выбрал, но ещё не отправил.
 *
 * Храним имя и размер сразу: системный пикер отдаёт content:// URI, а после
 * перезапуска он может стать невалидным. При отправке файл копируется
 * во внутренний кэш и дальше живёт как обычное вложение сообщения.
 */
data class SelectedFile(
    val uri: Uri,
    val name: String,
    val mime: String,
    val sizeBytes: Long
)

/**
 * Вложение в истории чата.
 *
 * path — либо локальная копия в chat_files, либо content:// URI, если файл
 * ещё не успел скопироваться. По mime и расширению UI выбирает иконку,
 * а модель получает уже извлечённый текст.
 */
data class ChatAttachment(
    val path: String,
    val name: String,
    val mime: String = "",
    val sizeBytes: Long = 0L
) {
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()
}
