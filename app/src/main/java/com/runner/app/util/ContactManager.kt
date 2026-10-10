package com.runner.app.util

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Утилита для работы с контактами и телефонными звонками устройства.
 *
 * Обеспечивает:
 * 1. Поиск контактов по имени или номеру телефона через ContactsContract (требует READ_CONTACTS).
 * 2. Прямой исходящий вызов через Intent.ACTION_CALL (требует CALL_PHONE).
 * 3. Безопасный откат на Intent.ACTION_DIAL, если разрешение на звонки не выдано.
 */
object ContactManager {

    data class ContactEntry(
        val name: String,
        val number: String,
        val typeLabel: String
    )

    sealed class CallResult {
        data class Success(val directCall: Boolean, val message: String) : CallResult()
        data class Failure(val message: String) : CallResult()
    }

    /**
     * Нормализация телефонного номера:
     * - Удаление пробелов, скобок, тире;
     * - Приведение российского 11-значного 89... к международному +79... для надежного сравнения.
     */
    fun normalizePhoneNumber(rawNumber: String): String {
        val cleaned = rawNumber.trim().replace(Regex("[^0-9+*#,]"), "")
        if (cleaned.length == 11 && cleaned.startsWith("8") && cleaned[1] == '9') {
            return "+7" + cleaned.substring(1)
        }
        return cleaned
    }

    /**
     * Поиск контактов по имени или номеру телефона в телефонной книге устройства.
     *
     * @param context Context приложения
     * @param query Строка поиска (имя контакта или фрагмент номера)
     * @param limit Максимальное количество результатов
     */
    fun searchContacts(context: Context, query: String, limit: Int = 15): List<ContactEntry> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val results = mutableListOf<ContactEntry>()
        val trimmedQuery = query.trim()

        val uri = if (trimmedQuery.isNotBlank()) {
            Uri.withAppendedPath(
                ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                Uri.encode(trimmedQuery)
            )
        } else {
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL
        )

        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

        try {
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use { c ->
                val nameIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)

                val seen = mutableSetOf<String>()

                while (c.moveToNext() && results.size < limit) {
                    val name = if (nameIndex >= 0) c.getString(nameIndex)?.trim().orEmpty() else ""
                    val rawNumber = if (numberIndex >= 0) c.getString(numberIndex)?.trim().orEmpty() else ""
                    val type = if (typeIndex >= 0) c.getInt(typeIndex) else ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
                    val label = if (labelIndex >= 0) c.getString(labelIndex) else null

                    val normalized = normalizePhoneNumber(rawNumber)
                    if (name.isBlank() || normalized.isBlank()) continue

                    val key = "${name.lowercase()}_$normalized"
                    if (seen.add(key)) {
                        val typeStr = try {
                            ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                                context.resources,
                                type,
                                label
                            ).toString()
                        } catch (_: Exception) {
                            "Телефон"
                        }
                        results.add(ContactEntry(name = name, number = rawNumber, typeLabel = typeStr))
                    }
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {}

        // Резервный поиск по LIKE, если через CONTENT_FILTER_URI ничего не найдено
        if (results.isEmpty() && trimmedQuery.isNotBlank()) {
            try {
                val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
                val selectionArgs = arrayOf("%$trimmedQuery%", "%$trimmedQuery%")
                val fallbackCursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )
                fallbackCursor?.use { c ->
                    val nameIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val typeIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                    val labelIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)

                    val seen = mutableSetOf<String>()
                    while (c.moveToNext() && results.size < limit) {
                        val name = if (nameIndex >= 0) c.getString(nameIndex)?.trim().orEmpty() else ""
                        val rawNumber = if (numberIndex >= 0) c.getString(numberIndex)?.trim().orEmpty() else ""
                        val type = if (typeIndex >= 0) c.getInt(typeIndex) else ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
                        val label = if (labelIndex >= 0) c.getString(labelIndex) else null

                        val normalized = normalizePhoneNumber(rawNumber)
                        if (name.isBlank() || normalized.isBlank()) continue

                        val key = "${name.lowercase()}_$normalized"
                        if (seen.add(key)) {
                            val typeStr = try {
                                ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                                    context.resources,
                                    type,
                                    label
                               ).toString()
                            } catch (_: Exception) {
                                "Телефон"
                            }
                            results.add(ContactEntry(name = name, number = rawNumber, typeLabel = typeStr))
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return results
    }

    /**
     * Совершает телефонный вызов.
     * При наличии разрешения CALL_PHONE начинает вызов немедленно (ACTION_CALL).
     * В противном случае открывает системную звонилку с подставленным номером (ACTION_DIAL).
     */
    fun dialOrCall(context: Context, phoneNumber: String, displayName: String? = null): CallResult {
        val cleanNumber = phoneNumber.trim().replace(Regex("[^0-9+*#,]"), "")
        if (cleanNumber.isBlank()) {
            return CallResult.Failure("Номер телефона не указан или пуст.")
        }

        val targetLabel = if (!displayName.isNullOrBlank()) "$displayName ($cleanNumber)" else cleanNumber
        val callUri = Uri.fromParts("tel", cleanNumber, null)
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCallPermission) {
            try {
                val callIntent = Intent(Intent.ACTION_CALL, callUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                return CallResult.Success(
                    directCall = true,
                    message = "Выполняется прямой вызов: $targetLabel"
                )
            } catch (_: SecurityException) {
                // Если система выбросила SecurityException, делаем fallback на ACTION_DIAL
            } catch (e: Exception) {
                return CallResult.Failure("Не удалось совершить вызов: ${e.message}")
            }
        }

        // Fallback на ACTION_DIAL
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL, callUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            return CallResult.Success(
                directCall = false,
                message = "Открыт экран набора номера для $targetLabel. Для совершения прямых звонков без открытия звонилки предоставьте разрешение «Телефон» в настройках Android."
            )
        } catch (e: ActivityNotFoundException) {
            return CallResult.Failure("На устройстве не найдено приложение для телефонных вызовов.")
        } catch (e: Exception) {
            return CallResult.Failure("Ошибка при открытии набора номера: ${e.message}")
        }
    }
}
