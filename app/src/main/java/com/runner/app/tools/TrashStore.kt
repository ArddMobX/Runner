package com.runner.app.tools

import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Корзина: обратимая история файловых операций агента.
 *
 * Зачем это нужно. Агент умеет удалять и перемещать файлы, а модель ошибается.
 * Пока операции были безвозвратными, единственной защитой оставался диалог
 * подтверждения — то есть пользователь был зажат между «терпеть шторку на каждое
 * действие» и «снять её и рискнуть». Корзина убирает эту вилку: ошибку можно
 * откатить, поэтому подтверждения разрешено ослаблять.
 *
 * Запись описывает ОДНУ операцию и может быть двух видов:
 *
 * - **Удаление.** Объекты физически лежат внутри корзины
 *   (`items/<id>/<слот>/<имя>`), `storedInTrash = true`. Они занимают место,
 *   и именно они удаляются безвозвратно при очистке.
 * - **Перемещение.** Объекты стоят на новых местах, корзина их не занимает
 *   (`storedInTrash = false`). Запись хранит только пару «откуда → куда»,
 *   чтобы перемещение можно было откатить.
 *
 * Вид записи не хранится отдельным полем: он выводится из расположения объектов.
 * Так не бывает расхождения между флагом и фактическим положением файла.
 *
 * Раскладка на диске:
 *
 * ```
 * /storage/emulated/0/.runner-trash/
 *   manifest.json              — журнал операций
 *   items/<id>/<слот>/<имя>    — объекты удалённого
 * ```
 *
 * Каталог начинается с точки, поэтому не мозолит глаза в галерее и файловых
 * менеджерах. Аналитические тулы его пропускают (см. `findLargestFiles`,
 * `searchFiles`) — иначе удалённое всплывало бы в «самых тяжёлых файлах».
 */
object TrashStore {

    const val DIR_NAME = ".runner-trash"
    private const val MANIFEST = "manifest.json"
    private const val ITEMS_DIR = "items"

    /** Маркер в выводе инструмента: по нему UI находит, что можно откатить. */
    const val ID_PREFIX = "trash_id:"

    /** Маркер «эту запись уже откатили»: кнопка отмены больше не показывается. */
    const val RESTORED_PREFIX = "trash_restored:"

    /** Разбор маркера: id и, для перемещений, признак вида операции. */
    private val MARKER_REGEX = Regex("""(?m)^trash_id:\s*([A-Za-z0-9\-]+)(?:\s+kind=(\w+))?\s*$""")

    private const val KIND_MOVE = "move"

    /** Вид записи: удаление (объект в корзине) или перемещение. */
    enum class EntryKind { DELETE, MOVE }

    /**
     * Объект операции.
     *
     * [originalPath] — откуда объект взялся и куда его вернёт отмена.
     * [currentPath] — где он находится сейчас.
     * [storedInTrash] — лежит ли он внутри корзины, то есть занимает ли место.
     */
    data class TrashItem(
        val originalPath: String,
        val name: String,
        val isDirectory: Boolean,
        val sizeBytes: Long,
        val fileCount: Int,
        val currentPath: String,
        val storedInTrash: Boolean
    )

    data class TrashEntry(
        val id: String,
        val at: Long,
        val items: List<TrashItem>,
        /** Занятые корзиной байты. У перемещений — ноль: объекты стоят на новых местах. */
        val totalBytes: Long
    ) {
        val fileCount: Int get() = items.sumOf { it.fileCount }

        /** Удаление, если хотя бы один объект реально лежит в корзине. */
        val kind: EntryKind
            get() = if (items.any { it.storedInTrash }) EntryKind.DELETE else EntryKind.MOVE

        val isMove: Boolean get() = kind == EntryKind.MOVE
    }

    data class TrashResult(
        val entry: TrashEntry?,
        val movedNames: List<String>,
        val failedNames: List<String>
    )

    data class RestoreOutcome(
        val restored: Int,
        val failed: Int,
        /** Объекты, которым при возврате пришлось дать новое имя (место было занято). */
        val renamedTo: List<String>
    )

    /** Ссылка на запись, которую можно откатить, из вывода инструмента. */
    data class UndoRef(val entryId: String, val isMove: Boolean)

    /** Корень корзины. Всегда внутри общей памяти, иначе файловые тулы до него не дойдут. */
    fun root(): File = File(Environment.getExternalStorageDirectory(), DIR_NAME)

