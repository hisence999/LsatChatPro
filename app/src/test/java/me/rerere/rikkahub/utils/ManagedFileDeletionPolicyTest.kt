package me.rerere.rikkahub.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ManagedFileDeletionPolicyTest {
    @Test
    fun `accepts files inside managed directory`() {
        val filesDir = Files.createTempDirectory("lastchat-files").toFile()
        val target = File(filesDir, "upload/chat/a.png")

        assertTrue(isManagedChatFile(filesDir, target))
    }

    @Test
    fun `rejects path traversal outside managed directory`() {
        val filesDir = Files.createTempDirectory("lastchat-files").toFile()
        val traversal = File(filesDir, "upload/../outside.png")

        assertFalse(isManagedChatFile(filesDir, traversal))
    }

    @Test
    fun `rejects external storage path`() {
        val filesDir = Files.createTempDirectory("lastchat-files").toFile()
        val externalRoot = Files.createTempDirectory("external-root").toFile()
        val external = File(externalRoot, "upload/chat/a.png")

        assertFalse(isManagedChatFile(filesDir, external))
    }

    @Test
    fun `rejects managed prefix spoofing path`() {
        val filesDir = Files.createTempDirectory("lastchat-files").toFile()
        val spoofed = File(filesDir, "upload_evil/chat/a.png")

        assertFalse(isManagedChatFile(filesDir, spoofed))
    }
}
