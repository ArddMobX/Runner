package com.runner.app.util

import java.io.File

/**
 * Создаёт RUNNER.md в рабочей папке.
 *
 * Это не анализ моделью, а быстрый детерминированный скан: структура папки,
 * найденные системы сборки и заготовка правил. Пользователь потом дописывает
 * то, что знает только он. Файл автоматически попадает в системный промпт.
 */
object ProjectInstructionsGenerator {

    const val FILE_NAME = "RUNNER.md"
    private const val MAX_STRUCTURE_ENTRIES = 24

    data class Result(
        val created: Boolean,
        val path: String,
        val message: String
    )

    fun create(projectDir: File): Result {
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return Result(
                created = false,
                path = projectDir.absolutePath,
                message = "Рабочая папка не найдена: ${projectDir.absolutePath}"
            )
        }

        val file = File(projectDir, FILE_NAME)
        if (file.exists()) {
            return Result(
                created = false,
                path = file.absolutePath,
                message = "Файл $FILE_NAME уже есть. Я его не перезаписывал."
            )
        }

        return try {
            file.writeText(buildContent(projectDir))
            Result(
                created = true,
                path = file.absolutePath,
                message = "Создан $FILE_NAME. Дополни его тем, что важно знать агенту."
            )
        } catch (e: Exception) {
            Result(
                created = false,
                path = file.absolutePath,
                message = "Не удалось создать $FILE_NAME: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    fun buildContent(projectDir: File): String {
        val entries = projectDir.listFiles()
            ?.filterNot { it.name in IGNORED_NAMES }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.take(MAX_STRUCTURE_ENTRIES)
            .orEmpty()

        val buildLines = buildList {
            val names = projectDir.list()?.toSet().orEmpty()
            when {
                "build.gradle.kts" in names || "build.gradle" in names ||
                        "settings.gradle.kts" in names -> {
                    add("- Gradle: `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`")
                }
            }
            if ("package.json" in names) {
                add("- Node.js: `npm install`, `npm test` (или `npm run build`, если есть)")
            }
            if ("pyproject.toml" in names || "requirements.txt" in names) {
                add("- Python: `pytest`")
            }
            if ("Cargo.toml" in names) {
                add("- Rust: `cargo test`")
            }
            if ("go.mod" in names) {
                add("- Go: `go test ./...`")
            }
            if ("Makefile" in names) {
                add("- Make: `make`")
            }
            if ("README.md" in names) {
                add("- Документация: `README.md`")
            }
        }

        return buildString {
            append("# Runner: инструкции проекта\n\n")
            append("## О проекте\n\n")
            append("Папка: `").append(projectDir.name.ifBlank { projectDir.absolutePath }).append("`.\n\n")

            append("## Структура\n\n")
            if (entries.isEmpty()) {
                append("- Папка пуста или недоступна.\n")
            } else {
                entries.forEach { entry ->
                    append("- `").append(entry.name)
                    if (entry.isDirectory) append("/")
                    append("`\n")
                }
            }

            append("\n## Сборка и проверка\n\n")
            if (buildLines.isEmpty()) {
                append("- Команды сборки и проверки не найдены.\n")
            } else {
                buildLines.forEach { append(it).append('\n') }
            }

            append("\n## Правила\n\n")
            append("- Следуй существующему стилю кода и структуре проекта.\n")
            append("- Перед изменением читай соседний код, не угадывай API.\n")
            append("- После изменений запускай проверки из раздела выше.\n")
            append("- Не трогай секреты, ключи и файлы вне рабочей папки.\n")
        }
    }

    private val IGNORED_NAMES = setOf(
        ".git",
        ".gradle",
        ".idea",
        ".kotlin",
        "build",
        "node_modules",
        "dist",
        "out"
    )
}
