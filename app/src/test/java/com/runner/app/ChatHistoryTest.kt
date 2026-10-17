package com.runner.app

import com.runner.app.tools.TrashStore
import com.runner.app.ui.ChatListItem
import com.runner.app.ui.ChatMessage
import com.runner.app.ui.MessageRole
import com.runner.app.ui.components.ActionButtonMode
import com.runner.app.ui.components.canSendMessage
import com.runner.app.ui.components.resolveActionButtonMode
import com.runner.app.ui.findLastUserMessageId
import com.runner.app.ui.groupChatItems
import com.runner.app.ui.undoLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryTest {

    @Test
    fun testFindLastUserMessageId_emptyList() {
        val result = findLastUserMessageId(emptyList())
        assertNull(result)
    }

    @Test
    fun testFindLastUserMessageId_noUserMessages() {
        val messages = listOf(
            ChatMessage(role = MessageRole.SYSTEM_INFO, content = "System initialized"),
            ChatMessage(role = MessageRole.ASSISTANT, content = "Hello! How can I help?"),
            ChatMessage(role = MessageRole.TOOL_EXECUTION, content = "Executing tool...", toolName = "list_files")
        )
        assertNull(findLastUserMessageId(messages))
    }

    @Test
    fun testFindLastUserMessageId_singleUserMessage() {
        val userMsg = ChatMessage(id = "user_1", role = MessageRole.USER, content = "List files in directory")
        val messages = listOf(
            ChatMessage(role = MessageRole.SYSTEM_INFO, content = "System ready"),
            userMsg,
            ChatMessage(role = MessageRole.TOOL_EXECUTION, content = "Listing...", toolName = "list_files"),
            ChatMessage(role = MessageRole.ASSISTANT, content = "Found 3 files.")
        )
        assertEquals("user_1", findLastUserMessageId(messages))
    }

    @Test
    fun testFindLastUserMessageId_multipleUserMessages() {
        val user1 = ChatMessage(id = "u1", role = MessageRole.USER, content = "First prompt")
        val assistant1 = ChatMessage(role = MessageRole.ASSISTANT, content = "First answer")
        val user2 = ChatMessage(id = "u2", role = MessageRole.USER, content = "Second prompt")
        val user3 = ChatMessage(id = "u3", role = MessageRole.USER, content = "Latest prompt")
        val assistant3 = ChatMessage(role = MessageRole.ASSISTANT, content = "Latest answer")

        val messages = listOf(user1, assistant1, user2, user3, assistant3)
        assertEquals("u3", findLastUserMessageId(messages))
    }

    @Test
    fun testFindLastUserMessageId_consecutiveUserMessages() {
        val u1 = ChatMessage(id = "u1", role = MessageRole.USER, content = "First")
        val u2 = ChatMessage(id = "u2", role = MessageRole.USER, content = "Second in a row")
        val messages = listOf(u1, u2)
        assertEquals("u2", findLastUserMessageId(messages))
    }

    @Test
    fun testFindLastUserMessageId_dialogueEndsWithAssistantOrTool() {
        val u1 = ChatMessage(id = "u1", role = MessageRole.USER, content = "Do work")
        val t1 = ChatMessage(id = "t1", role = MessageRole.TOOL_EXECUTION, content = "", toolName = "read_file")
        val a1 = ChatMessage(id = "a1", role = MessageRole.ASSISTANT, content = "Done")
        val messages = listOf(u1, t1, a1)
        assertEquals("u1", findLastUserMessageId(messages))
    }

    @Test
    fun testFindLastUserMessageId_interleavedPlanAndSystem() {
        val s1 = ChatMessage(role = MessageRole.SYSTEM_INFO, content = "Info 1")
        val u1 = ChatMessage(id = "u1", role = MessageRole.USER, content = "User prompt")
        val p1 = ChatMessage(role = MessageRole.PLAN, content = "Plan proposed")
        val s2 = ChatMessage(role = MessageRole.SYSTEM_INFO, content = "Info 2")
        val messages = listOf(s1, u1, p1, s2)
        assertEquals("u1", findLastUserMessageId(messages))
    }

    @Test
    fun testUndoLabel_allKinds() {
        val moveRef = TrashStore.UndoRef(entryId = "e1", kind = TrashStore.EntryKind.MOVE)
        val overwriteRef = TrashStore.UndoRef(entryId = "e2", kind = TrashStore.EntryKind.OVERWRITE)
        val deleteRef = TrashStore.UndoRef(entryId = "e3", kind = TrashStore.EntryKind.DELETE)

        assertEquals("Отменить перемещение", undoLabel(moveRef))
        assertEquals("Отменить перезапись", undoLabel(overwriteRef))
        assertEquals("Отменить удаление", undoLabel(deleteRef))
    }

    @Test
    fun testGroupChatItems_emptyList() {
        val items = groupChatItems(emptyList())
        assertTrue(items.isEmpty())
    }

    @Test
    fun testGroupChatItems_noTools() {
        val u1 = ChatMessage(role = MessageRole.USER, content = "Hello")
        val a1 = ChatMessage(role = MessageRole.ASSISTANT, content = "Hi")
        val items = groupChatItems(listOf(u1, a1))
        assertEquals(2, items.size)
        assertTrue(items[0] is ChatListItem.Single)
        assertTrue(items[1] is ChatListItem.Single)
    }

    @Test
    fun testGroupChatItems_consecutiveToolExecutions() {
        val u1 = ChatMessage(role = MessageRole.USER, content = "Run diagnostics")
        val t1 = ChatMessage(id = "t1", role = MessageRole.TOOL_EXECUTION, isRunning = false, toolName = "ping", content = "pong")
        val t2 = ChatMessage(id = "t2", role = MessageRole.TOOL_EXECUTION, isRunning = false, toolName = "status", content = "ok")
        val a1 = ChatMessage(role = MessageRole.ASSISTANT, content = "All checks passed")

        val items = groupChatItems(listOf(u1, t1, t2, a1))
        assertEquals(3, items.size)
        assertTrue(items[0] is ChatListItem.Single)
        assertEquals(u1, (items[0] as ChatListItem.Single).message)

        assertTrue(items[1] is ChatListItem.ToolGroup)
        val group = (items[1] as ChatListItem.ToolGroup).messages
        assertEquals(2, group.size)
        assertEquals("t1", group[0].id)
        assertEquals("t2", group[1].id)

        assertTrue(items[2] is ChatListItem.Single)
        assertEquals(a1, (items[2] as ChatListItem.Single).message)
    }

    @Test
    fun testGroupChatItems_multipleSeparateToolGroups() {
        val t1 = ChatMessage(id = "t1", role = MessageRole.TOOL_EXECUTION, content = "", isRunning = false, toolName = "step1")
        val t2 = ChatMessage(id = "t2", role = MessageRole.TOOL_EXECUTION, content = "", isRunning = false, toolName = "step2")
        val a1 = ChatMessage(role = MessageRole.ASSISTANT, content = "Interim answer")
        val t3 = ChatMessage(id = "t3", role = MessageRole.TOOL_EXECUTION, content = "", isRunning = false, toolName = "step3")
        val t4 = ChatMessage(id = "t4", role = MessageRole.TOOL_EXECUTION, content = "", isRunning = false, toolName = "step4")

        val items = groupChatItems(listOf(t1, t2, a1, t3, t4))
        assertEquals(3, items.size)
        assertTrue(items[0] is ChatListItem.ToolGroup)
        assertEquals(2, (items[0] as ChatListItem.ToolGroup).messages.size)
        assertTrue(items[1] is ChatListItem.Single)
        assertTrue(items[2] is ChatListItem.ToolGroup)
        assertEquals(2, (items[2] as ChatListItem.ToolGroup).messages.size)
    }

    @Test
    fun testGroupChatItems_singleToolIsNotGrouped() {
        val t1 = ChatMessage(role = MessageRole.TOOL_EXECUTION, isRunning = false, toolName = "ping", content = "pong")
        val items = groupChatItems(listOf(t1))
        assertEquals(1, items.size)
        assertTrue(items[0] is ChatListItem.Single)
    }

    @Test
    fun testGroupChatItems_runningToolIsNotGrouped() {
        val t1 = ChatMessage(role = MessageRole.TOOL_EXECUTION, isRunning = true, toolName = "ping", content = "running...")
        val t2 = ChatMessage(role = MessageRole.TOOL_EXECUTION, isRunning = true, toolName = "ping", content = "running...")
        val items = groupChatItems(listOf(t1, t2))
        assertEquals(2, items.size)
        assertTrue(items[0] is ChatListItem.Single)
        assertTrue(items[1] is ChatListItem.Single)
    }

    @Test
    fun testCanSendMessage() {
        assertTrue(canSendMessage(hasContent = true, isRunning = false, isListening = false))
        assertFalse(canSendMessage(hasContent = false, isRunning = false, isListening = false))
        assertFalse(canSendMessage(hasContent = true, isRunning = true, isListening = false))
        assertFalse(canSendMessage(hasContent = true, isRunning = false, isListening = true))
        assertFalse(canSendMessage(hasContent = true, isRunning = true, isListening = true))
    }

    @Test
    fun testResolveActionButtonMode_priorityOrder() {
        // 1. isRunning takes top priority -> STOP
        assertEquals(
            ActionButtonMode.STOP,
            resolveActionButtonMode(isRunning = true, isListening = true, canSend = true)
        )
        assertEquals(
            ActionButtonMode.STOP,
            resolveActionButtonMode(isRunning = true, isListening = false, canSend = true)
        )

        // 2. isListening takes priority over canSend -> RECORDING (allows stopping speech recording)
        assertEquals(
            ActionButtonMode.RECORDING,
            resolveActionButtonMode(isRunning = false, isListening = true, canSend = true)
        )
        assertEquals(
            ActionButtonMode.RECORDING,
            resolveActionButtonMode(isRunning = false, isListening = true, canSend = false)
        )

        // 3. canSend when idle -> SEND
        assertEquals(
            ActionButtonMode.SEND,
            resolveActionButtonMode(isRunning = false, isListening = false, canSend = true)
        )

        // 4. default idle -> MIC
        assertEquals(
            ActionButtonMode.MIC,
            resolveActionButtonMode(isRunning = false, isListening = false, canSend = false)
        )
    }
}