    /** Путь ведёт внутрь корзины? Нужно, чтобы не «удалить» саму корзину. */
    fun isTrashPath(file: File): Boolean {
        val rootPath = try {
            root().canonicalPath
        } catch (e: Exception) {
            root().absolutePath
        }
        val path = try {
            file.canonicalPath
        } catch (e: Exception) {
            file.absolutePath
        }
        return path == rootPath || path.startsWith("$rootPath/")
    }

    // --- Чтение состояния ---

    /** Записи журнала, новые сверху. */
    fun list(): List<TrashEntry> = readManifest().sortedByDescending { it.at }

    /** Сколько места занимает корзина. Записи о перемещениях не считаются. */
    fun totalBytes(): Long = readManifest().sumOf { it.totalBytes }

    // --- Запись операций ---

    /**
     * Переносит объекты в корзину одной записью (удаление).
     *
     * Частичный успех допускается и честно возвращается в [TrashResult]: если
     * объект занят другим приложением, остальные всё равно попадут в корзину,
     * а пользователь увидит, что именно не удалось.
     */
    fun moveToTrash(files: List<File>): TrashResult {
        val failed = files.filterNot { it.exists() }.map { it.name }.toMutableList()
        val candidates = files.filter { it.exists() }
        if (candidates.isEmpty()) {
            return TrashResult(null, emptyList(), failed)
        }

        val id = newEntryId()
        val entryRoot = File(File(root(), ITEMS_DIR), id)
        if (!entryRoot.mkdirs() && !entryRoot.isDirectory) {
            return TrashResult(null, emptyList(), failed + candidates.map { it.name })
        }

        val items = mutableListOf<TrashItem>()
        val moved = mutableListOf<String>()

        candidates.forEachIndexed { index, source ->
            val slot = File(entryRoot, index.toString())
            if (!slot.mkdirs() && !slot.isDirectory) {
                failed += source.name
                return@forEachIndexed
            }

            val destination = File(slot, source.name)
            if (!moveFile(source, destination)) {
                // Слот не понадобился: убираем, чтобы не оставлять пустых папок.
                slot.deleteRecursively()
                failed += source.name
                return@forEachIndexed
            }

            items += TrashItem(
                originalPath = source.absolutePath,
                name = source.name,
                isDirectory = destination.isDirectory,
                sizeBytes = sizeOf(destination),
                fileCount = fileCountOf(destination),
                currentPath = destination.absolutePath,
                storedInTrash = true
            )
            moved += source.name
        }

        if (items.isEmpty()) {
            entryRoot.deleteRecursively()
            return TrashResult(null, emptyList(), failed)
        }

        val entry = TrashEntry(
            id = id,
            at = System.currentTimeMillis(),
            items = items,
            totalBytes = items.sumOf { it.sizeBytes }
        )

        val manifest = readManifest()
        manifest.add(0, entry)
        if (!writeManifest(manifest)) {
            // Журнал не записался — возвращаем объекты на исходные места.
            // Оставить их в items/ без записи нельзя: подметание сирот сочло бы
            // их мусором, и данные пропали бы молча. Лучше честно провалить
            // удаление, чем сделать вид, что оно прошло.
            entry.items.forEach { item ->
                val payload = File(item.currentPath)
                if (payload.exists()) moveFile(payload, File(item.originalPath))
            }
            File(File(root(), ITEMS_DIR), id).deleteRecursively()
            return TrashResult(null, emptyList(), failed + candidates.map { it.name })
        }

        return TrashResult(entry, moved, failed)
    }

