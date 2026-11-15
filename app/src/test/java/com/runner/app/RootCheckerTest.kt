package com.runner.app

import com.runner.app.tools.RootChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootCheckerTest {

    @Test
    fun rootUidIsRecognized() {
        assertTrue(RootChecker.isRootOutput("uid=0(root) gid=0(root) groups=0(root)"))
    }

    @Test
    fun shellUidIsNotRoot() {
        assertFalse(RootChecker.isRootOutput("uid=2000(shell) gid=2000(shell)"))
        assertFalse(RootChecker.isRootOutput("uid=10123(u0_a123)"))
        assertFalse(RootChecker.isRootOutput(""))
    }
}
