package com.runner.app.data

/** Что именно сделал агент с файлом или папкой. */
enum class FileChangeKind {
    CREATED,
    MODIFIED,
    DELETED,
    MOVED,
    COPIED,
    DIR_CREATED
}

/**
 * Одна файловая операция агента для карточки изменений.
 *
 * Хранится рядом с tool-сообщением и переживает перезапуск: по ней строится
 * строка шага («Создан script.py · 42 строки») и итоговая плашка хода.
 */
data class FileChange(
    val kind: FileChangeKind,
    val path: String,
    /** Сколько файлов затронуто: папка может содержать десятки. */
    val itemCount: Int = 1,
    val bytes: Long? = null,
    val linesAdded: Int = 0,
    val linesRemoved: Int = 0,
    /** Готовый diff для шторки. Null, если сравнивать было нечего. */
    val diff: String? = null
) {
    /** Путь без общего префикса памяти: карточка не должна начинаться с /storage/... */
    val displayPath: String
        get() = path
            .removePrefix("/storage/emulated/0/")
            .removePrefix("/sdcard/")
            .ifBlank { path }
}
