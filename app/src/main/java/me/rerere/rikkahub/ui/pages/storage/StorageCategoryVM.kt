package me.rerere.rikkahub.ui.pages.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.AssistantChatCleanupMode
import me.rerere.rikkahub.data.repository.AssistantAttachmentStats
import me.rerere.rikkahub.data.repository.AssistantFileEntry
import me.rerere.rikkahub.data.repository.AssistantImageEntry
import me.rerere.rikkahub.data.repository.DeleteResult
import me.rerere.rikkahub.data.repository.OrphanScanResult
import me.rerere.rikkahub.data.repository.StorageCategoryKey
import me.rerere.rikkahub.data.repository.StorageCategoryUsage
import me.rerere.rikkahub.data.repository.StorageManagerRepository
import me.rerere.rikkahub.utils.UiState
import kotlin.uuid.Uuid

data class AttachmentListState<T>(
    val items: List<T>,
    val totalCount: Int,
    val totalBytes: Long,
    val hasMore: Boolean,
    val isLoadingMore: Boolean,
)

internal fun <T> buildAttachmentListState(
    allItems: List<T>,
    limit: Int,
    totalBytes: Long,
    isLoadingMore: Boolean = false,
): AttachmentListState<T> {
    val safeLimit = limit.coerceAtLeast(0)
    val totalCount = allItems.size
    val items = if (safeLimit >= totalCount) allItems else allItems.take(safeLimit)
    return AttachmentListState(
        items = items,
        totalCount = totalCount,
        totalBytes = totalBytes,
        hasMore = items.size < totalCount,
        isLoadingMore = isLoadingMore,
    )
}

