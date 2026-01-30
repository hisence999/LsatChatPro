package me.rerere.rikkahub.data.repository

import android.content.Context
import androidx.core.net.toFile
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.dao.AIRequestLogDao
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.GenMediaDAO
import me.rerere.rikkahub.data.model.Avatar
import java.io.File
import kotlin.uuid.Uuid

enum class StorageCategoryKey(val key: String) {
    IMAGES("images"),
    FILES("files"),
    CHAT_RECORDS("chat_records"),
    CACHE("cache"),
    HISTORY_FILES("history_files"),
    LOGS("logs"),
    ;

    companion object {
        fun fromKeyOrNull(key: String): StorageCategoryKey? = entries.firstOrNull { it.key == key }
    }
}

data class StorageCategoryUsage(
    val category: StorageCategoryKey,
    val bytes: Long,
    val fileCount: Int,
)

data class StorageOverview(
    val totalBytes: Long,
    val categories: List<StorageCategoryUsage>,
    val requestLogCount: Int,
    val generatedAt: Long,
)

data class OrphanEntry(
    val absolutePath: String,
    val bytes: Long,
)

data class OrphanScanResult(
    val totalBytes: Long,
    val totalCount: Int,
    val preview: List<OrphanEntry>,
)

data class DeleteResult(
    val deletedCount: Int,
    val failedCount: Int,
    val deletedBytes: Long,
)

data class AssistantAttachmentStats(
    val imageCount: Int,
    val imageBytes: Long,
    val fileCount: Int,
    val fileBytes: Long,
)

enum class AssistantChatCleanupMode {
    RECORDS_ONLY,
    FILES_ONLY,
    RECORDS_AND_FILES,
}