    /**
     * Записывает уже выполненные перемещения, чтобы их можно было откатить.
     *
     * Диск не трогает: файлы уже на новых местах, здесь только журнал.
     * Поэтому при неудачной записи терять нечего — объекты остаются там, куда
     * их положили, и метод просто возвращает null.
     *
     * [pairs] — пары «исходное расположение → новое расположение».
     */
    fun recordMove(pairs: List<Pair<File, File>>): TrashEntry? {
        // Пишем только то, что действительно уехало: если перемещение не удалось,
        // откатывать нечего, а запись с недостоверным «куда» только запутала бы.
        val landed = pairs.filter { (source, destination) ->
            destination.exists() && !source.exists()
        }
        if (landed.isEmpty()) return null

        val items = landed.map { (source, destination) ->
            TrashItem(
                originalPath = source.absolutePath,
                name = destination.name,
                isDirectory = destination.isDirectory,
                // Размер и число файлов у перемещения не считаем: обход большого
                // каталога ради справочной цифры в журнале — лишняя работа.
                // Место корзины эти объекты всё равно не занимают.
                sizeBytes = if (destination.isFile) destination.length() else 0L,
                fileCount = if (destination.isFile) 1 else 0,
                currentPath = destination.absolutePath,
                storedInTrash = false
            )
        }

        val entry = TrashEntry(
            id = newEntryId(),
            at = System.currentTimeMillis(),
            items = items,
            totalBytes = 0L
        )

        val manifest = readManifest()
        manifest.add(0, entry)
        if (!writeManifest(manifest)) return null
        return entry
    }

    // --- Откат ---

    /** Возвращает объекты записи на исходные места. */
    fun restore(entryId: String): RestoreOutcome {
        val manifest = readManifest()
        val entry = manifest.firstOrNull { it.id == entryId }
            ?: return RestoreOutcome(0, 0, emptyList())

        var restored = 0
        var failed = 0
        val renamedTo = mutableListOf<String>()
        val leftovers = mutableListOf<TrashItem>()

        entry.items.forEach { item ->
            val current = File(item.currentPath)
            if (!current.exists()) {
                failed++
                return@forEach
            }

            val original = File(item.originalPath)
            original.parentFile?.mkdirs()

            // Место могло быть занято, пока объект лежал в корзине или стоял
            // на новом месте: молча перезаписывать чужой файл нельзя, поэтому
            // даём новое имя.
            val target = if (original.exists()) nonConflicting(original) else original
            if (target != original) renamedTo += target.absolutePath

            if (moveFile(current, target)) {
                restored++
            } else {
                failed++
                leftovers += item
            }
        }

        // Запись убираем только когда в ней ничего не осталось; иначе она
        // продолжает висеть в журнале, и объект всё ещё можно вернуть.
        val rest = manifest.mapNotNull { candidate ->
            when {
                candidate.id != entryId -> candidate
                leftovers.isEmpty() -> null
                else -> candidate.copy(
                    items = leftovers,
                    totalBytes = leftovers.filter { it.storedInTrash }.sumOf { it.sizeBytes }
                )
            }
        }
        if (leftovers.isEmpty()) {
            File(File(root(), ITEMS_DIR), entryId).deleteRecursively()
        }
        writeManifest(rest)

        return RestoreOutcome(restored, failed, renamedTo)
    }

    /** Откатывает все записи журнала. Возвращает число возвращённых объектов. */
    fun restoreAll(): RestoreOutcome {
        var restored = 0
        var failed = 0
        val renamedTo = mutableListOf<String>()
        list().forEach { entry ->
            val outcome = restore(entry.id)
            restored += outcome.restored
            failed += outcome.failed
            renamedTo += outcome.renamedTo
        }
        return RestoreOutcome(restored, failed, renamedTo)
    }

    // --- Очистка ---

    /**
     * Удаляет удалённое безвозвратно и стирает журнал целиком.
     *
     * Объекты перемещений не трогаются: они стоят на своих новых местах, и это
     * не мусор. Теряется только возможность откатить эти перемещения.
     * Возвращает освобождённые байты.
     */
    fun empty(): Long {
        val freed = totalBytes()
        File(root(), ITEMS_DIR).deleteRecursively()
        File(root(), MANIFEST).delete()
        File(root(), "$MANIFEST.tmp").delete()
        return freed
    }

    /**
     * Убирает записи старше [retentionDays] и подметает потерянные объекты.
     * Возвращает число убранных записей.
     *
     * У просроченного удаления объекты стираются с диска, у просроченного
     * перемещения — только запись: сам файл остаётся там, куда его положили.
     */
    fun purgeExpired(retentionDays: Int): Int {
        sweepOrphans()
        if (retentionDays <= 0) return 0

        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L
        val manifest = readManifest()
        val expired = manifest.filter { it.at in 1L until cutoff }
        if (expired.isEmpty()) return 0

        expired.forEach { entry ->
            entry.items
                .filter { it.storedInTrash }
                .forEach { item -> File(item.currentPath).deleteRecursively() }
            File(File(root(), ITEMS_DIR), entry.id).deleteRecursively()
        }
        val expiredIds = expired.map { it.id }.toSet()
        writeManifest(manifest.filterNot { it.id in expiredIds })
        return expired.size
    }

