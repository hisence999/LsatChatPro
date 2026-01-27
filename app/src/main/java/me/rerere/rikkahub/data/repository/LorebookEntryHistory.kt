package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookEntry

internal object LorebookEntryHistory {
    enum class Action {
        CREATE,
        UPDATE,
        DELETE,
    }

    fun applyUndo(
        lorebook: Lorebook,
        action: Action,
        entryId: String,
        entryIndex: Int?,
        before: LorebookEntry?,
    ): Lorebook {
        val entries = lorebook.entries.toMutableList()
        val existingIndex = entries.indexOfFirst { it.id.toString() == entryId }

        when (action) {
            Action.CREATE -> {
                if (existingIndex >= 0) entries.removeAt(existingIndex)
            }

            Action.UPDATE -> {
                val restored = before ?: return lorebook
                if (existingIndex >= 0) {
                    entries[existingIndex] = restored
                } else {
                    val insertIndex = (entryIndex ?: entries.size).coerceIn(0, entries.size)
                    entries.add(insertIndex, restored)
                }
            }

            Action.DELETE -> {
                val restored = before ?: return lorebook
                if (existingIndex < 0) {
                    val insertIndex = (entryIndex ?: entries.size).coerceIn(0, entries.size)
                    entries.add(insertIndex, restored)
                }
            }
        }

        if (entries == lorebook.entries) return lorebook
        return lorebook.copy(entries = entries)
    }
}

