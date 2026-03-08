package me.rerere.rikkahub.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class RememberedWorkspaceForNewChatsTest {
    @Test
    fun rememberWorkspaceForNewChatsIfEnabled_ignoresUpdatesWhenDisabled() {
        val settings = Settings(rememberLastWorkspaceForNewChats = false)

        val updated = settings.rememberWorkspaceForNewChatsIfEnabled(
            workspaceRootTreeUri = "content://workspace",
            workDirRelPath = "project",
        )

        assertNull(updated.rememberedWorkspaceForNewChats)
    }

    @Test
    fun rememberWorkspaceForNewChatsIfEnabled_normalizesValues() {
        val settings = Settings(rememberLastWorkspaceForNewChats = true)

        val updated = settings.rememberWorkspaceForNewChatsIfEnabled(
            workspaceRootTreeUri = "  content://workspace  ",
            workDirRelPath = " ./project\\feature ",
        )

        assertEquals(
            RememberedWorkspaceForNewChats(
                workspaceRootTreeUri = "content://workspace",
                workDirRelPath = "project/feature",
            ),
            updated.rememberedWorkspaceForNewChats,
        )
    }

    @Test
    fun applyRememberedWorkspaceToConversation_usesDefaultRootForManualDir() {
        val conversationId = Uuid.random()
        val settings = Settings(workspaceRootTreeUri = "content://default-root")

        val updated = settings.applyRememberedWorkspaceToConversation(
            conversationId = conversationId,
            rememberedWorkspace = RememberedWorkspaceForNewChats(
                workspaceRootTreeUri = null,
                workDirRelPath = "project",
            ),
        )

        val key = conversationId.toString()
        assertFalse(updated.conversationWorkspaceRoots.containsKey(key))
        assertEquals(
            ConversationWorkDirBinding(
                mode = ConversationWorkDirMode.MANUAL,
                relPath = "project",
            ),
            updated.conversationWorkDirs[key],
        )
    }

    @Test
    fun applyRememberedWorkspaceToConversation_skipsManualDirWithoutAnyRoot() {
        val conversationId = Uuid.random()
        val settings = Settings()

        val updated = settings.applyRememberedWorkspaceToConversation(
            conversationId = conversationId,
            rememberedWorkspace = RememberedWorkspaceForNewChats(
                workspaceRootTreeUri = null,
                workDirRelPath = "project",
            ),
        )

        val key = conversationId.toString()
        assertFalse(updated.conversationWorkspaceRoots.containsKey(key))
        assertFalse(updated.conversationWorkDirs.containsKey(key))
    }

    @Test
    fun clearConversationWorkspace_removesRootAndWorkDirForConversation() {
        val conversationId = Uuid.random()
        val otherConversationId = Uuid.random()
        val settings = Settings(
            conversationWorkspaceRoots = mapOf(
                conversationId.toString() to "content://workspace",
                otherConversationId.toString() to "content://other",
            ),
            conversationWorkDirs = mapOf(
                conversationId.toString() to ConversationWorkDirBinding(
                    mode = ConversationWorkDirMode.MANUAL,
                    relPath = "project",
                ),
                otherConversationId.toString() to ConversationWorkDirBinding(
                    mode = ConversationWorkDirMode.MANUAL,
                    relPath = "other",
                ),
            ),
        )

        val updated = settings.clearConversationWorkspace(conversationId)

        assertFalse(updated.conversationWorkspaceRoots.containsKey(conversationId.toString()))
        assertFalse(updated.conversationWorkDirs.containsKey(conversationId.toString()))
        assertTrue(updated.conversationWorkspaceRoots.containsKey(otherConversationId.toString()))
        assertTrue(updated.conversationWorkDirs.containsKey(otherConversationId.toString()))
    }
}
