package me.rerere.rikkahub.utils

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.model.ChubCharacterV2
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookExport
import me.rerere.rikkahub.data.model.SillyTavernWorldInfo
import me.rerere.rikkahub.data.model.TavernCharacterBook
import me.rerere.rikkahub.data.model.toLorebook
import me.rerere.rikkahub.data.model.toSillyTavernWorldInfo
import me.rerere.rikkahub.data.model.toTavernCharacterBook
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Utility for importing and exporting lorebooks.
 * Supports LastChat format, Tavern CharacterBook format, and SillyTavern World Info format.
 */
object LorebookExportImport {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }
    
    /**
     * Export a lorebook to LastChat JSON format.
     */
    fun exportToLastChatFormat(lorebook: Lorebook): String {
        val export = LorebookExport(
            version = 1,
            format = "lastchat",
            lorebook = lorebook
        )
        return json.encodeToString(LorebookExport.serializer(), export)
    }
    
    /**
     * Export a lorebook to Tavern CharacterBook format.
     * Note: Image content will be lost in conversion.
     */
    fun exportToTavernFormat(lorebook: Lorebook): String {
        val tavernBook = lorebook.toTavernCharacterBook()
        return json.encodeToString(TavernCharacterBook.serializer(), tavernBook)
    }
    
    /**
     * Export a lorebook to SillyTavern World Info format.
     * This format has entries as an object with numeric string keys.
     */
    fun exportToSillyTavernFormat(lorebook: Lorebook): String {
        val worldInfo = lorebook.toSillyTavernWorldInfo()
        return json.encodeToString(SillyTavernWorldInfo.serializer(), worldInfo)
    }
    
    /**
     * Result of importing a lorebook.
     */
    sealed class ImportResult {
        data class Success(val lorebook: Lorebook, val format: String) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }
    
    /**
     * Import a lorebook from a JSON string.
     * Auto-detects format (LastChat, Tavern, or SillyTavern).
     */
    fun importFromJson(jsonString: String): ImportResult {
        return try {
            // Try LastChat format first
            try {
                val export = json.decodeFromString(LorebookExport.serializer(), jsonString)
                return ImportResult.Success(export.lorebook, "lastchat")
            } catch (e: Exception) {
                // Not LastChat format, try others
            }
            
            // Try SillyTavern World Info format (entries is an object/map)
            try {
                val worldInfo = json.decodeFromString(SillyTavernWorldInfo.serializer(), jsonString)
                // Check if it has entries (map format indicates SillyTavern)
                if (worldInfo.entries.isNotEmpty()) {
                    return ImportResult.Success(worldInfo.toLorebook(), "sillytavern")
                }
            } catch (e: Exception) {
                // Not SillyTavern format
            }
            
            // Try Tavern CharacterBook format (entries is an array)
            try {
                val tavernBook = json.decodeFromString(TavernCharacterBook.serializer(), jsonString)
                // Ensure it has entries or a name to be considered valid
                if (tavernBook.entries.isNotEmpty() || tavernBook.name.isNotEmpty()) {
                    return ImportResult.Success(tavernBook.toLorebook(), "tavern")
                }
            } catch (e: Exception) {
                // Not Tavern format either
            }
            
            // Try Chub.ai / Tavern V2 character format (may have nested character_book)
            try {
                val chubChar = json.decodeFromString(ChubCharacterV2.serializer(), jsonString)
                // Check for character_book in data (V2) or directly (V1)
                val characterBook = chubChar.data?.character_book ?: chubChar.character_book
                if (characterBook != null && (characterBook.entries.isNotEmpty() || characterBook.name.isNotEmpty())) {
                    val lorebook = characterBook.toLorebook().copy(
                        name = characterBook.name.ifEmpty { chubChar.data?.name ?: chubChar.name },
                        description = characterBook.description.ifEmpty { chubChar.data?.description ?: chubChar.description }
                    )
                    return ImportResult.Success(lorebook, "chub")
                }
            } catch (e: Exception) {
                // Not Chub format
            }
            
            ImportResult.Error("Unsupported lorebook format")
        } catch (e: Exception) {
            ImportResult.Error("Failed to parse lorebook: ${e.message}")
        }
    }
    
    /**
     * Import a lorebook from a URI.
     */
    fun importFromUri(context: Context, uri: Uri): ImportResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ImportResult.Error("Could not open file")
            
            val reader = BufferedReader(InputStreamReader(inputStream))
            val content = reader.readText()
            reader.close()
            
            importFromJson(content)
        } catch (e: Exception) {
            ImportResult.Error("Failed to read file: ${e.message}")
        }
    }
    
    /**
     * Get file name suggestion based on lorebook name.
     */
    fun getSuggestedFileName(lorebook: Lorebook, format: String = "lastchat"): String {
        val baseName = lorebook.name.ifEmpty { "lorebook" }
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(50)
        return when (format) {
            "tavern" -> "${baseName}_tavern.json"
            "sillytavern" -> "${baseName}_sillytavern.json"
            else -> "${baseName}.json"
        }
    }
}

