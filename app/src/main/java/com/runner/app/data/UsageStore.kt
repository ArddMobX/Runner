package com.runner.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Один ответ модели, по которому потом считается расход. */
data class UsageEvent(
    val timestamp: Long,
    val providerId: String,
    val providerName: String,
    val model: String,
    val sessionId: String?,
    val promptTokens: Int,
    val completionTokens: Int
) {
    val totalTokens: Int get() = promptTokens + completionTokens
}

data class UsageTotals(
    val requests: Int = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0
) {
    val totalTokens: Long get() = promptTokens + completionTokens

    operator fun plus(other: UsageTotals): UsageTotals = UsageTotals(
        requests = requests + other.requests,
        promptTokens = promptTokens + other.promptTokens,
        completionTokens = completionTokens + other.completionTokens
    )
}

/**
 * Локальный журнал расхода токенов.
 *
 * Хранит только факты использования модели, без текста сообщений и ключей.
 * Единый синглтон на процесс (через [UsageStore.get]), чтобы MainActivity и
 * OverlayActivity не затирали события друг друга; чтение и сериализация
 * выполняются асинхронно в фоновом потоке.
 */
class UsageStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val pendingSave = AtomicBoolean(false)
    private var loadedFromDisk = false

    private val _events = MutableStateFlow<List<UsageEvent>>(emptyList())
    val events: StateFlow<List<UsageEvent>> = _events.asStateFlow()

    init {
        ioExecutor.execute {
            val diskEvents = loadFromDisk()
            var needsFlush = false
            synchronized(lock) {
                loadedFromDisk = true
                val recordedBeforeLoad = _events.value
                val merged = (diskEvents + recordedBeforeLoad).takeLast(MAX_EVENTS)
                _events.value = merged
                needsFlush = recordedBeforeLoad.isNotEmpty()
            }
            if (needsFlush) {
                scheduleSave()
            }
        }
    }

    fun record(
        provider: Provider,
        model: String,
        sessionId: String?,
        promptTokens: Int,
        completionTokens: Int
    ) {
        if (promptTokens <= 0 && completionTokens <= 0) return
        val event = UsageEvent(
            timestamp = System.currentTimeMillis(),
            providerId = provider.id,
            providerName = provider.name,
            model = model,
            sessionId = sessionId,
            promptTokens = promptTokens.coerceAtLeast(0),
            completionTokens = completionTokens.coerceAtLeast(0)
        )
        val shouldPersist: Boolean
        synchronized(lock) {
            _events.value = (_events.value + event).takeLast(MAX_EVENTS)
            shouldPersist = loadedFromDisk
        }
        if (shouldPersist) {
            scheduleSave()
        }
    }

    private fun scheduleSave() {
        if (!pendingSave.compareAndSet(false, true)) return
        ioExecutor.execute {
            pendingSave.set(false)
            val snapshot = synchronized(lock) { _events.value }
            saveToDisk(snapshot)
        }
    }

    private fun loadFromDisk(): List<UsageEvent> {
        val raw = prefs.getString(KEY_EVENTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val prompt = item.optInt("prompt", 0)
                    val completion = item.optInt("completion", 0)
                    if (prompt <= 0 && completion <= 0) continue
                    add(
                        UsageEvent(
                            timestamp = item.optLong("timestamp", 0L),
                            providerId = item.optNullableString("providerId").orEmpty(),
                            providerName = item.optNullableString("providerName").orEmpty(),
                            model = item.optNullableString("model").orEmpty(),
                            sessionId = item.optNullableString("sessionId")?.takeIf { it.isNotBlank() },
                            promptTokens = prompt,
                            completionTokens = completion
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveToDisk(events: List<UsageEvent>) {
        val array = JSONArray()
        events.forEach { event ->
            array.put(
                JSONObject().apply {
                    put("timestamp", event.timestamp)
                    put("providerId", event.providerId)
                    put("providerName", event.providerName)
                    put("model", event.model)
                    put("sessionId", event.sessionId.orEmpty())
                    put("prompt", event.promptTokens)
                    put("completion", event.completionTokens)
                }
            )
        }
        prefs.edit().putString(KEY_EVENTS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "runner_usage"
        private const val KEY_EVENTS = "usage_events"
        private const val MAX_EVENTS = 1200

        @Volatile
        private var instance: UsageStore? = null

        private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "runner-usage-store").apply { isDaemon = true }
        }

        fun get(context: Context): UsageStore =
            instance ?: synchronized(this) {
                instance ?: UsageStore(context.applicationContext).also { instance = it }
            }
    }
}

fun List<UsageEvent>.totals(): UsageTotals = fold(UsageTotals()) { total, event ->
    total + UsageTotals(
        requests = 1,
        promptTokens = event.promptTokens.toLong(),
        completionTokens = event.completionTokens.toLong()
    )
}
