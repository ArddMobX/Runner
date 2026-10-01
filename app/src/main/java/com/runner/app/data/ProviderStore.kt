package com.runner.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Провайдер — это имя, Base URL, свой ключ и список моделей.
 * Ключ хранится только здесь (в зашифрованном виде) и никуда не логируется.
 */
data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val models: List<String> = emptyList(),
    val selectedModel: String = ""
) {
    /** Ключ задан — провайдера можно использовать. */
    val isReady: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank()

    /** Модель, которая реально уйдёт в запрос. */
    val activeModel: String get() = selectedModel.ifBlank { models.firstOrNull().orEmpty() }
}

/**
 * Хранилище провайдеров.
 *
 * Весь список (вместе с ключами) сериализуется в JSON и шифруется через [SecureStore],
 * в SharedPreferences лежит только шифротекст. Если Keystore почему-то недоступен —
 * падаем в незашифрованный резерв, чтобы приложение не потеряло настройки,
 * но это осознанный компромисс, а не норма.
 */
class ProviderStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val legacyPrefs = context.applicationContext
        .getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

    fun loadProviders(): List<Provider> {
        val blob = prefs.getString(KEY_BLOB, null)
        if (blob != null) {
            val json = SecureStore.decrypt(blob) ?: prefs.getString(KEY_BLOB_FALLBACK, null)
            if (json != null) {
                val parsed = parse(json)
                if (!parsed.isNullOrEmpty()) return parsed
            }
        }

        val migrated = migrateLegacy()
        if (migrated != null) {
            saveProviders(migrated)
            legacyPrefs.edit().clear().apply()
            return migrated
        }

        val seeds = defaultProviders()
        saveProviders(seeds)
        return seeds
    }

    fun saveProviders(providers: List<Provider>) {
        val json = serialize(providers).toString()
        val encrypted = SecureStore.encrypt(json)
        prefs.edit().apply {
            if (encrypted != null) {
                putString(KEY_BLOB, encrypted)
                remove(KEY_BLOB_FALLBACK)
            } else {
                putString(KEY_BLOB_FALLBACK, json)
                remove(KEY_BLOB)
            }
        }.apply()
    }

    fun activeProviderId(): String =
        prefs.getString(KEY_ACTIVE_ID, DEFAULT_PROVIDER_ID) ?: DEFAULT_PROVIDER_ID

    fun setActiveProviderId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun upsert(provider: Provider): List<Provider> {
        val current = loadProviders().toMutableList()
        val index = current.indexOfFirst { it.id == provider.id }
        if (index >= 0) current[index] = provider else current.add(provider)
        saveProviders(current)
        return current
    }

    fun remove(id: String): List<Provider> {
        val current = loadProviders().filterNot { it.id == id }
        val safe = current.ifEmpty { defaultProviders() }
        saveProviders(safe)
        if (activeProviderId() == id) setActiveProviderId(safe.first().id)
        return safe
    }

    /** Провайдер, выбранный для работы. Если выбранного нет — первый доступный. */
    fun activeProvider(): Provider? {
        val providers = loadProviders()
        return providers.firstOrNull { it.id == activeProviderId() } ?: providers.firstOrNull()
    }

    private fun migrateLegacy(): List<Provider>? {
        val baseUrl = legacyPrefs.getString("api_base_url", null)?.trim().orEmpty()
        val apiKey = legacyPrefs.getString("api_key", null)
            ?: legacyPrefs.getString("groq_api_key", null)
        val model = legacyPrefs.getString("api_model_name", null)?.trim().orEmpty()

        if (baseUrl.isBlank() && apiKey.isNullOrBlank()) return null

        val providers = defaultProviders().toMutableList()
        if (baseUrl.isNotBlank()) {
            val index = providers.indexOfFirst { it.baseUrl == baseUrl }
            if (index >= 0) {
                val existing = providers[index]
                providers[index] = existing.copy(
                    apiKey = apiKey.orEmpty(),
                    selectedModel = model.ifBlank { existing.selectedModel },
                    models = if (model.isNotBlank() && model !in existing.models) {
                        listOf(model) + existing.models
                    } else {
                        existing.models
                    }
                )
            } else {
                providers.add(
                    0,
                    Provider(
                        id = "custom_${System.currentTimeMillis()}",
                        name = "Свой провайдер",
                        baseUrl = baseUrl,
                        apiKey = apiKey.orEmpty(),
                        models = if (model.isNotBlank()) listOf(model) else emptyList(),
                        selectedModel = model
                    )
                )
            }
        }
        return providers
    }

    private fun serialize(providers: List<Provider>): JSONArray = JSONArray().apply {
        providers.forEach { provider ->
            put(
                JSONObject().apply {
                    put("id", provider.id)
                    put("name", provider.name)
                    put("baseUrl", provider.baseUrl)
                    put("apiKey", provider.apiKey)
                    put("models", JSONArray(provider.models))
                    put("selectedModel", provider.selectedModel)
                }
            )
        }
    }

    private fun parse(json: String): List<Provider>? = try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) return@mapNotNull null
            val modelsArray = item.optJSONArray("models")
            val models = if (modelsArray == null) {
                emptyList()
            } else {
                (0 until modelsArray.length()).mapNotNull { modelsArray.optString(it).ifBlank { null } }
            }
            Provider(
                id = id,
                name = item.optString("name").ifBlank { id },
                baseUrl = item.optString("baseUrl"),
                apiKey = item.optString("apiKey"),
                models = models,
                selectedModel = item.optString("selectedModel")
            )
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val PREFS_NAME = "runner_secure"
        private const val LEGACY_PREFS_NAME = "runner_prefs"
        private const val KEY_BLOB = "providers_blob"
        private const val KEY_BLOB_FALLBACK = "providers_blob_plain"
        private const val KEY_ACTIVE_ID = "active_provider_id"

        const val DEFAULT_PROVIDER_ID = "groq"

        /**
         * Стартовый набор. Модели здесь — только подсказка до первого
         * «Обновить список»: настоящий список тянется из GET /models.
         */
        fun defaultProviders(): List<Provider> = listOf(
            Provider(
                id = "groq",
                name = "Groq",
                baseUrl = "https://api.groq.com/openai/v1",
                models = listOf("openai/gpt-oss-120b", "openai/gpt-oss-20b", "qwen/qwen3.8-27b"),
                selectedModel = "openai/gpt-oss-120b"
            ),
            Provider(
                id = "gemini",
                name = "Gemini",
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                models = listOf("gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.5-flash"),
                selectedModel = "gemini-3.8-flash"
            ),
            Provider(
                id = "openrouter",
                name = "OpenRouter",
                baseUrl = "https://openrouter.ai/api/v1",
                models = listOf(
                    "qwen/qwen3.8-27b:free",
                    "nvidia/nemotron-3.5-lightning:free",
                    "deepseek/deepseek-v4.1-flash"
                ),
                selectedModel = "qwen/qwen3.8-27b:free"
            ),
            Provider(
                id = "deepseek",
                name = "DeepSeek",
                baseUrl = "https://api.deepseek.com",
                models = listOf("deepseek-flash", "deepseek-v4-pro"),
                selectedModel = "deepseek-flash"
            ),
            Provider(
                id = "openai",
                name = "OpenAI",
                baseUrl = "https://api.openai.com/v1",
                models = listOf("gpt-5.4-nano", "gpt-6-luna", "gpt-6-astra"),
                selectedModel = "gpt-5.4-nano"
            )
        )
    }
}
