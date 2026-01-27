package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.dao.LorebookEntryRevisionDao
import me.rerere.rikkahub.data.db.entity.LorebookEntryRevisionEntity
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookEntry
import me.rerere.rikkahub.utils.JsonInstant

class LorebookEntryRevisionRepository(
    private val dao: LorebookEntryRevisionDao,
    private val settingsStore: SettingsStore,
) {
    companion object {
        const val KEEP_LATEST_PER_LOREBOOK = 100
    }

    enum class Action(val raw: String) {
        CREATE("create"),
        UPDATE("update"),
        DELETE("delete");

        companion object {
            fun parse(raw: String): Action? {
                return entries.firstOrNull { it.raw == raw }
            }
        }
    }

    sealed interface UndoResult {
        data class Success(val revisionId: Int) : UndoResult
        data class Failure(val code: String, val message: String) : UndoResult
    }

    fun observeRecentByLorebook(lorebookId: String, limit: Int): Flow<List<LorebookEntryRevisionEntity>> {
        return dao.observeRecentByLorebook(lorebookId, limit.coerceIn(1, KEEP_LATEST_PER_LOREBOOK))
    }

    suspend fun getRecentByLorebook(lorebookId: String, limit: Int): List<LorebookEntryRevisionEntity> =
        withContext(Dispatchers.IO) {
            dao.getRecentByLorebook(lorebookId, limit.coerceIn(1, KEEP_LATEST_PER_LOREBOOK))
        }

    suspend fun record(
        lorebookId: String,
        assistantId: String,
        conversationId: String?,
        action: Action,
        entryId: String,
        entryTitle: String,
        entryIndex: Int?,
        before: LorebookEntry?,
        after: LorebookEntry?,
    ): Int = withContext(Dispatchers.IO) {
        val beforeJson = before?.let { JsonInstant.encodeToString(LorebookEntry.serializer(), it) }
        val afterJson = after?.let { JsonInstant.encodeToString(LorebookEntry.serializer(), it) }
        val id = dao.insert(
            LorebookEntryRevisionEntity(
                lorebookId = lorebookId,
                assistantId = assistantId,
                conversationId = conversationId,
                action = action.raw,
                entryId = entryId,
                entryTitle = entryTitle,
                entryIndex = entryIndex,
                beforeJson = beforeJson,
                afterJson = afterJson,
            )
        ).toInt()
        dao.pruneKeepLatest(lorebookId, KEEP_LATEST_PER_LOREBOOK)
        id
    }

    suspend fun undo(lorebookId: String, revisionId: Int? = null): UndoResult = withContext(Dispatchers.IO) {
        val revision = if (revisionId != null) {
            dao.getById(revisionId)
        } else {
            dao.getLatestNotUndone(lorebookId)
        } ?: return@withContext UndoResult.Failure(
            code = "revision_not_found",
            message = "No revision found to undo",
        )

        if (revision.lorebookId != lorebookId) {
            return@withContext UndoResult.Failure(
                code = "revision_lorebook_mismatch",
                message = "Revision does not belong to lorebook",
            )
        }

        if (revision.undoneAt != null) {
            return@withContext UndoResult.Failure(
                code = "revision_already_undone",
                message = "Revision already undone",
            )
        }

        val action = Action.parse(revision.action) ?: return@withContext UndoResult.Failure(
            code = "invalid_revision_action",
            message = "Invalid revision action",
        )

        val beforeEntry = revision.beforeJson
            ?.let { raw -> runCatching { JsonInstant.decodeFromString<LorebookEntry>(raw) }.getOrNull() }

        val requiredBefore = action == Action.UPDATE || action == Action.DELETE
        if (requiredBefore && beforeEntry == null) {
            return@withContext UndoResult.Failure(
                code = "revision_missing_before",
                message = "Revision is missing before snapshot",
            )
        }

        var lorebookFound = false
        settingsStore.update { current ->
            val lorebookIndex = current.lorebooks.indexOfFirst { it.id.toString() == lorebookId }
            if (lorebookIndex == -1) return@update current

            lorebookFound = true
            val lorebook = current.lorebooks[lorebookIndex]
            val updatedLorebook = LorebookEntryHistory.applyUndo(
                lorebook = lorebook,
                action = when (action) {
                    Action.CREATE -> LorebookEntryHistory.Action.CREATE
                    Action.UPDATE -> LorebookEntryHistory.Action.UPDATE
                    Action.DELETE -> LorebookEntryHistory.Action.DELETE
                },
                entryId = revision.entryId,
                entryIndex = revision.entryIndex,
                before = beforeEntry,
            )

            if (updatedLorebook == lorebook) return@update current

            current.copy(
                lorebooks = current.lorebooks.toMutableList().apply {
                    this[lorebookIndex] = updatedLorebook
                }
            )
        }

        if (!lorebookFound) {
            return@withContext UndoResult.Failure(
                code = "lorebook_not_found",
                message = "Lorebook not found",
            )
        }

        dao.markUndone(revision.id, System.currentTimeMillis())
        UndoResult.Success(revision.id)
    }
}
