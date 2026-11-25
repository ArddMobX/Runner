package com.runner.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Тип провайдера. Определяет, какие необязательные поля запроса ему можно слать.
 * Раньше это угадывалось по подстроке в URL, из-за чего, например, поле
 * `thinking` уходило на любой адрес со словом deepseek.com, включая чужие прокси.
 */
enum class ProviderKind {
    OPENAI_COMPATIBLE,
    GEMINI,
    DEEPSEEK
}

/**
 * Извлекает нормализованный хост из URL без схемы, userinfo, порта, пути,
 * query/fragment и хвостовой точки FQDN.
 */
internal fun extractHost(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return ""
    val authority = trimmed
        .substringAfter("://", trimmed)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('@')
        .trim()
    if (authority.isEmpty()) return ""
    val host = if (authority.startsWith('[')) {
        val closeIdx = authority.indexOf(']')
        if (closeIdx > 0) authority.substring(0, closeIdx + 1) else authority
    } else {
        authority.substringBefore(':')
    }
    return host.trimEnd('.').lowercase()
}

/**
 * Провайдер — это имя, Base URL, свой ключ и список моделей.
 * Ключ хранится только здесь (в зашифрованном виде) и никуда не логируется.
 */
data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val models: List<ModelInfo> = emptyList(),
    val selectedModel: String = ""
) {
    /** Ключ задан — провайдера можно использовать. */
    val isReady: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank()

    /** Модель, которая реально уйдёт в запрос (без технического префикса models/). */
    val activeModel: String get() = selectedModel.ifBlank { models.firstOrNull()?.id.orEmpty() }.removePrefix("models/")

    fun modelIds(): List<String> = models.map { it.id.removePrefix("models/") }

    /**
     * Тип провайдера: сначала по id пресета, потом по точному хосту Base URL.
     *
     * Имя в определении НЕ участвует: провайдер, названный «Gemini через прокси»,
     * получал Google-специфичные подстановки (`thought_signature`) на чужом хосте.
     * Гейт по имени — угадывание, а ошибка тут стоит лишних 400-х от провайдера.
     * id пресета остаётся рабочим ориентиром: его пользователь не переименует.
     */
    val kind: ProviderKind get() = when {
        id == "gemini" || extractHost(baseUrl) == "generativelanguage.googleapis.com" -> ProviderKind.GEMINI
        id == "deepseek" || extractHost(baseUrl) == "api.deepseek.com" -> ProviderKind.DEEPSEEK
        else -> ProviderKind.OPENAI_COMPATIBLE
    }

    /** Base URL ведёт ровно на официальный DeepSeek, а не на похожий по имени прокси. */
    val isOfficialDeepSeekHost: Boolean get() = extractHost(baseUrl) == "api.deepseek.com"
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

    fun loadProviders(): List<Provider> = synchronized(storeLock) {
        loadProvidersLocked()
    }

    private fun loadProvidersLocked(): List<Provider> {
        val blob = prefs.getString(KEY_BLOB, null)
        val fallback = prefs.getString(KEY_BLOB_FALLBACK, null)
        val resolved = resolveStoredProvidersJson(blob, fallback)
        if (resolved != null) {
            val parsed = parse(resolved.json)
            if (!parsed.isNullOrEmpty()) {
                val migrated = migrateStalePresets(parsed)
                // Если обновились пресеты или данные пришли из plaintext fallback,
                // пробуем пересохранить их (при восстановлении Keystore fallback зашифруется).
                if (migrated != parsed || resolved.fromFallback) {
                    saveProvidersLocked(migrated)
                }
                return migrated
            }
        }

        val legacy = migrateLegacy()
        if (legacy != null) {
            saveProvidersLocked(legacy.providers)
            if (!legacy.activeProviderId.isNullOrBlank()) {
                prefs.edit().putString(KEY_ACTIVE_ID, legacy.activeProviderId).apply()
            }
            // Удаляем только ключи провайдеров, не трогая api_reverse_proxy_url
            // и прочие ключи, которые мигрирует SettingsStore.
            legacyPrefs.edit()
                .remove("api_base_url")
                .remove("api_key")
                .remove("groq_api_key")
                .remove("api_model_name")
                .apply()
            return legacy.providers
        }

        val seeds = defaultProviders()
        saveProvidersLocked(seeds)
        return seeds
    }

    fun saveProviders(providers: List<Provider>) = synchronized(storeLock) {
        saveProvidersLocked(providers)
    }

    private fun saveProvidersLocked(providers: List<Provider>) {
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

    fun setActiveProviderId(id: String) = synchronized(storeLock) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun upsert(provider: Provider): List<Provider> = synchronized(storeLock) {
        val current = loadProvidersLocked().toMutableList()
        val index = current.indexOfFirst { it.id == provider.id }
        if (index >= 0) current[index] = provider else current.add(provider)
        saveProvidersLocked(current)
        current
    }

    fun remove(id: String): List<Provider> = synchronized(storeLock) {
        val current = loadProvidersLocked().filterNot { it.id == id }
        val safe = current.ifEmpty { defaultProviders() }
        saveProvidersLocked(safe)
        if (activeProviderId() == id) {
            prefs.edit().putString(KEY_ACTIVE_ID, safe.first().id).apply()
        }
        safe
    }

    /** Провайдер, выбранный для работы. Если выбранного нет — первый доступный. */
    fun activeProvider(): Provider? = synchronized(storeLock) {
        val providers = loadProvidersLocked()
        val activeId = activeProviderId()
        providers.firstOrNull { it.id == activeId } ?: providers.firstOrNull()
    }

    private data class LegacyMigrationResult(
        val providers: List<Provider>,
        val activeProviderId: String?
    )

    private fun migrateLegacy(): LegacyMigrationResult? {
        val baseUrl = legacyPrefs.getString("api_base_url", null)?.trim().orEmpty()
        val apiKey = (legacyPrefs.getString("api_key", null)
            ?: legacyPrefs.getString("groq_api_key", null))?.trim().orEmpty()
        val model = legacyPrefs.getString("api_model_name", null)?.trim().orEmpty()

        if (baseUrl.isBlank() && apiKey.isBlank()) return null

        val providers = defaultProviders().toMutableList()
        val activeId: String?
        if (baseUrl.isNotBlank()) {
            val normalizedUrl = baseUrl.trimEnd('/')
            val index = providers.indexOfFirst {
                it.baseUrl.trimEnd('/').equals(normalizedUrl, ignoreCase = true)
            }
            if (index >= 0) {
                val existing = providers[index]
                providers[index] = existing.copy(
                    apiKey = apiKey,
                    selectedModel = model.ifBlank { existing.selectedModel },
                    models = if (model.isNotBlank() && existing.models.none { it.id == model }) {
                        listOf(ModelInfo(id = model)) + existing.models
                    } else {
                        existing.models
                    }
                )
                activeId = existing.id
            } else {
                val customId = "custom_${System.currentTimeMillis()}"
                providers.add(
                    0,
                    Provider(
                        id = customId,
                        name = "Свой провайдер",
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        models = if (model.isNotBlank()) listOf(ModelInfo(id = model)) else emptyList(),
                        selectedModel = model
                    )
                )
                activeId = customId
            }
        } else {
            // Задан только legacy api_key / groq_api_key без кастомного baseUrl:
            // переносим его в дефолтный провайдер (Groq), чтобы ключ не потерялся.
            val defaultIdx = providers.indexOfFirst { it.id == DEFAULT_PROVIDER_ID }
            if (defaultIdx >= 0) {
                val existing = providers[defaultIdx]
                providers[defaultIdx] = existing.copy(
                    apiKey = apiKey,
                    selectedModel = model.ifBlank { existing.selectedModel },
                    models = if (model.isNotBlank() && existing.models.none { it.id == model }) {
                        listOf(ModelInfo(id = model)) + existing.models
                    } else {
                        existing.models
                    }
                )
                activeId = existing.id
            } else {
                activeId = null
            }
        }
        return LegacyMigrationResult(providers = providers, activeProviderId = activeId)
    }

    /**
     * Одноразовая миграция старых стартовых списков моделей.
     *
     * Пресеты Token Harbor, Clean API, AbniBots, ProxyAPI и VseGPT приезжали
     * с моделями 2024 года (gpt-4o, claude-3-5-sonnet, gemini-2.0-flash).
     * Новые установки получают актуальный список сразу, а у существующих
     * в настройках лежит сохранённая копия. Меняем её только если список
     * в точности равен старому стартовому набору: значит, пользователь его
     * не обновлял и не добавлял свои модели, и ничего не потеряет.
     */
    private fun migrateStalePresets(providers: List<Provider>): List<Provider> {
        return providers.map { provider ->
            val legacySets = LEGACY_SEED_MODELS[provider.id] ?: return@map provider
            val currentIds = provider.models.map { it.id }.toSet()
            val matchedLegacy = legacySets.firstOrNull { it == currentIds } ?: return@map provider
            val preset = PRESETS.firstOrNull { it.id == provider.id } ?: return@map provider
            provider.copy(
                models = preset.models,
                selectedModel = if (provider.selectedModel in matchedLegacy) {
                    preset.selectedModel
                } else {
                    provider.selectedModel
                }
            )
        }
    }

    private fun serialize(providers: List<Provider>): JSONArray = JSONArray().apply {
        providers.forEach { provider ->
            put(
                JSONObject().apply {
                    put("id", provider.id)
                    put("name", provider.name)
                    put("baseUrl", provider.baseUrl)
                    put("apiKey", provider.apiKey)
                    put(
                        "models",
                        JSONArray().apply {
                            provider.models.forEach { model ->
                                put(
                                    JSONObject().apply {
                                        put("id", model.id)
                                        if (model.name.isNotBlank()) put("name", model.name)
                                        model.contextLength?.let { put("contextLength", it) }
                                        if (model.supportedReasoningEfforts.isNotEmpty()) {
                                            put(
                                                "reasoningEfforts",
                                                JSONArray().apply {
                                                    model.supportedReasoningEfforts.forEach { put(it.apiValue) }
                                                }
                                            )
                                        }
                                        model.defaultReasoningEffort?.let {
                                            put("defaultReasoningEffort", it.apiValue)
                                        }
                                        if (model.reasoningMetadataKnown) {
                                            put("reasoningMetadataKnown", true)
                                        }
                                    }
                                )
                            }
                        }
                    )
                    put("selectedModel", provider.selectedModel)
                }
            )
        }
    }

    private fun parse(json: String): List<Provider>? = try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optStringOrEmpty("id")
            if (id.isBlank()) return@mapNotNull null

            Provider(
                id = id,
                name = item.optStringOrEmpty("name").ifBlank { id },
                baseUrl = item.optStringOrEmpty("baseUrl"),
                apiKey = item.optStringOrEmpty("apiKey"),
                models = parseModels(item.optJSONArray("models")),
                selectedModel = item.optStringOrEmpty("selectedModel")
            )
        }
    } catch (_: Exception) {
        null
    }

    /** Поддерживает и старый формат (массив строк), и новый (массив объектов). */
    private fun parseModels(array: JSONArray?): List<ModelInfo> {
        if (array == null) return emptyList()
        val result = LinkedHashMap<String, ModelInfo>()
        for (index in 0 until array.length()) {
            val raw = array.opt(index)
            when (raw) {
                is String -> if (raw.isNotBlank()) result[raw] = ModelInfo(id = raw)
                is JSONObject -> {
                    val id = raw.optStringOrEmpty("id")
                    if (id.isBlank()) continue
                    val effortsJson = raw.optJSONArray("reasoningEfforts")
                    val supportedEfforts = if (effortsJson != null) {
                        buildList<ReasoningEffort> {
                            for (i in 0 until effortsJson.length()) {
                                ReasoningEffort.fromApiValue(effortsJson.opt(i) as? String)?.let { add(it) }
                            }
                        }.distinct().sortedBy { it.ordinal }
                    } else {
                        emptyList()
                    }
                    val defaultEffort = ReasoningEffort.fromApiValue(raw.optStringOrEmpty("defaultReasoningEffort"))
                    val metadataKnown = raw.optBoolean("reasoningMetadataKnown", supportedEfforts.isNotEmpty())
                    result[id] = ModelInfo(
                        id = id,
                        name = raw.optStringOrEmpty("name"),
                        contextLength = raw.optInt("contextLength", 0).takeIf { it > 0 },
                        supportedReasoningEfforts = supportedEfforts,
                        defaultReasoningEffort = defaultEffort,
                        reasoningMetadataKnown = metadataKnown
                    )
                }
                else -> Unit
            }
        }
        return result.values.toList()
    }

    private fun JSONObject.optStringOrEmpty(key: String): String =
        (opt(key) as? String).orEmpty()

    companion object {
        private val storeLock = Any()

        private const val PREFS_NAME = "runner_secure"
        private const val LEGACY_PREFS_NAME = "runner_prefs"
        private const val KEY_BLOB = "providers_blob"
        private const val KEY_BLOB_FALLBACK = "providers_blob_plain"
        private const val KEY_ACTIVE_ID = "active_provider_id"

        const val DEFAULT_PROVIDER_ID = "groq"

        internal data class StoredProvidersResolution(
            val json: String,
            val fromFallback: Boolean
        )

        /**
         * Разрешает сохранённый JSON провайдеров из зашифрованного блоба и plaintext-резерва.
         * При отсутствии [encryptedBlob] (например, если Keystore упал при сохранении и
         * удалил [KEY_BLOB]) обязан читаться [fallbackBlob], чтобы ключи пользователя не стирались.
         */
        internal fun resolveStoredProvidersJson(
            encryptedBlob: String?,
            fallbackBlob: String?,
            decrypt: (String) -> String? = SecureStore::decrypt
        ): StoredProvidersResolution? {
            val decrypted = encryptedBlob?.let(decrypt)
            if (decrypted != null) {
                return StoredProvidersResolution(json = decrypted, fromFallback = false)
            }
            if (!fallbackBlob.isNullOrBlank()) {
                return StoredProvidersResolution(json = fallbackBlob, fromFallback = true)
            }
            return null
        }

        /**
         * Старые стартовые списки пресетов: по ним узнаём установки, где
         * список моделей ни разу не обновляли. Ключ - id провайдера.
         */
        private val LEGACY_SEED_MODELS: Map<String, List<Set<String>>> = mapOf(
            "tokenharbor" to listOf(
                setOf("gpt-4o", "claude-3-5-sonnet", "gemini-2.0-flash", "deepseek-chat"),
                setOf("gpt-5.4-nano", "claude-sonnet-4.5", "gemini-2.5-flash", "deepseek-v4-pro")
            ),
            "cleanapi" to listOf(
                setOf("gpt-4o", "claude-3-5-sonnet", "gemini-2.0-flash"),
                setOf("gpt-5.4-nano", "claude-sonnet-4.5", "gemini-2.5-flash")
            ),
            "abnibots" to listOf(
                setOf("gpt-4o", "gpt-4o-mini", "claude-3-5-sonnet"),
                setOf("gpt-5.4-nano", "gpt-6-luna", "claude-sonnet-4.5")
            ),
            "proxyapi" to listOf(
                setOf("gpt-4o", "gpt-4o-mini", "claude-3-5-sonnet-20241022"),
                setOf("gpt-5.4-nano", "gpt-6-luna", "claude-sonnet-4.5")
            ),
            "vsegpt" to listOf(
                setOf("openai/gpt-4o", "anthropic/claude-3-5-sonnet"),
                setOf("openai/gpt-5.4-nano", "anthropic/claude-sonnet-4.5")
            ),
            "together" to listOf(
                setOf(
                    "meta-llama/Llama-3.3-70B-Instruct-Turbo",
                    "Qwen/Qwen2.5-72B-Instruct-Turbo"
                ),
                setOf(
                    "meta-llama/Llama-4-Maverick-17B-128E-Instruct-Turbo",
                    "Qwen/Qwen3.8-72B-Instruct-Turbo"
                )
            ),
            "gemini" to listOf(
                setOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro")
            ),
            "openai" to listOf(
                setOf("gpt-5.4-nano", "gpt-6-luna", "gpt-6-astra")
            ),
            "openrouter" to listOf(
                setOf(
                    "qwen/qwen3.8-27b:free",
                    "nvidia/nemotron-3.5-lightning:free",
                    "deepseek/deepseek-v4.1-flash"
                )
            )
        )

        /**
         * Готовые пресеты провайдеров для быстрой настройки без ручного ввода Base URL и моделей.
         */
        val PRESETS: List<Provider> = listOf(
            Provider(
                id = "tokenharbor",
                name = "Token Harbor",
                baseUrl = "https://tokenharbor.ai/v1",
                models = listOf(
                    ModelInfo("deepseek-v4.1-flash"),
                    ModelInfo("claude-sonnet-5.5"),
                    ModelInfo("gemini-4-hydrogen"),
                    ModelInfo("gpt-5.6-luna"),
                    ModelInfo("deepseek-v4-pro")
                ),
                selectedModel = "deepseek-v4.1-flash"
            ),
            Provider(
                id = "cleanapi",
                name = "Clean API",
                baseUrl = "https://cleanapis.com/v1",
                models = listOf(
                    ModelInfo("gpt-5.6-luna"),
                    ModelInfo("claude-sonnet-5"),
                    ModelInfo("gemini-3.6-flash"),
                    ModelInfo("deepseek-v4-flash-0731")
                ),
                selectedModel = "gpt-5.6-luna"
            ),
            Provider(
                id = "abnibots",
                name = "AbniBots",
                baseUrl = "https://api.abnibots.ru/v1",
                models = listOf(
                    ModelInfo("gpt-6-luna"),
                    ModelInfo("gpt-5.6-luna"),
                    ModelInfo("claude-sonnet-5-5")
                ),
                selectedModel = "gpt-6-luna"
            ),
            Provider(
                id = "proxyapi",
                name = "ProxyAPI",
                baseUrl = "https://api.proxyapi.ru/openai/v1",
                models = listOf(
                    ModelInfo("gpt-6-luna"),
                    ModelInfo("gpt-5.6-luna"),
                    ModelInfo("claude-sonnet-5-5")
                ),
                selectedModel = "gpt-6-luna"
            ),
            Provider(
                id = "vsegpt",
                name = "VseGPT",
                baseUrl = "https://api.vsegpt.ru/v1",
                models = listOf(
                    ModelInfo("openai/gpt-6-luna"),
                    ModelInfo("anthropic/claude-sonnet-5.5-medium"),
                    ModelInfo("deepseek/deepseek-v4.1-flash-1017-alt-lowcost")
                ),
                selectedModel = "openai/gpt-6-luna"
            ),
            Provider(
                id = "together",
                name = "Together AI",
                baseUrl = "https://api.together.xyz/v1",
                models = listOf(
                    ModelInfo("zai-org/GLM-5.3-Flash"),
                    ModelInfo("deepseek-ai/DeepSeek-V4.1-Flash-1017"),
                    ModelInfo("Qwen/Qwen3.8-Flash")
                ),
                selectedModel = "zai-org/GLM-5.3-Flash"
            ),
            Provider(
                id = "groq",
                name = "Groq",
                baseUrl = "https://api.groq.com/openai/v1",
                models = listOf(
                    ModelInfo("openai/gpt-oss-120b"),
                    ModelInfo("openai/gpt-oss-20b"),
                    ModelInfo("qwen/qwen3.8-27b")
                ),
                selectedModel = "openai/gpt-oss-120b"
            ),
            Provider(
                id = "gemini",
                name = "Gemini",
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                models = listOf(
                    ModelInfo("gemini-4-hydrogen"),
                    ModelInfo("gemini-3.5-flash-lite"),
                    ModelInfo("gemini-3.8-flash")
                ),
                selectedModel = "gemini-4-hydrogen"
            ),
            Provider(
                id = "openrouter",
                name = "OpenRouter",
                baseUrl = "https://openrouter.ai/api/v1",
                models = listOf(
                    ModelInfo("nvidia/nemotron-3-ultra-550b-a55b:free"),
                    ModelInfo("nvidia/nemotron-3.5-super-vl:free"),
                    ModelInfo("nvidia/nemotron-3.5-lightning:free"),
                    ModelInfo("deepseek/deepseek-v4.1-flash-1017")
                ),
                selectedModel = "nvidia/nemotron-3-ultra-550b-a55b:free"
            ),
            Provider(
                id = "deepseek",
                name = "DeepSeek",
                baseUrl = "https://api.deepseek.com",
                models = listOf(ModelInfo("deepseek-flash"), ModelInfo("deepseek-v4-pro")),
                selectedModel = "deepseek-flash"
            ),
            Provider(
                id = "openai",
                name = "OpenAI",
                baseUrl = "https://api.openai.com/v1",
                models = listOf(
                    ModelInfo("gpt-5.6-luna"),
                    ModelInfo("gpt-6-luna"),
                    ModelInfo("gpt-5.6-terra"),
                    ModelInfo("gpt-6.1-sol")
                ),
                selectedModel = "gpt-5.6-luna"
            )
        )

        /**
         * Стартовый набор. Модели здесь — только подсказка до первого
         * «Обновить список»: настоящий список тянется из GET /models.
         */
        fun defaultProviders(): List<Provider> = listOf(
            Provider(
                id = "groq",
                name = "Groq",
                baseUrl = "https://api.groq.com/openai/v1",
                models = listOf(
                    ModelInfo("openai/gpt-oss-120b"),
                    ModelInfo("openai/gpt-oss-20b"),
                    ModelInfo("qwen/qwen3.8-27b")
                ),
                selectedModel = "openai/gpt-oss-120b"
            ),
            Provider(
                id = "gemini",
                name = "Gemini",
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                models = listOf(
                    ModelInfo("gemini-4-hydrogen"),
                    ModelInfo("gemini-3.5-flash-lite"),
                    ModelInfo("gemini-3.8-flash")
                ),
                selectedModel = "gemini-4-hydrogen"
            ),
            Provider(
                id = "openrouter",
                name = "OpenRouter",
                baseUrl = "https://openrouter.ai/api/v1",
                models = listOf(
                    ModelInfo("nvidia/nemotron-3-ultra-550b-a55b:free"),
                    ModelInfo("nvidia/nemotron-3.5-super-vl:free"),
                    ModelInfo("nvidia/nemotron-3.5-lightning:free"),
                    ModelInfo("deepseek/deepseek-v4.1-flash-1017")
                ),
                selectedModel = "nvidia/nemotron-3-ultra-550b-a55b:free"
            ),
            Provider(
                id = "deepseek",
                name = "DeepSeek",
                baseUrl = "https://api.deepseek.com",
                models = listOf(ModelInfo("deepseek-flash"), ModelInfo("deepseek-v4-pro")),
                selectedModel = "deepseek-flash"
            ),
            Provider(
                id = "openai",
                name = "OpenAI",
                baseUrl = "https://api.openai.com/v1",
                models = listOf(
                    ModelInfo("gpt-5.6-luna"),
                    ModelInfo("gpt-6-luna"),
                    ModelInfo("gpt-5.6-terra"),
                    ModelInfo("gpt-6.1-sol")
                ),
                selectedModel = "gpt-5.6-luna"
            ),
            Provider(
                id = "tokenharbor",
                name = "Token Harbor",
                baseUrl = "https://tokenharbor.ai/v1",
                models = listOf(
                    ModelInfo("deepseek-v4.1-flash"),
                    ModelInfo("claude-sonnet-5.5"),
                    ModelInfo("gemini-4-hydrogen"),
                    ModelInfo("gpt-5.6-luna"),
                    ModelInfo("deepseek-v4-pro")
                ),
                selectedModel = "deepseek-v4.1-flash"
            ),
            Provider(
                id = "cleanapi",
                name = "Clean API",
                baseUrl = "https://cleanapis.com/v1",
                models = listOf(
                    ModelInfo("gpt-5.6-luna"),
                    ModelInfo("claude-sonnet-5"),
                    ModelInfo("gemini-3.6-flash"),
                    ModelInfo("deepseek-v4-flash-0731")
                ),
                selectedModel = "gpt-5.6-luna"
            ),
            Provider(
                id = "abnibots",
                name = "AbniBots",
                baseUrl = "https://api.abnibots.ru/v1",
                models = listOf(
                    ModelInfo("gpt-6-luna"),
                    ModelInfo("gpt-5.6-luna"),
                    ModelInfo("claude-sonnet-5-5")
                ),
                selectedModel = "gpt-6-luna"
            )
        )
    }
}
