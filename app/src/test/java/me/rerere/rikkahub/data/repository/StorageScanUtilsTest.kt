package me.rerere.rikkahub.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StorageScanUtilsTest {
    @Test
    fun fileUrlRegex_findsFileUrls() {
        val input = """
            {
              "a": "file:///data/user/0/me.rerere.rikkahub/files/upload/a.png",
              "b": "hello file:///data/user/0/me.rerere.rikkahub/files/upload/b.jpg world"
            }
        """.trimIndent()

        val urls = StorageScanUtils.fileUrlRegex
            .findAll(input)
            .map { it.value }
            .toList()

        assertEquals(
            listOf(
                "file:///data/user/0/me.rerere.rikkahub/files/upload/a.png",
                "file:///data/user/0/me.rerere.rikkahub/files/upload/b.jpg",
            ),
            urls
        )
    }

    @Test
    fun isImageExtension_works() {
        assertTrue(StorageScanUtils.isImageExtension("png"))
        assertTrue(StorageScanUtils.isImageExtension("JPG"))
        assertTrue(StorageScanUtils.isImageExtension("webp"))
        assertFalse(StorageScanUtils.isImageExtension(""))
        assertFalse(StorageScanUtils.isImageExtension("mp4"))
    }

    @Test
    fun isInChildOf_works() {
        val root = File(System.getProperty("java.io.tmpdir"), "storageScanUtils_${System.nanoTime()}")
        val parent = File(root, "parent")
        val childDir = File(parent, "child")
        val childFile = File(childDir, "a.txt")
        val sibling = File(root, "sibling/a.txt")

        try {
            childDir.mkdirs()
            childFile.writeText("x")
            sibling.parentFile?.mkdirs()
            sibling.writeText("y")

            assertTrue(StorageScanUtils.isInChildOf(childFile, parent))
            assertFalse(StorageScanUtils.isInChildOf(sibling, parent))
        } finally {
            runCatching { root.deleteRecursively() }
        }
    }

    @Test
    fun storageCategoryKey_fromKeyOrNull() {
        assertEquals(StorageCategoryKey.IMAGES, StorageCategoryKey.fromKeyOrNull("images"))
        assertEquals(StorageCategoryKey.CHAT_RECORDS, StorageCategoryKey.fromKeyOrNull("chat_records"))
        assertEquals(null, StorageCategoryKey.fromKeyOrNull("nope"))
    }
}