    /**
     * Убирает ПУСТЫЕ каталоги в `items/`, которых нет в журнале.
     *
     * Непустые сироты не удаляем сознательно. Осиротеть каталог может, если
     * журнал потеряли (сброс данных приложения, ручное удаление manifest.json),
     * а имя файла внутри сохранилось — то есть данные ещё можно вытащить руками
     * через файловый менеджер. Молча стирать их ради экономии места нельзя:
     * весь смысл корзины в том, чтобы данные не пропадали.
     *
     * Нормальный путь появления сирот закрыт: при неудавшейся записи журнала
     * [moveToTrash] возвращает объекты на место сам.
     */
    fun sweepOrphans(): Int {
        val itemsDir = File(root(), ITEMS_DIR)
        val dirs = itemsDir.listFiles()?.filter { it.isDirectory } ?: return 0
        val known = readManifest().map { it.id }.toSet()
        var removed = 0
        dirs.forEach { dir ->
            if (dir.name in known) return@forEach
            val leftovers = dir.walkTopDown().count { it.isFile }
            if (leftovers == 0) {
                dir.deleteRecursively()
                removed++
            }
        }
        return removed
    }

    // --- Маркеры в выводе инструмента ---

    fun markerLine(entryId: String, isMove: Boolean): String =
        if (isMove) "$ID_PREFIX $entryId kind=$KIND_MOVE" else "$ID_PREFIX $entryId"

    /** Запись, которую можно откатить, если маркер есть в выводе инструмента. */
    fun undoRefFromOutput(output: String?): UndoRef? {
        if (output.isNullOrBlank()) return null
        val match = MARKER_REGEX.find(output) ?: return null
        val id = match.groupValues[1]
        if (id.isEmpty()) return null
        return UndoRef(entryId = id, isMove = match.groupValues[2] == KIND_MOVE)
    }

    /** Только id записи. Нужен там, где вид операции неважен. */
    fun trashIdFromOutput(output: String?): String? = undoRefFromOutput(output)?.entryId

    /** Помечает запись откаченной: кнопка «Отменить» гаснет. */
    fun markRestored(output: String, entryId: String): String {
        // Только буквы, цифры и дефис: id генерируем мы, но подставлять
        // произвольную строку в регулярное выражение всё равно нельзя.
        val safe = entryId.filter { it.isLetterOrDigit() || it == '-' }
        if (safe.isEmpty()) return output
        return Regex("(?m)^trash_id:\\s*$safe.*$")
            .replace(output, "$RESTORED_PREFIX $safe")
    }

    /** Дата и время записи для списка журнала. */
    fun formatDate(millis: Long): String = try {
        SimpleDateFormat("d MMMM, HH:mm", Locale("ru")).format(Date(millis))
    } catch (e: Exception) {
        ""
    }

    // --- Внутреннее ---

    private fun newEntryId(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        // Случайный хвост нужен против совпадения в одну секунду: две операции
        // подряд дали бы одинаковый id, и вторая запись затёрла бы первую.
        val suffix = UUID.randomUUID().toString().substring(0, 6)
        return "$stamp-$suffix"
    }

    /** Сначала rename (быстро и атомарно в пределах тома), иначе копирование. */
    private fun moveFile(source: File, destination: File): Boolean {
        if (source.renameTo(destination)) return true
        return try {
            if (source.isDirectory) {
                if (!copyDirectory(source, destination)) return false
            } else {
                destination.parentFile?.mkdirs()
                source.inputStream().use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
            }
            source.deleteRecursively()
        } catch (e: Exception) {
            destination.deleteRecursively()
            false
        }
    }

