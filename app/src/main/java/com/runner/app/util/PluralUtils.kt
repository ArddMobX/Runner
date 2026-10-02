package com.runner.app.util

import kotlin.math.abs

/**
 * Утилиты склонения русских числительных:
 * 1 шаг, 2 шага, 5 шагов
 * 1 действие, 2 действия, 5 действий
 * 1 файл, 2 файла, 5 файлов
 */
object PluralUtils {

    fun pluralWord(count: Int, one: String, few: String, many: String): String {
        val n = abs(count)
        val rem100 = n % 100
        val rem10 = n % 10
        return when {
            rem100 in 11..19 -> many
            rem10 == 1 -> one
            rem10 in 2..4 -> few
            else -> many
        }
    }

    fun pluralize(count: Int, one: String, few: String, many: String): String {
        return "$count ${pluralWord(count, one, few, many)}"
    }

    fun steps(count: Int): String = pluralize(count, "шаг", "шага", "шагов")
    fun actions(count: Int): String = pluralize(count, "действие", "действия", "действий")
    fun models(count: Int): String = pluralize(count, "модель", "модели", "моделей")
    fun files(count: Int): String = pluralize(count, "файл", "файла", "файлов")
}