class StorageManagerRepository(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val conversationDAO: ConversationDAO,
    private val conversationRepository: ConversationRepository,
    private val genMediaDAO: GenMediaDAO,
    private val aiRequestLogDao: AIRequestLogDao,
) {
    private val overviewCache = TimedSuspendCache<StorageOverview>(
        maxAgeMs = 5 * 60_000L,
    )

    fun peekOverviewCache(): StorageOverview? = overviewCache.peek()?.value

    fun invalidateOverviewCache() {
        overviewCache.invalidate()
    }

    suspend fun loadOverview(forceRefresh: Boolean = false): StorageOverview {
        return overviewCache.get(forceRefresh = forceRefresh) { computeOverview() }
    }

    private suspend fun computeOverview(): StorageOverview = withContext(Dispatchers.IO) {
        val settings = settingsStore.settingsFlow.value

        val requestLogCount = runCatching { aiRequestLogDao.countAll() }.getOrNull() ?: 0

        val dbUsage = countDatabaseUsage()
        val cacheUsage = countDirUsage(context.cacheDir)

        val referencedFilePaths = buildReferencedFilePathSet(settings = settings)
        val referencedSkillIds = settings.skills.map { it.id.toString() }.toSet()

        val uploadDir = File(context.filesDir, "upload")
        val imagesDir = File(context.filesDir, "images")
        val avatarsDir = File(context.filesDir, "avatars")
        val customIconsDir = File(context.filesDir, "custom_icons")
        val skillsDir = File(context.filesDir, "skills")

        val uploadUsage = countManagedFilesInDir(
            rootDir = uploadDir,
            referencedFilePaths = referencedFilePaths,
            treatAllAsImages = false,
        )

        val imagesUsage = countManagedFilesInDir(
            rootDir = imagesDir,
            referencedFilePaths = referencedFilePaths,
            treatAllAsImages = true,
        )

        val avatarsUsage = countManagedFilesInDir(
            rootDir = avatarsDir,
            referencedFilePaths = referencedFilePaths,
            treatAllAsImages = true,
        )

        val customIconsUsage = countManagedFilesInDir(
            rootDir = customIconsDir,
            referencedFilePaths = referencedFilePaths,
            treatAllAsImages = true,
        )

        val skillsUsage = countSkillsUsage(
            skillsDir = skillsDir,
            referencedSkillIds = referencedSkillIds,
        )

        val imagesBytes = uploadUsage.images.bytes +
            imagesUsage.images.bytes +
            avatarsUsage.images.bytes +
            customIconsUsage.images.bytes
        val imagesCount = uploadUsage.images.count +
            imagesUsage.images.count +
            avatarsUsage.images.count +
            customIconsUsage.images.count

        val filesBytes = uploadUsage.files.bytes + skillsUsage.files.bytes
        val filesCount = uploadUsage.files.count + skillsUsage.files.count

        val historyBytes = uploadUsage.history.bytes +
            imagesUsage.history.bytes +
            avatarsUsage.history.bytes +
            customIconsUsage.history.bytes +
            skillsUsage.history.bytes
        val historyCount = uploadUsage.history.count +
            imagesUsage.history.count +
            avatarsUsage.history.count +
            customIconsUsage.history.count +
            skillsUsage.history.count

        val categories = listOf(
            StorageCategoryUsage(StorageCategoryKey.IMAGES, imagesBytes, imagesCount),
            StorageCategoryUsage(StorageCategoryKey.FILES, filesBytes, filesCount),
            StorageCategoryUsage(StorageCategoryKey.CHAT_RECORDS, dbUsage.bytes, dbUsage.count),
            StorageCategoryUsage(StorageCategoryKey.CACHE, cacheUsage.bytes, cacheUsage.count),
            StorageCategoryUsage(StorageCategoryKey.HISTORY_FILES, historyBytes, historyCount),
            StorageCategoryUsage(StorageCategoryKey.LOGS, bytes = 0L, fileCount = requestLogCount),
        )

        StorageOverview(
            totalBytes = categories
                .asSequence()
                .filterNot { it.category == StorageCategoryKey.LOGS }
                .sumOf { it.bytes },
            categories = categories,
            requestLogCount = requestLogCount,
            generatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun getCacheUsage(): StorageCategoryUsage = withContext(Dispatchers.IO) {
        val usage = countDirUsage(context.cacheDir)
        StorageCategoryUsage(
            category = StorageCategoryKey.CACHE,
            bytes = usage.bytes,
            fileCount = usage.count,
        )
    }

    suspend fun getChatRecordsUsage(): StorageCategoryUsage = withContext(Dispatchers.IO) {
        val usage = countDatabaseUsage()
        StorageCategoryUsage(
            category = StorageCategoryKey.CHAT_RECORDS,
            bytes = usage.bytes,
            fileCount = usage.count,
        )
    }

    suspend fun getLogsUsage(): StorageCategoryUsage = withContext(Dispatchers.IO) {
        val requestLogCount = runCatching { aiRequestLogDao.countAll() }.getOrNull() ?: 0
        StorageCategoryUsage(
            category = StorageCategoryKey.LOGS,
            bytes = 0L,
            fileCount = requestLogCount,
        )
    }

    suspend fun getAssistantAttachmentStats(assistantId: Uuid): AssistantAttachmentStats = withContext(Dispatchers.IO) {
        val conversations = conversationRepository.getConversationsOfAssistant(assistantId).first()
        val imageUrls = LinkedHashSet<String>()
        val fileUrls = LinkedHashSet<String>()

        conversations.forEach { conversation ->
            conversation.messageNodes.forEach { node ->
                node.messages.forEach { message ->
                    message.parts.forEach { part ->
                        when (part) {
                            is UIMessagePart.Image -> part.url.takeIf { it.startsWith("file://") }?.let(imageUrls::add)
                            is UIMessagePart.Document -> part.url.takeIf { it.startsWith("file://") }?.let(fileUrls::add)
                            is UIMessagePart.Video -> part.url.takeIf { it.startsWith("file://") }?.let(fileUrls::add)
                            is UIMessagePart.Audio -> part.url.takeIf { it.startsWith("file://") }?.let(fileUrls::add)
                            else -> Unit
                        }
                    }
                }
            }
        }

        val images = imageUrls
            .asSequence()
            .mapNotNull(::toLocalFileOrNull)
            .distinctBy { it.absolutePath }
            .filter { file ->
                // Safety: only delete chat attachments in upload/, avoid touching avatars/images dirs.
                StorageScanUtils.isInChildOf(file, File(context.filesDir, "upload"))
            }
            .toList()

        val files = fileUrls
            .asSequence()
            .mapNotNull(::toLocalFileOrNull)
            .distinctBy { it.absolutePath }
            .filter { file ->
                StorageScanUtils.isInChildOf(file, File(context.filesDir, "upload"))
            }
            .toList()

        AssistantAttachmentStats(
            imageCount = images.size,
            imageBytes = images.sumOf { it.lengthSafe() },
            fileCount = files.size,
            fileBytes = files.sumOf { it.lengthSafe() },
        )
    }

    suspend fun clearAssistantChatAttachments(
        assistantId: Uuid,
        clearImages: Boolean,
        clearFiles: Boolean,
    ): DeleteResult = withContext(Dispatchers.IO) {
        val conversations = conversationRepository.getConversationsOfAssistant(assistantId).first()
        val imageUrls = LinkedHashSet<String>()
        val fileUrls = LinkedHashSet<String>()

        conversations.forEach { conversation ->
            conversation.messageNodes.forEach { node ->
                node.messages.forEach { message ->
                    message.parts.forEach { part ->
                        when (part) {
                            is UIMessagePart.Image -> if (clearImages) part.url.takeIf { it.startsWith("file://") }?.let(imageUrls::add)
                            is UIMessagePart.Document -> if (clearFiles) part.url.takeIf { it.startsWith("file://") }?.let(fileUrls::add)
                            is UIMessagePart.Video -> if (clearFiles) part.url.takeIf { it.startsWith("file://") }?.let(fileUrls::add)
                            is UIMessagePart.Audio -> if (clearFiles) part.url.takeIf { it.startsWith("file://") }?.let(fileUrls::add)
                            else -> Unit
                        }
                    }
                }
            }
        }

        val targetFiles = (imageUrls + fileUrls)
            .asSequence()
            .mapNotNull(::toLocalFileOrNull)
            .distinctBy { it.absolutePath }
            .filter { file ->
                StorageScanUtils.isInChildOf(file, File(context.filesDir, "upload"))
            }
            .toList()

        val result = deleteFiles(targetFiles)
        invalidateOverviewCache()
        result
    }

    suspend fun clearAssistantChats(
        assistantId: Uuid,
        mode: AssistantChatCleanupMode,
    ): DeleteResult = withContext(Dispatchers.IO) {
        val result = when (mode) {
            AssistantChatCleanupMode.RECORDS_ONLY -> {
                conversationRepository.deleteConversationOfAssistant(assistantId, deleteFiles = false)
                DeleteResult(deletedCount = 0, failedCount = 0, deletedBytes = 0L)
            }

            AssistantChatCleanupMode.FILES_ONLY -> {
                clearAssistantChatAttachments(
                    assistantId = assistantId,
                    clearImages = true,
                    clearFiles = true,
                )
            }

            AssistantChatCleanupMode.RECORDS_AND_FILES -> {
                conversationRepository.deleteConversationOfAssistant(assistantId, deleteFiles = true)
                DeleteResult(deletedCount = 0, failedCount = 0, deletedBytes = 0L)
            }
        }
        invalidateOverviewCache()
        result
    }

    suspend fun getAssistantConversationCount(assistantId: Uuid): Int = withContext(Dispatchers.IO) {
        runCatching { conversationDAO.getConversationCountOfAssistant(assistantId.toString()) }
            .getOrNull()
            ?: 0
    }

    suspend fun scanOrphans(previewLimit: Int = 40): OrphanScanResult = withContext(Dispatchers.IO) {
        val settings = settingsStore.settingsFlow.value
        val referencedFilePaths = buildReferencedFilePathSet(settings = settings)
        val referencedSkillIds = settings.skills.map { it.id.toString() }.toSet()

        val preview = mutableListOf<OrphanEntry>()
        var totalBytes = 0L
        var totalCount = 0

        fun addOrphan(file: File) {
            val bytes = file.lengthSafe()
            totalBytes += bytes
            totalCount += 1
            if (preview.size < previewLimit) {
                preview += OrphanEntry(absolutePath = file.absolutePath, bytes = bytes)
            }
        }

        val roots = listOf(
            File(context.filesDir, "upload"),
            File(context.filesDir, "images"),
            File(context.filesDir, "avatars"),
            File(context.filesDir, "custom_icons"),
        )

        roots.forEach { root ->
            if (!root.exists()) return@forEach
            root.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val normalized = StorageScanUtils.normalizePath(file)
                    if (normalized !in referencedFilePaths) {
                        addOrphan(file)
                    }
                }
        }

        // Orphan skills: folders under filesDir/skills/{uuid} not referenced by settings.skills.
        val skillsDir = File(context.filesDir, "skills")
        if (skillsDir.exists()) {
            skillsDir.listFiles()
                ?.filter { it.isDirectory }
                ?.forEach { dir ->
                    val isUuidFolder = runCatching { Uuid.parse(dir.name) }.isSuccess
                    if (isUuidFolder && dir.name !in referencedSkillIds) {
                        dir.walkTopDown()
                            .filter { it.isFile }
                            .forEach(::addOrphan)
                    }
                }
        }

        OrphanScanResult(
            totalBytes = totalBytes,
            totalCount = totalCount,
            preview = preview,
        )
    }

    suspend fun clearAllOrphans(): DeleteResult = withContext(Dispatchers.IO) {
        val settings = settingsStore.settingsFlow.value
        val referencedFilePaths = buildReferencedFilePathSet(settings = settings)
        val referencedSkillIds = settings.skills.map { it.id.toString() }.toSet()

        var deletedCount = 0
        var failedCount = 0
        var deletedBytes = 0L

        fun deleteFile(file: File) {
            val bytes = file.lengthSafe()
            val ok = runCatching { file.delete() }.getOrNull() == true
            if (ok) {
                deletedCount += 1
                deletedBytes += bytes
            } else {
                failedCount += 1
            }
        }

        val roots = listOf(
            File(context.filesDir, "upload"),
            File(context.filesDir, "images"),
            File(context.filesDir, "avatars"),
            File(context.filesDir, "custom_icons"),
        )
        roots.forEach { root ->
            if (!root.exists()) return@forEach
            root.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val normalized = StorageScanUtils.normalizePath(file)
                    if (normalized !in referencedFilePaths) {
                        deleteFile(file)
                    }
                }
        }

        val skillsDir = File(context.filesDir, "skills")
        if (skillsDir.exists()) {
            skillsDir.listFiles()
                ?.filter { it.isDirectory }
                ?.forEach { dir ->
                    val isUuidFolder = runCatching { Uuid.parse(dir.name) }.isSuccess
                    if (!isUuidFolder) return@forEach
                    if (dir.name in referencedSkillIds) return@forEach

                    val usage = countDirUsage(dir)
                    val ok = runCatching { dir.deleteRecursively() }.getOrNull() == true
                    if (ok) {
                        deletedCount += usage.count
                        deletedBytes += usage.bytes
                    } else {
                        failedCount += 1
                    }
                }
        }

        val result = DeleteResult(
            deletedCount = deletedCount,
            failedCount = failedCount,
            deletedBytes = deletedBytes,
        )
        invalidateOverviewCache()
        result
    }

    suspend fun clearCache(): DeleteResult = withContext(Dispatchers.IO) {
        val root = context.cacheDir
        val children = root.listFiles().orEmpty()
        val targets = children.filter { it.exists() }
        val result = deleteFilesOrDirs(targets)
        invalidateOverviewCache()
        result
    }

    private data class Usage(val count: Int, val bytes: Long)

    private data class SplitUsage(
        val images: Usage,
        val files: Usage,
        val history: Usage,
    )

    private data class SkillSplitUsage(
        val files: Usage,
        val history: Usage,
    )

    private fun countDatabaseUsage(): Usage {
        val dbFile = context.getDatabasePath("rikka_hub")
        val walFile = File(dbFile.parentFile, "rikka_hub-wal")
        val shmFile = File(dbFile.parentFile, "rikka_hub-shm")
        val files = listOf(dbFile, walFile, shmFile).filter { it.exists() && it.isFile }
        return Usage(
            count = files.size,
            bytes = files.sumOf { it.lengthSafe() },
        )
    }

    private fun countDirUsage(root: File): Usage {
        if (!root.exists()) return Usage(count = 0, bytes = 0)
        var count = 0
        var bytes = 0L
        root.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                count += 1
                bytes += file.lengthSafe()
            }
        return Usage(count = count, bytes = bytes)
    }

    private fun countManagedFilesInDir(
        rootDir: File,
        referencedFilePaths: Set<String>,
        treatAllAsImages: Boolean,
    ): SplitUsage {
        if (!rootDir.exists()) {
            return SplitUsage(images = Usage(0, 0), files = Usage(0, 0), history = Usage(0, 0))
        }

        var imagesCount = 0
        var imagesBytes = 0L
        var filesCount = 0
        var filesBytes = 0L
        var historyCount = 0
        var historyBytes = 0L

        rootDir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val bytes = file.lengthSafe()
                val referenced = StorageScanUtils.normalizePath(file) in referencedFilePaths
                if (!referenced) {
                    historyCount += 1
                    historyBytes += bytes
                    return@forEach
                }

                val isImage = treatAllAsImages || StorageScanUtils.isImageExtension(file.extension)
                if (isImage) {
                    imagesCount += 1
                    imagesBytes += bytes
                } else {
                    filesCount += 1
                    filesBytes += bytes
                }
            }

        return SplitUsage(
            images = Usage(imagesCount, imagesBytes),
            files = Usage(filesCount, filesBytes),
            history = Usage(historyCount, historyBytes),
        )
    }

    private fun countSkillsUsage(
        skillsDir: File,
        referencedSkillIds: Set<String>,
    ): SkillSplitUsage {
        if (!skillsDir.exists()) return SkillSplitUsage(files = Usage(0, 0), history = Usage(0, 0))

        var filesCount = 0
        var filesBytes = 0L
        var historyCount = 0
        var historyBytes = 0L

        skillsDir.listFiles().orEmpty().forEach { entry ->
            if (!entry.exists()) return@forEach

            val isUuidFolder = entry.isDirectory && runCatching { Uuid.parse(entry.name) }.isSuccess
            val isReferenced = isUuidFolder && entry.name in referencedSkillIds
            val usage = countDirUsage(entry)

            if (isUuidFolder && !isReferenced) {
                historyCount += usage.count
                historyBytes += usage.bytes
            } else {
                filesCount += usage.count
                filesBytes += usage.bytes
            }
        }

        return SkillSplitUsage(
            files = Usage(filesCount, filesBytes),
            history = Usage(historyCount, historyBytes),
        )
    }

    private suspend fun buildReferencedFilePathSet(settings: Settings): Set<String> {
        val referenced = HashSet<String>(8_192)

        fun addUrl(url: String?) {
            if (url.isNullOrBlank() || !url.startsWith("file://")) return
            val file = toLocalFileOrNull(url) ?: return
            referenced += StorageScanUtils.normalizePath(file)
        }

        fun addAvatar(avatar: Avatar?) {
            when (avatar) {
                is Avatar.Image -> addUrl(avatar.url)
                else -> Unit
            }
        }

        // Global user profile assets.
        addAvatar(settings.displaySetting.userAvatar)

        settings.assistants.forEach { assistant ->
            addAvatar(assistant.avatar)
            addUrl(assistant.background)
        }

        // Prompt injection assets.
        settings.modes.forEach { mode ->
            mode.attachments.forEach { attachment ->
                addUrl(attachment.url)
            }
        }

        settings.lorebooks.forEach { lorebook ->
            addAvatar(lorebook.cover)
            lorebook.entries.forEach { entry ->
                entry.attachments.forEach { attachment ->
                    addUrl(attachment.url)
                }
                addUrl(entry.imageContent)
            }
        }

        // Generated images: GenMediaEntity.path uses relative paths like "images/xxx.png".
        val mediaList = try {
            genMediaDAO.getAllMedia()
        } catch (_: Exception) {
            emptyList()
        }
        mediaList.forEach { media ->
            val path = media.path.trim()
            if (path.isBlank()) return@forEach
            val file = File(context.filesDir, path)
            referenced += StorageScanUtils.normalizePath(file)
        }

        // Conversations: scan nodes JSON in batches and pick up file:// urls.
        val total = try {
            conversationDAO.getConversationCount()
        } catch (_: Exception) {
            0
        }
        val batchSize = 40
        var offset = 0
        while (offset < total) {
            val batch = try {
                conversationDAO.getNodesBatchForScan(limit = batchSize, offset = offset)
            } catch (_: Exception) {
                emptyList()
            }

            batch.forEach { row ->
                StorageScanUtils.fileUrlRegex.findAll(row.nodes).forEach { match ->
                    val file = toLocalFileOrNull(match.value) ?: return@forEach
                    referenced += StorageScanUtils.normalizePath(file)
                }
            }

            offset += batchSize
            if (batch.isEmpty()) break
        }

        return referenced
    }

    private fun toLocalFileOrNull(url: String): File? {
        val uri = runCatching { url.toUri() }.getOrNull() ?: return null
        if (uri.scheme != "file") return null
        val file = runCatching { uri.toFile() }.getOrNull() ?: return null
        return file.takeIf { StorageScanUtils.isInChildOf(it, context.filesDir) }
    }

    private fun File.lengthSafe(): Long = runCatching { length() }.getOrNull() ?: 0L

    private fun deleteFiles(files: List<File>): DeleteResult {
        var deletedCount = 0
        var failedCount = 0
        var deletedBytes = 0L

        files.forEach { file ->
            val bytes = file.lengthSafe()
            val ok = runCatching { file.delete() }.getOrNull() == true
            if (ok) {
                deletedCount += 1
                deletedBytes += bytes
            } else {
                failedCount += 1
            }
        }

        return DeleteResult(
            deletedCount = deletedCount,
            failedCount = failedCount,
            deletedBytes = deletedBytes,
        )
    }

    private fun deleteFilesOrDirs(entries: List<File>): DeleteResult {
        var deletedCount = 0
        var failedCount = 0
        var deletedBytes = 0L

        entries.forEach { entry ->
            val usage = countDirUsage(entry)
            val ok = runCatching {
                if (entry.isDirectory) entry.deleteRecursively() else entry.delete()
            }.getOrNull() == true
            if (ok) {
                deletedCount += usage.count
                deletedBytes += usage.bytes
            } else {
                failedCount += 1
            }
        }

        return DeleteResult(
            deletedCount = deletedCount,
            failedCount = failedCount,
            deletedBytes = deletedBytes,
        )
    }
}
