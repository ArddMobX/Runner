package com.runner.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.StringReader
import java.util.Properties

/**
 * Регрессионные проверки CI/CD, конфигурации сборки, ProGuard и критических
 * инвариантов манифеста/ресурсов, исправленных в ходе аудита.
 */
class BuildAndCiStage7Test {

    private fun findRepoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").canonicalFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile && File(dir, "app/build.gradle.kts").isFile) {
                return dir
            }
            dir = dir.parentFile
        }
        error("Не удалось найти корень репозитория от ${System.getProperty("user.dir")}")
    }

    @Test
    fun utf8SigningPropertiesRoundTripPreservesSpecialAndNonAsciiCharacters() {
        // Воспроизводит экранирование Python-скрипта из .github/workflows/build.yml
        fun escapeProperty(value: String): String = buildString {
            val trimmed = value.trim()
            trimmed.forEachIndexed { index, ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    ':', '=', '#', '!' -> append('\\').append(ch)
                    ' ' -> if (index == 0) append("\\ ") else append(' ')
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\u000C' -> append("\\f")
                    else -> append(ch)
                }
            }
        }

        val rawPath = """C:\Temp\ключа\release.jks"""
        val rawPass = """P@ss:word=1#2!пароль\end"""
        val rawAlias = "релиз_ключ:1"
        val content = buildString {
            append("RUNNER_KEYSTORE_PATH=").append(escapeProperty(rawPath)).append('\n')
            append("RUNNER_KEYSTORE_PASSWORD=").append(escapeProperty(rawPass)).append('\n')
            append("RUNNER_KEY_ALIAS=").append(escapeProperty(rawAlias)).append('\n')
        }

        val props = Properties().apply {
            // Совпадает с app/build.gradle.kts: bufferedReader(Charsets.UTF_8)
            load(StringReader(content))
        }

        assertEquals(rawPath, props.getProperty("RUNNER_KEYSTORE_PATH"))
        assertEquals(rawPass, props.getProperty("RUNNER_KEYSTORE_PASSWORD"))
        assertEquals(rawAlias, props.getProperty("RUNNER_KEY_ALIAS"))
    }

    @Test
    fun ciWorkflowEnforcesTestsSafeSigningAndNoCiErrorsLoop() {
        val repoRoot = findRepoRoot()
        val workflowFile = File(repoRoot, ".github/workflows/build.yml")
        assertTrue("Отсутствует .github/workflows/build.yml", workflowFile.isFile)
        val yaml = workflowFile.readText(Charsets.UTF_8)

        assertTrue("Ветка ci-errors должна быть исключена из push", yaml.contains("\"!ci-errors\""))
        assertTrue("SIGNING_ARGS должен объявляться как пустой массив", yaml.contains("SIGNING_ARGS=()"))
        assertFalse("SIGNING_ARGS=\"\" ломает debug-сборку пустым аргументом", yaml.contains("SIGNING_ARGS=\"\""))
        assertTrue("CI обязан запускать unit-тесты", yaml.contains("testDebugUnitTest"))
        assertTrue("CI должен запускать Android Lint", yaml.contains("lintDebug"))
        assertFalse(
            "Безусловный push после `git commit ... || true` может зациклить ci-errors",
            yaml.contains("git commit -m \"ci: лог ошибки сборки [skip ci]\" || true")
        )
        assertTrue(
            "Шаги релиза должны опираться на release_built",
            yaml.contains("steps.build.outputs.release_built == 'true'")
        )
        assertTrue(
            "Проверка подписи должна использовать pipefail и не маскировать сбой apksigner через tee",
            yaml.contains("set -euo pipefail") && yaml.contains("\"\$APKSIGNER\" verify --print-certs \"\$APK\" > certs.txt")
        )
    }

    @Test
    fun gradleBuildReadsUtf8SigningPropsAndNeverLogsKeyAlias() {
        val repoRoot = findRepoRoot()
        val gradleFile = File(repoRoot, "app/build.gradle.kts")
        assertTrue(gradleFile.isFile)
        val kts = gradleFile.readText(Charsets.UTF_8)

        assertTrue(
            "signing.properties должен читаться в UTF-8",
            kts.contains("bufferedReader(Charsets.UTF_8)")
        )
        assertFalse(
            "KEY_ALIAS не должен печататься открытым текстом в build.log",
            kts.contains("append(\" keyAlias=\")") || kts.contains("'\$keyAlias'")
        )
        assertTrue(kts.contains("keyAliasLen="))
    }

    @Test
    fun proguardRulesCoverCriticalLibrariesAndComponents() {
        val repoRoot = findRepoRoot()
        val proguardFile = File(repoRoot, "app/proguard-rules.pro")
        assertTrue(proguardFile.isFile)
        val rules = proguardFile.readText(Charsets.UTF_8)

        listOf(
            "SourceFile,LineNumberTable",
            "com.tom_roush.pdfbox.**",
            "com.gemalto.jp2.**",
            "net.lingala.zip4j.**",
            "org.jsoup.nodes.**",
            "com.runner.app.service.**",
            "com.runner.app.data.db.**",
            "android.util.Log"
        ).forEach { expected ->
            assertTrue("В proguard-rules.pro отсутствует правило для $expected", rules.contains(expected))
        }
    }

    @Test
    fun manifestAndXmlResourcesKeepSecurityAndAssistantInvariants() {
        val repoRoot = findRepoRoot()
        val manifest = File(repoRoot, "app/src/main/AndroidManifest.xml").readText(Charsets.UTF_8)
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(manifest.contains("android:allowBackup=\"false\""))

        val overlayBlock = Regex("""<activity[^>]*OverlayActivity[^>]*>""", RegexOption.DOT_MATCHES_ALL)
            .find(manifest)?.value
        assertNotNull("Блок OverlayActivity не найден в манифесте", overlayBlock)
        assertTrue(overlayBlock!!.contains("android:exported=\"true\""))
        assertTrue(overlayBlock.contains("android:showWhenLocked=\"true\""))
        assertTrue(overlayBlock.contains("android:turnScreenOn=\"true\""))

        val receiverBlock = Regex("""<receiver[^>]*TermuxResultReceiver[^>]*>""", RegexOption.DOT_MATCHES_ALL)
            .find(manifest)?.value
        assertNotNull("Блок TermuxResultReceiver не найден в манифесте", receiverBlock)
        assertTrue(receiverBlock!!.contains("android:exported=\"false\""))

        val extractionRules = File(repoRoot, "app/src/main/res/xml/data_extraction_rules.xml")
        assertTrue("Отсутствует res/xml/data_extraction_rules.xml", extractionRules.isFile)

        val recognitionV31 = File(repoRoot, "app/src/main/res/xml-v31/recognition_service.xml")
        assertTrue("Отсутствует res/xml-v31/recognition_service.xml", recognitionV31.isFile)
        assertTrue(recognitionV31.readText(Charsets.UTF_8).contains("android:selectableAsDefault=\"false\""))
    }
}
