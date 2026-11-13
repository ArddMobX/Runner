package com.runner.app

import com.runner.app.util.ProjectInstructionsGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProjectInstructionsGeneratorTest {

    @Test
    fun createsFileAndDetectsGradle() {
        val dir = Files.createTempDirectory("runner-init").toFile()
        try {
            File(dir, "build.gradle.kts").writeText("")
            File(dir, "settings.gradle.kts").writeText("")
            File(dir, "src").mkdirs()

            val result = ProjectInstructionsGenerator.create(dir)

            assertTrue(result.created)
            val file = File(dir, ProjectInstructionsGenerator.FILE_NAME)
            assertTrue(file.isFile)
            val text = file.readText()
            assertTrue(text.contains("Gradle"))
            assertTrue(text.contains("`src/`"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun detectsNodeProject() {
        val dir = Files.createTempDirectory("runner-init-node").toFile()
        try {
            File(dir, "package.json").writeText("""{"scripts":{"test":"jest"}}""")

            val text = ProjectInstructionsGenerator.buildContent(dir)

            assertTrue(text.contains("Node.js"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun doesNotOverwriteExistingFile() {
        val dir = Files.createTempDirectory("runner-init-existing").toFile()
        try {
            val file = File(dir, ProjectInstructionsGenerator.FILE_NAME)
            file.writeText("мой текст")

            val result = ProjectInstructionsGenerator.create(dir)

            assertFalse(result.created)
            assertEquals("мой текст", file.readText())
        } finally {
            dir.deleteRecursively()
        }
    }
}