    private fun copyDirectory(source: File, destination: File): Boolean {
        if (!destination.mkdirs() && !destination.isDirectory) return false
        val children = source.listFiles() ?: return false
        for (child in children) {
            val target = File(destination, child.name)
            if (child.isDirectory) {
                if (!copyDirectory(child, target)) return false
            } else {
                target.parentFile?.mkdirs()
                child.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        return true
    }

    /**
     * Свободное имя рядом с занятым. Суффикс латиницей: файл попадает в общую
     * память, а по ней потом ходит `run_shell_command`.
     */
    private fun nonConflicting(target: File): File {
        val parent = target.parentFile ?: return target
        val name = target.name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""

        var counter = 1
        var candidate = File(parent, "${base}_restored$extension")
        while (candidate.exists()) {
            candidate = File(parent, "${base}_restored_$counter$extension")
            counter++
        }
        return candidate
    }

    private fun sizeOf(file: File): Long = when {
        file.isFile -> file.length()
        file.isDirectory -> file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        else -> 0L
    }

    private fun fileCountOf(file: File): Int = when {
        file.isFile -> 1
        file.isDirectory -> file.walkTopDown().count { it.isFile }
        else -> 0
    }

    private fun readManifest(): MutableList<TrashEntry> {
        val file = File(root(), MANIFEST)
        if (!file.exists()) return mutableListOf<TrashEntry>()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull entryLoop@{ index ->
                val obj = array.optJSONObject(index) ?: return@entryLoop null
                val id = obj.optString("id", "").trim()
                if (id.isEmpty()) return@entryLoop null

                val rawItems = obj.optJSONArray("items") ?: JSONArray()
                val items = (0 until rawItems.length()).mapNotNull itemLoop@{ itemIndex ->
                    val itemObj = rawItems.optJSONObject(itemIndex) ?: return@itemLoop null
                    val originalPath = itemObj.optString("originalPath", "").trim()
                    if (originalPath.isEmpty()) return@itemLoop null

                    // currentPath появился, когда журнал научился хранить ещё и
                    // перемещения. Старые записи (и сборки, где поле называлось
                    // payloadPath) читаем по прежнему виду, чтобы уже лежащая
                    // корзина не осиротела после обновления.
                    val legacyPayload = itemObj.optString("payloadPath", "").trim()
                    val stored = itemObj.optBoolean("storedInTrash", legacyPayload.isNotEmpty())
                    val currentPath = itemObj.optString("currentPath", "").trim().ifEmpty {
                        if (legacyPayload.isEmpty()) "" else File(root(), legacyPayload).absolutePath
                    }
                    if (currentPath.isEmpty()) return@itemLoop null

                    TrashItem(
                        originalPath = originalPath,
                        name = itemObj.optString("name", File(originalPath).name),
                        isDirectory = itemObj.optBoolean("isDirectory", false),
                        sizeBytes = itemObj.optLong("sizeBytes", 0L),
                        fileCount = itemObj.optInt("fileCount", 1),
                        currentPath = currentPath,
                        storedInTrash = stored
                    )
                }
                if (items.isEmpty()) return@entryLoop null

                TrashEntry(
                    id = id,
                    // Раньше метка времени называлась deletedAt: запись тогда
                    // описывала только удаление.
                    at = obj.optLong("at", obj.optLong("deletedAt", 0L)),
                    items = items,
                    totalBytes = obj.optLong("totalBytes", 0L)
                )
            }.toMutableList()
        } catch (e: Exception) {
            // Битого журнала быть не должно, но падать из-за него нельзя:
            // пустой список означает «откатывать нечего», и это безопасно.
            mutableListOf<TrashEntry>()
        }
    }

    private fun writeManifest(entries: List<TrashEntry>): Boolean {
        val dir = root()
        if (!dir.exists() && !dir.mkdirs()) return false

        val array = JSONArray()
        entries.forEach { entry ->
            val items = JSONArray()
            entry.items.forEach { item ->
                items.put(
                    JSONObject().apply {
                        put("originalPath", item.originalPath)
                        put("name", item.name)
                        put("isDirectory", item.isDirectory)
                        put("sizeBytes", item.sizeBytes)
                        put("fileCount", item.fileCount)
                        put("currentPath", item.currentPath)
                        put("storedInTrash", item.storedInTrash)
                    }
                )
            }
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("at", entry.at)
                    put("totalBytes", entry.totalBytes)
                    put("items", items)
                }
            )
        }

        // Пишем через временный файл: обрыв записи не должен оставить
        // журнал в состоянии, из которого нельзя прочитать корзину.
        return try {
            val tmp = File(dir, "$MANIFEST.tmp")
            tmp.writeText(array.toString())
            val target = File(dir, MANIFEST)
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        } catch (e: Exception) {
            false
        }
    }
}
