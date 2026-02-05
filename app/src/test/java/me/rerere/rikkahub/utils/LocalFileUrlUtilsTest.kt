package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFileUrlUtilsTest {
    @Test
    fun `extractLocalFilePathOrNull supports file urls and absolute paths`() {
        assertEquals(
            "/storage/emulated/0/Pictures/a.png",
            LocalFileUrlUtils.extractLocalFilePathOrNull("file:///storage/emulated/0/Pictures/a.png"),
        )
        assertEquals(
            "/storage/emulated/0/Pictures/a.png",
            LocalFileUrlUtils.extractLocalFilePathOrNull("/storage/emulated/0/Pictures/a.png"),
        )
        assertEquals(
            "/android_asset/icons/a.svg",
            LocalFileUrlUtils.extractLocalFilePathOrNull("file:///android_asset/icons/a.svg"),
        )

        assertNull(LocalFileUrlUtils.extractLocalFilePathOrNull("https://example.com/a.png"))
        assertNull(LocalFileUrlUtils.extractLocalFilePathOrNull("content://example.provider/a.png"))
        assertNull(LocalFileUrlUtils.extractLocalFilePathOrNull("  "))
    }

    @Test
    fun `needsExternalMediaPermission returns true for shared media path`() {
        val appOwned = listOf(
            "/data/user/0/me.rerere.rikkahub",
            "/data/user/0/me.rerere.rikkahub/files",
            "/storage/emulated/0/Android/data/me.rerere.rikkahub/files",
        )
        assertTrue(
            LocalFileUrlUtils.needsExternalMediaPermission(
                value = "file:///storage/emulated/0/Pictures/chart.png",
                appOwnedDirPrefixes = appOwned,
            )
        )
    }

    @Test
    fun `needsExternalMediaPermission returns false for app owned external files`() {
        val appOwned = listOf(
            "/storage/emulated/0/Android/data/me.rerere.rikkahub/files",
        )
        assertFalse(
            LocalFileUrlUtils.needsExternalMediaPermission(
                value = "file:///storage/emulated/0/Android/data/me.rerere.rikkahub/files/upload/a.png",
                appOwnedDirPrefixes = appOwned,
            )
        )
    }

    @Test
    fun `needsExternalMediaPermission returns false for android asset paths`() {
        assertFalse(
            LocalFileUrlUtils.needsExternalMediaPermission(
                value = "file:///android_asset/icons/a.svg",
                appOwnedDirPrefixes = emptyList(),
            )
        )
    }

    @Test
    fun `needsExternalMediaPermission returns false for non file urls`() {
        assertFalse(
            LocalFileUrlUtils.needsExternalMediaPermission(
                value = "https://example.com/a.png",
                appOwnedDirPrefixes = emptyList(),
            )
        )
    }
}

