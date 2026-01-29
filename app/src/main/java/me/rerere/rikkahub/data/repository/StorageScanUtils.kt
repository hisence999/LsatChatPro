package me.rerere.rikkahub.data.repository

import java.io.File

internal object StorageScanUtils {
    internal val fileUrlRegex: Regex = Regex("""file://[^\s"]+""")

    private val imageExtensions = setOf(
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "avif",
    )

    internal fun isImageExtension(extension: String): Boolean {
        val ext = extension.trim().lowercase()
        return ext.isNotBlank() && ext in imageExtensions
    }

    internal fun normalizePath(file: File): String {
        return runCatching { file.canonicalFile.absolutePath }.getOrElse { file.absolutePath }
    }

    internal fun isInChildOf(child: File, parent: File): Boolean {
        val parentPath = normalizePath(parent).let { p -> if (p.endsWith(File.separator)) p else p + File.separator }
        val childPath = normalizePath(child)
        return childPath.startsWith(parentPath)
    }
}

