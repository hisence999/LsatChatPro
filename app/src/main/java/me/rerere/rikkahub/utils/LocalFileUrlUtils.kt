package me.rerere.rikkahub.utils

import java.io.File
import java.net.URI

object LocalFileUrlUtils {
    private val externalPathPrefixes = listOf(
        "/storage/",
        "/sdcard/",
        "/mnt/",
    )

    fun extractLocalFilePathOrNull(value: String): String? {
        val raw = value.trim()
        if (raw.isBlank()) return null

        val path = when {
            raw.startsWith("file:") -> {
                runCatching { File(URI(raw)).path }.getOrNull()
                    ?: runCatching { raw.removePrefix("file://") }.getOrNull()
            }

            raw.startsWith("/") -> raw
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return null

        return normalizePath(path)
    }

    fun needsExternalMediaPermission(
        value: String,
        appOwnedDirPrefixes: List<String>,
    ): Boolean {
        val filePath = extractLocalFilePathOrNull(value) ?: return false
        val normalizedPath = normalizePath(filePath)

        if (isAndroidAssetOrResPath(normalizedPath)) return false
        if (!looksLikeExternalStoragePath(normalizedPath)) return false

        return appOwnedDirPrefixes
            .asSequence()
            .map { normalizeDirPrefix(it) }
            .none { normalizedPath.startsWith(it) }
    }

    private fun looksLikeExternalStoragePath(path: String): Boolean {
        return externalPathPrefixes.any { path.startsWith(it) }
    }

    private fun isAndroidAssetOrResPath(path: String): Boolean {
        return path.startsWith("/android_asset/") || path.startsWith("/android_res/")
    }

    private fun normalizePath(path: String): String {
        return path.trim().replace('\\', '/')
    }

    private fun normalizeDirPrefix(dir: String): String {
        val normalized = normalizePath(dir)
        return if (normalized.endsWith("/")) normalized else "$normalized/"
    }
}
