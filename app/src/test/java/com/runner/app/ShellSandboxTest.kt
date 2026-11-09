package com.runner.app

import com.runner.app.tools.ToolDispatcher
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Относительные удаления и данные приложений.
 *
 * Раньше shellDataRefusal проверял только токены с ведущим '/', поэтому
 * `rm -rf *` и `find . -delete` проходили мимо абсолютных запретов.
 */
class ShellSandboxTest {

    @Test
    fun relativeDeleteAllIsRefused() {
        val refusal = ToolDispatcher.shellPreRefusal("rm -rf *", allowRootShell = false)
        assertNotNull(refusal)
        assertTrue(refusal!!.contains("текущем каталоге"))
    }

    @Test
    fun findDotDeleteIsRefused() {
        assertNotNull(ToolDispatcher.shellPreRefusal("find . -delete", allowRootShell = false))
    }

    @Test
    fun parentTraversalIsRefused() {
        assertNotNull(ToolDispatcher.shellPreRefusal("rm -rf ../backup", allowRootShell = false))
    }

    @Test
    fun namedRelativeFolderIsAllowed() {
        assertNull(ToolDispatcher.shellPreRefusal("rm -rf build", allowRootShell = false))
    }

    @Test
    fun appDataRemainsForbidden() {
        assertNotNull(
            ToolDispatcher.shellPreRefusal(
                "rm -rf /data/data/com.example/databases",
                allowRootShell = false
            )
        )
        assertNull(
            ToolDispatcher.shellPreRefusal(
                "rm -rf /data/data/com.example/cache",
                allowRootShell = false
            )
        )
    }
}