class StorageCategoryVM(
    categoryKey: String,
    private val settingsStore: SettingsStore,
    private val storageRepo: StorageManagerRepository,
) : ViewModel() {
    companion object {
        private const val GLOBAL_ATTACHMENT_PAGE_SIZE = 20
    }

    val category: StorageCategoryKey = StorageCategoryKey.fromKeyOrNull(categoryKey)
        ?: StorageCategoryKey.CHAT_RECORDS

    val assistants: StateFlow<List<Assistant>> = settingsStore.settingsFlow
        .map { it.assistants }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _selectedAssistantId = MutableStateFlow<Uuid?>(null)
    val selectedAssistantId: StateFlow<Uuid?> = _selectedAssistantId.asStateFlow()

    private val _categoryUsage = MutableStateFlow<UiState<StorageCategoryUsage>>(UiState.Idle)
    val categoryUsage: StateFlow<UiState<StorageCategoryUsage>> = _categoryUsage.asStateFlow()

    private val _assistantAttachmentStats = MutableStateFlow<UiState<AssistantAttachmentStats>>(UiState.Idle)
    val assistantAttachmentStats: StateFlow<UiState<AssistantAttachmentStats>> = _assistantAttachmentStats.asStateFlow()

    private val _assistantConversationCount = MutableStateFlow<UiState<Int>>(UiState.Idle)
    val assistantConversationCount: StateFlow<UiState<Int>> = _assistantConversationCount.asStateFlow()

    private val _assistantImages = MutableStateFlow<UiState<AttachmentListState<AssistantImageEntry>>>(UiState.Idle)
    val assistantImages: StateFlow<UiState<AttachmentListState<AssistantImageEntry>>> = _assistantImages.asStateFlow()

    private val _assistantFiles = MutableStateFlow<UiState<AttachmentListState<AssistantFileEntry>>>(UiState.Idle)
    val assistantFiles: StateFlow<UiState<AttachmentListState<AssistantFileEntry>>> = _assistantFiles.asStateFlow()

    private val _orphanScan = MutableStateFlow<UiState<OrphanScanResult>>(UiState.Idle)
    val orphanScan: StateFlow<UiState<OrphanScanResult>> = _orphanScan.asStateFlow()

    private val _action = MutableStateFlow<UiState<DeleteResult>>(UiState.Idle)
    val action: StateFlow<UiState<DeleteResult>> = _action.asStateFlow()

    private var globalImageEntries: List<AssistantImageEntry> = emptyList()
    private var globalImageBytes: Long = 0L
    private var globalImageLimit: Int = GLOBAL_ATTACHMENT_PAGE_SIZE

    private var globalFileEntries: List<AssistantFileEntry> = emptyList()
    private var globalFileBytes: Long = 0L
    private var globalFileLimit: Int = GLOBAL_ATTACHMENT_PAGE_SIZE

    init {
        refreshUsage()
        when (category) {
            StorageCategoryKey.IMAGES -> loadGlobalImages()
            StorageCategoryKey.FILES -> loadGlobalFiles()
            else -> Unit
        }
    }

    fun refreshUsage(force: Boolean = false) {
        viewModelScope.launch {
            val cached = storageRepo.peekOverviewCache()
                ?.categories
                ?.firstOrNull { it.category == category }
            _categoryUsage.value = cached?.let { UiState.Success(it) } ?: UiState.Loading
            _categoryUsage.value = runCatching {
                when (category) {
                    StorageCategoryKey.CACHE -> storageRepo.getCacheUsage()
                    StorageCategoryKey.CHAT_RECORDS -> storageRepo.getChatRecordsUsage()
                    StorageCategoryKey.LOGS -> storageRepo.getLogsUsage()
                    else -> storageRepo.loadOverview(forceRefresh = force).categories.first { it.category == category }
                }
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it) },
            )
        }
    }

    fun selectAssistant(id: Uuid?) {
        _selectedAssistantId.value = id
        if (id == null) {
            _assistantAttachmentStats.value = UiState.Idle
            _assistantConversationCount.value = UiState.Idle
            _assistantImages.value = UiState.Idle
            _assistantFiles.value = UiState.Idle

            when (category) {
                StorageCategoryKey.IMAGES -> loadGlobalImages()
                StorageCategoryKey.FILES -> loadGlobalFiles()
                else -> Unit
            }
            return
        }

        when (category) {
            StorageCategoryKey.IMAGES,
            StorageCategoryKey.FILES,
            StorageCategoryKey.CHAT_RECORDS,
            StorageCategoryKey.HISTORY_FILES,
            -> reloadAssistantData(id)

            else -> Unit
        }
    }

    private fun reloadAssistantData(assistantId: Uuid) {
        viewModelScope.launch {
            val loadAttachmentStats = category == StorageCategoryKey.FILES || category == StorageCategoryKey.CHAT_RECORDS
            val loadConversationCount = category == StorageCategoryKey.CHAT_RECORDS
            val loadImages = category == StorageCategoryKey.IMAGES
            val loadFiles = category == StorageCategoryKey.FILES

            _assistantAttachmentStats.value = if (loadAttachmentStats) UiState.Loading else UiState.Idle
            _assistantConversationCount.value = if (loadConversationCount) UiState.Loading else UiState.Idle
            _assistantImages.value = if (loadImages) UiState.Loading else UiState.Idle
            _assistantFiles.value = if (loadFiles) UiState.Loading else UiState.Idle

            val statsState = if (loadAttachmentStats) {
                runCatching { storageRepo.getAssistantAttachmentStats(assistantId) }
                    .fold(
                        onSuccess = { UiState.Success(it) },
                        onFailure = { UiState.Error(it) },
                    )
            } else {
                UiState.Idle
            }
            val countState = if (loadConversationCount) {
                runCatching { storageRepo.getAssistantConversationCount(assistantId) }
                    .fold(
                        onSuccess = { UiState.Success(it) },
                        onFailure = { UiState.Error(it) },
                    )
            } else {
                UiState.Idle
            }
            val imagesState = if (loadImages) {
                runCatching { storageRepo.getAssistantImageEntries(assistantId) }
                    .fold(
                        onSuccess = { entries ->
                            val bytes = entries.sumOf { it.bytes }
                            UiState.Success(buildAttachmentListState(entries, entries.size, bytes))
                        },
                        onFailure = { UiState.Error(it) },
                    )
            } else {
                UiState.Idle
            }

            val filesState = if (loadFiles) {
                runCatching { storageRepo.getAssistantFileEntries(assistantId) }
                    .fold(
                        onSuccess = { entries ->
                            val bytes = entries.sumOf { it.bytes }
                            UiState.Success(buildAttachmentListState(entries, entries.size, bytes))
                        },
                        onFailure = { UiState.Error(it) },
                    )
            } else {
                UiState.Idle
            }

            _assistantAttachmentStats.value = statsState
            _assistantConversationCount.value = countState
            _assistantImages.value = imagesState
            _assistantFiles.value = filesState
        }
    }

    fun clearAssistantImages(assistantId: Uuid) {
        viewModelScope.launch {
            _action.value = UiState.Loading
            _action.value = runCatching {
                storageRepo.clearAssistantChatAttachments(
                    assistantId = assistantId,
                    clearImages = true,
                    clearFiles = false,
                )
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it) },
            )
            refreshUsage()
            reloadAssistantData(assistantId)
        }
    }

    fun deleteImages(assistantId: Uuid?, absolutePaths: List<String>) {
        viewModelScope.launch {
            _action.value = UiState.Loading
            _action.value = runCatching { storageRepo.deleteAssistantImageEntries(absolutePaths) }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
            refreshUsage()
            if (assistantId == null) {
                loadGlobalImages()
            } else {
                reloadAssistantData(assistantId)
            }
        }
    }

    fun deleteFiles(assistantId: Uuid?, absolutePaths: List<String>) {
        viewModelScope.launch {
            _action.value = UiState.Loading
            _action.value = runCatching { storageRepo.deleteAssistantFileEntries(absolutePaths) }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
            refreshUsage()
            if (assistantId == null) {
                loadGlobalFiles()
            } else {
                reloadAssistantData(assistantId)
            }
        }
    }

    fun clearAssistantFiles(assistantId: Uuid) {
        viewModelScope.launch {
            _action.value = UiState.Loading
            _action.value = runCatching {
                storageRepo.clearAssistantChatAttachments(
                    assistantId = assistantId,
                    clearImages = false,
                    clearFiles = true,
                )
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it) },
            )
            refreshUsage()
            reloadAssistantData(assistantId)
        }
    }

    fun clearAssistantChats(assistantId: Uuid, mode: AssistantChatCleanupMode) {
        viewModelScope.launch {
            _action.value = UiState.Loading
            _action.value = runCatching {
                storageRepo.clearAssistantChats(
                    assistantId = assistantId,
                    mode = mode,
                )
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it) },
            )
            refreshUsage()
            reloadAssistantData(assistantId)
        }
    }

    fun scanOrphans() {
        viewModelScope.launch {
            _orphanScan.value = UiState.Loading
            _orphanScan.value = runCatching { storageRepo.scanOrphans() }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
        }
    }

    fun clearAllOrphans() {
        viewModelScope.launch {
            _action.value = UiState.Loading
            _action.value = runCatching { storageRepo.clearAllOrphans() }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
            refreshUsage()
            scanOrphans()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _action.value = UiState.Loading
            _action.value = runCatching { storageRepo.clearCache() }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
            refreshUsage()
        }
    }

    fun loadMoreImages() {
        if (category != StorageCategoryKey.IMAGES) return
        if (_selectedAssistantId.value != null) return
        val current = _assistantImages.value as? UiState.Success ?: return
        val data = current.data
        if (!data.hasMore || data.isLoadingMore) return

        _assistantImages.value = UiState.Success(data.copy(isLoadingMore = true))
        viewModelScope.launch {
            globalImageLimit = minOf(globalImageLimit + GLOBAL_ATTACHMENT_PAGE_SIZE, globalImageEntries.size)
            _assistantImages.value = UiState.Success(
                buildAttachmentListState(
                    allItems = globalImageEntries,
                    limit = globalImageLimit,
                    totalBytes = globalImageBytes,
                )
            )
        }
    }

    fun loadMoreFiles() {
        if (category != StorageCategoryKey.FILES) return
        if (_selectedAssistantId.value != null) return
        val current = _assistantFiles.value as? UiState.Success ?: return
        val data = current.data
        if (!data.hasMore || data.isLoadingMore) return

        _assistantFiles.value = UiState.Success(data.copy(isLoadingMore = true))
        viewModelScope.launch {
            globalFileLimit = minOf(globalFileLimit + GLOBAL_ATTACHMENT_PAGE_SIZE, globalFileEntries.size)
            _assistantFiles.value = UiState.Success(
                buildAttachmentListState(
                    allItems = globalFileEntries,
                    limit = globalFileLimit,
                    totalBytes = globalFileBytes,
                )
            )
        }
    }

    private fun loadGlobalImages() {
        viewModelScope.launch {
            globalImageLimit = GLOBAL_ATTACHMENT_PAGE_SIZE
            _assistantImages.value = UiState.Loading
            _assistantImages.value = runCatching {
                val entries = storageRepo.getAllImageEntries()
                globalImageEntries = entries
                globalImageBytes = entries.sumOf { it.bytes }
                buildAttachmentListState(
                    allItems = entries,
                    limit = globalImageLimit,
                    totalBytes = globalImageBytes,
                )
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it) },
            )
        }
    }

    private fun loadGlobalFiles() {
        viewModelScope.launch {
            globalFileLimit = GLOBAL_ATTACHMENT_PAGE_SIZE
            _assistantFiles.value = UiState.Loading
            _assistantFiles.value = runCatching {
                val entries = storageRepo.getAllFileEntries()
                globalFileEntries = entries
                globalFileBytes = entries.sumOf { it.bytes }
                buildAttachmentListState(
                    allItems = entries,
                    limit = globalFileLimit,
                    totalBytes = globalFileBytes,
                )
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it) },
            )
        }
    }
}
