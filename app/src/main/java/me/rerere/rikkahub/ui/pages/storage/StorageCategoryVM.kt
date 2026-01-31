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
import me.rerere.rikkahub.data.repository.AssistantImageEntry
import me.rerere.rikkahub.data.repository.DeleteResult
import me.rerere.rikkahub.data.repository.OrphanScanResult
import me.rerere.rikkahub.data.repository.StorageCategoryKey
import me.rerere.rikkahub.data.repository.StorageCategoryUsage
import me.rerere.rikkahub.data.repository.StorageManagerRepository
import me.rerere.rikkahub.utils.UiState
import kotlin.uuid.Uuid

class StorageCategoryVM(
    categoryKey: String,
    private val settingsStore: SettingsStore,
    private val storageRepo: StorageManagerRepository,
) : ViewModel() {
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

    private val _assistantImages = MutableStateFlow<UiState<List<AssistantImageEntry>>>(UiState.Idle)
    val assistantImages: StateFlow<UiState<List<AssistantImageEntry>>> = _assistantImages.asStateFlow()

    private val _orphanScan = MutableStateFlow<UiState<OrphanScanResult>>(UiState.Idle)
    val orphanScan: StateFlow<UiState<OrphanScanResult>> = _orphanScan.asStateFlow()

    private val _action = MutableStateFlow<UiState<DeleteResult>>(UiState.Idle)
    val action: StateFlow<UiState<DeleteResult>> = _action.asStateFlow()

    init {
        refreshUsage()
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
            _assistantAttachmentStats.value = UiState.Loading
            _assistantConversationCount.value = UiState.Loading
            _assistantImages.value = if (category == StorageCategoryKey.IMAGES) UiState.Loading else UiState.Idle

            val statsState = runCatching { storageRepo.getAssistantAttachmentStats(assistantId) }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
            val countState = runCatching { storageRepo.getAssistantConversationCount(assistantId) }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
            val imagesState = if (category == StorageCategoryKey.IMAGES) {
                runCatching { storageRepo.getAssistantImageEntries(assistantId) }
                    .fold(
                        onSuccess = { UiState.Success(it) },
                        onFailure = { UiState.Error(it) },
                    )
            } else {
                UiState.Idle
            }

            _assistantAttachmentStats.value = statsState
            _assistantConversationCount.value = countState
            _assistantImages.value = imagesState
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

    fun deleteAssistantImages(assistantId: Uuid, absolutePaths: List<String>) {
        viewModelScope.launch {
            _action.value = UiState.Loading
            _action.value = runCatching { storageRepo.deleteAssistantImageEntries(absolutePaths) }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
            refreshUsage()
            reloadAssistantData(assistantId)
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
}
