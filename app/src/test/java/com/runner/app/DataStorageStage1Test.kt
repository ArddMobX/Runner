package com.runner.app

import com.runner.app.data.ChatRepository
import com.runner.app.data.Provider
import com.runner.app.data.ProviderKind
import com.runner.app.data.ProviderStore
import com.runner.app.data.SettingsStore
import com.runner.app.data.extractHost
import com.runner.app.util.ImageUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DataStorageStage1Test {

    // --- ProviderStore fallback resolution ---

    @Test
    fun resolveStoredProvidersJson_readsFallbackWhenEncryptedBlobRemoved() {
        val fallbackJson = """[{"id":"openai","apiKey":"sk-test"}]"""
        val resolved = ProviderStore.resolveStoredProvidersJson(
            encryptedBlob = null,
            fallbackBlob = fallbackJson,
            decrypt = { null }
        )
        assertNotNull(resolved)
        assertEquals(fallbackJson, resolved?.json)
        assertTrue(resolved?.fromFallback == true)
    }

    @Test
    fun resolveStoredProvidersJson_fallsBackWhenDecryptFails() {
        val fallbackJson = """[{"id":"groq","apiKey":"gsk_test"}]"""
        val resolved = ProviderStore.resolveStoredProvidersJson(
            encryptedBlob = "corrupted:blob",
            fallbackBlob = fallbackJson,
            decrypt = { null }
        )
        assertNotNull(resolved)
        assertEquals(fallbackJson, resolved?.json)
        assertTrue(resolved?.fromFallback == true)
    }

    @Test
    fun resolveStoredProvidersJson_prefersDecryptedBlob() {
        val decryptedJson = """[{"id":"gemini","apiKey":"AIza-real"}]"""
        val resolved = ProviderStore.resolveStoredProvidersJson(
            encryptedBlob = "iv:cipher",
            fallbackBlob = """[{"id":"old"}]""",
            decrypt = { decryptedJson }
        )
        assertNotNull(resolved)
        assertEquals(decryptedJson, resolved?.json)
        assertFalse(resolved?.fromFallback == true)
    }

    @Test
    fun resolveStoredProvidersJson_returnsNullWhenBothUnavailable() {
        assertNull(
            ProviderStore.resolveStoredProvidersJson(
                encryptedBlob = "iv:cipher",
                fallbackBlob = null,
                decrypt = { null }
            )
        )
        assertNull(
            ProviderStore.resolveStoredProvidersJson(
                encryptedBlob = null,
                fallbackBlob = "   ",
                decrypt = { null }
            )
        )
    }

    // --- extractHost & ProviderKind ---

    @Test
    fun extractHost_stripsPortWhitespaceUserinfoAndTrailingDot() {
        assertEquals("api.deepseek.com", extractHost("  https://api.deepseek.com:443/v1  "))
        assertEquals("api.deepseek.com", extractHost("https://user:pass@api.deepseek.com.:443/chat?x=1#top"))
        assertEquals(
            "generativelanguage.googleapis.com",
            extractHost("https://generativelanguage.googleapis.com:443/v1beta/openai")
        )
        assertEquals("[::1]", extractHost("http://[::1]:8080/v1"))
        assertEquals("", extractHost("   "))
    }

    @Test
    fun providerKind_matchesHostWithPortAndRejectsSpoofedPath() {
        val deepSeekWithPort = Provider(
            id = "custom_ds",
            name = "My DS",
            baseUrl = " https://api.deepseek.com:443/v1 "
        )
        assertEquals(ProviderKind.DEEPSEEK, deepSeekWithPort.kind)
        assertTrue(deepSeekWithPort.isOfficialDeepSeekHost)

        val geminiWithPort = Provider(
            id = "custom_gem",
            name = "My Gemini",
            baseUrl = "https://generativelanguage.googleapis.com:443/v1beta/openai"
        )
        assertEquals(ProviderKind.GEMINI, geminiWithPort.kind)

        val spoofed = Provider(
            id = "custom_proxy",
            name = "DeepSeek Proxy",
            baseUrl = "https://evil.example/api.deepseek.com/v1"
        )
        assertEquals(ProviderKind.OPENAI_COMPATIBLE, spoofed.kind)
        assertFalse(spoofed.isOfficialDeepSeekHost)
    }

    // --- SettingsStore one-time maxSteps migration ---

    @Test
    fun maxStepsMigration_runsOnceAndPreservesUserChoiceOf5Afterward() {
        assertEquals(10, SettingsStore.resolveMaxStepsMigration(rawSteps = 5, alreadyMigrated = false))
        assertEquals(5, SettingsStore.resolveMaxStepsMigration(rawSteps = 5, alreadyMigrated = true))
        assertEquals(20, SettingsStore.resolveMaxStepsMigration(rawSteps = 20, alreadyMigrated = false))
        assertEquals(1, SettingsStore.resolveMaxStepsMigration(rawSteps = 1, alreadyMigrated = true))
    }

    // --- ChatRepository imageUris JSON codec ---

    @Test
    fun imageUris_roundTripsPathsWithCommasQuotesAndBackslashes() {
        val paths = listOf(
            "/data/user/0/com.runner.app/cache/chat_images/img_1,2.jpg",
            "/storage/emulated/0/DCIM/photo \"test\" \\ копия.png"
        )
        val encoded = ChatRepository.encodeImageUris(paths)
        assertNotNull(encoded)
        assertTrue(encoded!!.startsWith("["))
        assertEquals(paths, ChatRepository.decodeImageUris(encoded))
    }

    @Test
    fun imageUris_decodesEscapedSlashesAndLegacyCommaFormat() {
        val escapedJson = """["\/data\/user\/0\/app\/cache\/chat_images\/a,1.jpg","\/tmp\/b.jpg"]"""
        assertEquals(
            listOf("/data/user/0/app/cache/chat_images/a,1.jpg", "/tmp/b.jpg"),
            ChatRepository.decodeImageUris(escapedJson)
        )

        val legacyCsv = "/data/user/0/app/cache/chat_images/1.jpg, /data/user/0/app/cache/chat_images/2.jpg"
        assertEquals(
            listOf("/data/user/0/app/cache/chat_images/1.jpg", "/data/user/0/app/cache/chat_images/2.jpg"),
            ChatRepository.decodeImageUris(legacyCsv)
        )

        assertNull(ChatRepository.encodeImageUris(emptyList()))
        assertTrue(ChatRepository.decodeImageUris(null).isEmpty())
        assertTrue(ChatRepository.decodeImageUris("   ").isEmpty())
    }

    // --- ImageUtils cache purge protection ---

    @Test
    fun purgeCacheDirs_preservesReferencedChatFilesAndImages() {
        val tempRoot = Files.createTempDirectory("runner_cache_test").toFile()
        try {
            val now = 1_000_000_000L
            val maxAge = 10_000L
            val oldTime = now - 50_000L
            val freshTime = now - 1_000L

            val screenshots = File(tempRoot, "screenshots").apply { mkdirs() }
            val camera = File(tempRoot, "camera").apply { mkdirs() }
            val chatImages = File(tempRoot, "chat_images").apply { mkdirs() }
            val chatFiles = File(tempRoot, "chat_files").apply { mkdirs() }

            val oldShot = File(screenshots, "s1.jpg").apply { writeText("x"); setLastModified(oldTime) }
            val oldCam = File(camera, "c1.jpg").apply { writeText("x"); setLastModified(oldTime) }
            val protectedImg = File(chatImages, "keep.jpg").apply { writeText("x"); setLastModified(oldTime) }
            val orphanImg = File(chatImages, "orphan.jpg").apply { writeText("x"); setLastModified(oldTime) }
            val freshOrphanImg = File(chatImages, "fresh.jpg").apply { writeText("x"); setLastModified(freshTime) }
            val protectedDoc = File(chatFiles, "keep.pdf").apply { writeText("x"); setLastModified(oldTime) }
            val orphanDoc = File(chatFiles, "orphan.pdf").apply { writeText("x"); setLastModified(oldTime) }

            // 1. При protectedPaths == null чистятся только screenshots и camera; chat_* не трогаем.
            ImageUtils.purgeCacheDirs(
                cacheDir = tempRoot,
                maxAgeMs = maxAge,
                protectedPaths = null,
                nowMs = now
            )
            assertFalse(oldShot.exists())
            assertFalse(oldCam.exists())
            assertTrue(protectedImg.exists())
            assertTrue(orphanImg.exists())
            assertTrue(protectedDoc.exists())
            assertTrue(orphanDoc.exists())

            // 2. С актуальным protectedPaths живые вложения сохраняются, а старые сироты удаляются.
            ImageUtils.purgeCacheDirs(
                cacheDir = tempRoot,
                maxAgeMs = maxAge,
                protectedPaths = setOf(protectedImg.absolutePath, protectedDoc.absolutePath),
                nowMs = now
            )
            assertTrue(protectedImg.exists())
            assertTrue(protectedDoc.exists())
            assertTrue(freshOrphanImg.exists())
            assertFalse(orphanImg.exists())
            assertFalse(orphanDoc.exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
