package me.rerere.rikkahub.ui.pages.assistant.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.memory.entity.MemoryEdge
import me.rerere.rikkahub.data.memory.entity.MemoryNode
import me.rerere.rikkahub.data.memory.entity.MemoryTier
import me.rerere.rikkahub.data.memory.migration.LegacyMigrator
import me.rerere.rikkahub.data.memory.repository.KnowledgeGraphRepository
import me.rerere.rikkahub.data.repository.MemoryRepository

/**
 * UI State for the Knowledge Map and Memory pages.
 */
data class KnowledgeMapState(
    val isLoading: Boolean = true,
    val nodes: List<MemoryNode> = emptyList(),
    val edges: List<MemoryEdge> = emptyList(),
    val stats: KnowledgeGraphStats? = null,
    val selectedNode: MemoryNode? = null,
    val error: String? = null
)

/**
 * Migration State - tracks whether migration is needed and its progress.
 */
sealed class MigrationState {
    data object Checking : MigrationState()
    data object NotNeeded : MigrationState()
    data class NeedsMigration(val legacyMemoryCount: Int) : MigrationState()
    data class InProgress(val progress: Float, val stage: String) : MigrationState()
    data class Completed(val nodesCreated: Int, val edgesCreated: Int) : MigrationState()
    data class Failed(val error: String) : MigrationState()
}

/**
 * ViewModel for the Knowledge Map and all Memory pages.
 * Handles migration, data loading, and CRUD operations for the knowledge graph.
 */
class KnowledgeMapViewModel(
    private val assistantId: String,
    private val repository: KnowledgeGraphRepository,
    private val legacyMigrator: LegacyMigrator,
    private val memoryRepository: MemoryRepository? = null // Optional, for legacy count check
) : ViewModel() {
    
    private val _state = MutableStateFlow(KnowledgeMapState())
    val state: StateFlow<KnowledgeMapState> = _state.asStateFlow()
    
    // Alias for new UI tabs
    val uiState: StateFlow<KnowledgeMapState> = _state.asStateFlow()
    
    private val _migrationState = MutableStateFlow<MigrationState>(MigrationState.Checking)
    val migrationState: StateFlow<MigrationState> = _migrationState.asStateFlow()
    
    init {
        checkMigrationAndLoad()
    }
    
    /**
     * Check if migration is needed, then load data accordingly.
     */
    private fun checkMigrationAndLoad() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            _migrationState.value = MigrationState.Checking
            
            try {
                // Check if already migrated
                val isMigrated = legacyMigrator.isMigrated(assistantId)
                
                if (isMigrated) {
                    _migrationState.value = MigrationState.NotNeeded
                    loadGraphData()
                } else {
                    // Check legacy memory count
                    val legacyCount = try {
                        memoryRepository?.getMemoriesOfAssistant(assistantId)?.size ?: 0
                    } catch (e: Exception) {
                        0
                    }
                    
                    if (legacyCount > 0) {
                        _migrationState.value = MigrationState.NeedsMigration(legacyCount)
                        _state.update { it.copy(isLoading = false) }
                    } else {
                        // No legacy data, mark as migrated and load (empty) graph
                        legacyMigrator.markMigrated(assistantId)
                        _migrationState.value = MigrationState.NotNeeded
                        loadGraphData()
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
                _migrationState.value = MigrationState.Failed(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Start the migration process (called from UI).
     */
    fun startMigration() {
        viewModelScope.launch {
            _migrationState.value = MigrationState.InProgress(0f, "Preparing migration...")
            
            try {
                val result = legacyMigrator.migrate(assistantId) { phase, progress, message ->
                    _migrationState.value = MigrationState.InProgress(progress, message)
                }
                
                _migrationState.value = MigrationState.Completed(
                    nodesCreated = result.nodesCreated,
                    edgesCreated = result.edgesCreated
                )
                
                // Load the migrated data
                loadGraphData()
            } catch (e: Exception) {
                _migrationState.value = MigrationState.Failed(e.message ?: "Migration failed")
            }
        }
    }
    
    /**
     * Dismiss the migration result banner and switch to normal mode.
     */
    fun dismissMigrationResult() {
        _migrationState.value = MigrationState.NotNeeded
    }
    
    /**
     * Load graph data from repository.
     */
    private suspend fun loadGraphData() {
        // Collect nodes as Flow
        viewModelScope.launch {
            repository.getAllActiveNodesFlow(assistantId).collectLatest { nodes ->
                _state.update { it.copy(nodes = nodes, isLoading = false) }
            }
        }
        
        // Collect edges as Flow
        viewModelScope.launch {
            repository.getAllEdgesFlow(assistantId).collectLatest { edges ->
                _state.update { it.copy(edges = edges) }
            }
        }
        
        // Load stats
        viewModelScope.launch {
            try {
                val graphStats = repository.getGraphStats(assistantId)
                _state.update { it.copy(
                    stats = KnowledgeGraphStats(
                        totalNodes = graphStats.totalNodes,
                        totalEdges = graphStats.totalEdges,
                        coreCount = graphStats.coreCount,
                        recallCount = graphStats.recallCount,
                        archivalCount = graphStats.archivalCount
                    )
                )}
            } catch (e: Exception) {
                // Stats are optional, don't fail the whole load
            }
        }
    }
    
    /**
     * Refresh data.
     */
    fun refresh() {
        checkMigrationAndLoad()
    }
    
    /**
     * Select a node (for detail view).
     */
    fun selectNode(node: MemoryNode?) {
        _state.update { it.copy(selectedNode = node) }
        
        // Record access if selecting a node
        node?.let {
            viewModelScope.launch {
                repository.recordAccess(it.id)
            }
        }
    }
    
    /**
     * Delete a node by ID (from EntitiesTab).
     */
    fun deleteNode(nodeId: String) {
        viewModelScope.launch {
            repository.deleteNode(nodeId)
            _state.update { it.copy(selectedNode = null) }
        }
    }
    
    /**
     * Delete a node (legacy method).
     */
    fun deleteNode(node: MemoryNode) {
        deleteNode(node.id)
    }
    
    /**
     * Promote a node to core tier.
     */
    fun promoteToCore(node: MemoryNode) {
        viewModelScope.launch {
            repository.promoteToCore(node.id)
            _state.update { it.copy(selectedNode = null) }
        }
    }
    
    /**
     * Demote a node from core tier.
     */
    fun demoteFromCore(node: MemoryNode) {
        viewModelScope.launch {
            repository.demoteFromCore(node.id)
            _state.update { it.copy(selectedNode = null) }
        }
    }
    
    /**
     * Archive a node.
     */
    fun archiveNode(node: MemoryNode) {
        viewModelScope.launch {
            repository.archiveNode(node.id)
            _state.update { it.copy(selectedNode = null) }
        }
    }
    
    /**
     * Clear all memories for this assistant.
     */
    fun clearAllMemories() {
        viewModelScope.launch {
            try {
                repository.clearAllForAssistant(assistantId)
                _state.update { it.copy(nodes = emptyList(), edges = emptyList()) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to clear memories: ${e.message}") }
            }
        }
    }
    
    /**
     * Re-migrate from scratch (for debugging/fix issues).
     */
    fun remigrateFromScratch() {
        viewModelScope.launch {
            legacyMigrator.clearMigration(assistantId)
            checkMigrationAndLoad()
        }
    }
}

