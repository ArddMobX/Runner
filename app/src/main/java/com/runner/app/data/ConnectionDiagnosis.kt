package com.runner.app.data

/**
 * Человеческое объяснение ошибки проверки соединения.
 *
 * Сырой HTTP-код полезен разработчику, но новичку он ничего не говорит.
 * Здесь код превращается в короткий вывод и следующее действие.
 */
data class ConnectionDiagnosis(
    val title: String,
    val detail: String,
    val vpnSuspected: Boolean = false
) {
    companion object {
        fun of(result: ConnectionTestResult?): ConnectionDiagnosis? {
            if (result == null || result.isSuccess) return null
            val code = result.statusCode
            val message = result.message.lowercase()

            return when {
                code == 401 || "401" in result.message ->
                    ConnectionDiagnosis(
                        title = "Ключ не подошёл",
                        detail = "Проверьте, что ключ от этого провайдера и Base URL совпадает."
                    )
                code == 403 || "403" in result.message ->
                    ConnectionDiagnosis(
                        title = "Доступ ограничен",
                        detail = "Проверьте права ключа и баланс. Если провайдер блокирует регион, включите VPN.",
                        vpnSuspected = true
                    )
                code == 404 || code == 405 || "404" in result.message || "405" in result.message ->
                    ConnectionDiagnosis(
                        title = "Неверный адрес API",
                        detail = "Проверьте Base URL. Обычно он заканчивается на /v1."
                    )
                code == 429 || "429" in result.message ->
                    ConnectionDiagnosis(
                        title = "Лимит запросов",
                        detail = "Подождите немного или выберите другую модель."
                    )
                code != null && code >= 500 ->
                    ConnectionDiagnosis(
                        title = "Провайдер недоступен",
                        detail = "Сервис временно не отвечает. Повторите проверку позже."
                    )
                "таймаут" in message || "timeout" in message ->
                    ConnectionDiagnosis(
                        title = "Нет ответа",
                        detail = "Проверьте интернет и VPN. Если сеть работает, проверьте адрес API.",
                        vpnSuspected = true
                    )
                "dns" in message || "хост не найден" in message ->
                    ConnectionDiagnosis(
                        title = "Хост не найден",
                        detail = "Проверьте Base URL и подключение к интернету."
                    )
                "ssl" in message || "сертификат" in message ->
                    ConnectionDiagnosis(
                        title = "Ошибка сертификата",
                        detail = "Проверьте системное время и соединение. Возможно, сеть подменяет трафик."
                    )
                !result.isReachable ->
                    ConnectionDiagnosis(
                        title = "Сеть недоступна",
                        detail = "Проверьте интернет и VPN.",
                        vpnSuspected = true
                    )
                else ->
                    ConnectionDiagnosis(
                        title = "Провайдер ответил ошибкой",
                        detail = "Проверьте ключ, модель и адрес API."
                    )
            }
        }
    }
}
