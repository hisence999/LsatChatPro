package me.rerere.rikkahub.data.memory.engine

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.memory.entity.EdgeType
import me.rerere.rikkahub.data.memory.entity.NodeType


/**
 * Robust knowledge extraction from conversations with minimal LLM errors.
 * 
 * Uses a multi-stage pipeline:
 * 1. Entity extraction with JSON schema validation
 * 2. Relationship extraction between entities
 * 3. Emotional context extraction
 * 4. Retry with feedback on validation failure
 * 
 * Based on research on structured output and error handling for LLMs.
 */
class KnowledgeExtractor(
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore
) {
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    companion object {
        private const val TAG = "KnowledgeExtractor"
        private const val MAX_RETRIES = 3
        private const val MAX_CONTENT_LENGTH = 200
    }
    
    // ==================== DATA CLASSES ====================
    
    @Serializable
    data class ExtractedEntity(
        val label: String,
        val type: String,
        val description: String,
        val confidence: Float = 1.0f,
        val temporal: String? = null,  // "past", "present", "future", "recurring"
        @SerialName("emotional_valence")
        val emotionalValence: Float? = null,
        @SerialName("emotional_arousal")
        val emotionalArousal: Float? = null,
        @SerialName("dominant_emotion")
        val dominantEmotion: String? = null
    )
    
    @Serializable
    data class ExtractedRelationship(
        @SerialName("source_label")
        val sourceLabel: String,
        @SerialName("target_label")
        val targetLabel: String,
        @SerialName("relation_type")
        val relationType: String,
        val confidence: Float = 1.0f
    )
    
    @Serializable
    data class EmotionalContext(
        val valence: Float,          // -1 to +1
        val arousal: Float,          // 0 to 1
        val emotions: List<String>   // ["happy", "excited"]
    )
    
    data class ExtractionResult(
        val entities: List<ExtractedEntity>,
        val relationships: List<ExtractedRelationship>,
        val emotionalContext: EmotionalContext?,
        val overallConfidence: Float,
        val rawResponses: List<String>
    )
    
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String?,
        val entities: List<ExtractedEntity>?,
        val relationships: List<ExtractedRelationship>?
    )
    
    // ==================== MAIN EXTRACTION ====================
    
    /**
     * Extract knowledge from conversation messages.
     * This is the main entry point for extraction.
     */
    suspend fun extractFromConversation(
        messages: List<UIMessage>,
        assistantId: String,
        existingEntityLabels: List<String> = emptyList()
    ): ExtractionResult {
        val rawResponses = mutableListOf<String>()
        
        // Format messages for analysis
        val messagesText = messages.takeLast(10).joinToString("\n") { msg ->
            val role = when (msg.role) {
                MessageRole.USER -> "User"
                MessageRole.ASSISTANT -> "Assistant"
                else -> "System"
            }
            val content = msg.toContentText().take(500)
            "$role: $content"
        }
        
        // Step 1: Extract entities
        val entities = extractEntities(
            messagesText = messagesText,
            assistantId = assistantId,
            existingLabels = existingEntityLabels,
            rawResponses = rawResponses
        )
        
        if (entities.isEmpty()) {
            return ExtractionResult(
                entities = emptyList(),
                relationships = emptyList(),
                emotionalContext = null,
                overallConfidence = 0f,
                rawResponses = rawResponses
            )
        }
        
        // Step 2: Extract relationships between extracted entities
        val relationships = extractRelationships(
            messagesText = messagesText,
            assistantId = assistantId,
            entityLabels = entities.map { it.label },
            rawResponses = rawResponses
        )
        
        // Step 3: Extract emotional context
        val emotionalContext = extractEmotionalContext(
            messagesText = messagesText,
            assistantId = assistantId,
            rawResponses = rawResponses
        )
        
        // Calculate overall confidence
        val entityConfidence = entities.map { it.confidence }.average().toFloat()
        val relationshipConfidence = if (relationships.isNotEmpty()) {
            relationships.map { it.confidence }.average().toFloat()
        } else 1.0f
        val overallConfidence = (entityConfidence + relationshipConfidence) / 2
        
        return ExtractionResult(
            entities = entities,
            relationships = relationships,
            emotionalContext = emotionalContext,
            overallConfidence = overallConfidence,
            rawResponses = rawResponses
        )
    }
    
    /**
     * Extract from a single text (e.g., a core memory from old system).
     */
    suspend fun extractFromText(
        text: String,
        assistantId: String,
        existingEntityLabels: List<String> = emptyList()
    ): ExtractionResult {
        val rawResponses = mutableListOf<String>()
        
        val entities = extractEntities(
            messagesText = text,
            assistantId = assistantId,
            existingLabels = existingEntityLabels,
            rawResponses = rawResponses
        )
        
        val relationships = extractRelationships(
            messagesText = text,
            assistantId = assistantId,
            entityLabels = entities.map { it.label },
            rawResponses = rawResponses
        )
        
        val overallConfidence = if (entities.isNotEmpty()) {
            entities.map { it.confidence }.average().toFloat()
        } else 0f
        
        return ExtractionResult(
            entities = entities,
            relationships = relationships,
            emotionalContext = null,
            overallConfidence = overallConfidence,
            rawResponses = rawResponses
        )
    }
    
    // ==================== ENTITY EXTRACTION ====================
    
    private suspend fun extractEntities(
        messagesText: String,
        assistantId: String,
        existingLabels: List<String>,
        rawResponses: MutableList<String>
    ): List<ExtractedEntity> {
        val nodeTypes = NodeType.entries.map { it.name }
        
        val prompt = """
You are a knowledge extraction system. Extract ATOMIC FACTS about the USER from this conversation.

IMPORTANT RULES:
1. Only extract facts about the USER, not the assistant
2. Each fact should be self-contained and specific
3. Note temporal context (past/present/future changes)
4. Detect when new info contradicts old beliefs

Valid entity types: ${nodeTypes.joinToString(", ")}

Already known entities (use exact label if same): ${existingLabels.joinToString(", ").ifEmpty { "none" }}

Conversation:
$messagesText

Extract as JSON array. Each entity:
- label: Short identifier (e.g., "Alice", "Tokyo", "Morning Coffee")
- type: One of the valid types (EXACT match required)
- description: Complete atomic fact statement (max $MAX_CONTENT_LENGTH chars)
- confidence: 0.0-1.0 (explicit=0.9+, inferred=0.5-0.8)
- temporal: "past" | "present" | "future" | "recurring" | null
- emotional_valence: -1.0 to 1.0 (negative to positive)
- dominant_emotion: Primary emotion or null

TEMPORAL DETECTION EXAMPLES:
- "I moved to Tokyo last year" → temporal: "present" (current state, started in past)
- "I used to love coffee" → temporal: "past" (no longer true)
- "I'm planning to learn Japanese" → temporal: "future"
- "I always have coffee in the morning" → temporal: "recurring"

BELIEF REVISION: If user says something that contradicts what they said before, note it in description.

Only include facts with confidence >= 0.5.
Return ONLY valid JSON array. If nothing found: []

Example:
[{"label":"Tokyo","type":"PLACE","description":"User currently lives in Tokyo, moved there last year","confidence":0.95,"temporal":"present","emotional_valence":0.2,"dominant_emotion":"neutral"}]
""".trimIndent()
        
        return executeWithRetry(
            prompt = prompt,
            assistantId = assistantId,
            rawResponses = rawResponses,
            parser = { response -> parseEntityResponse(response) }
        ) ?: emptyList()
    }
    
    private fun parseEntityResponse(response: String): List<ExtractedEntity>? {
        return try {
            // Find JSON array in response
            val jsonStart = response.indexOf('[')
            val jsonEnd = response.lastIndexOf(']')
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
                Log.w(TAG, "No JSON array found in response")
                return null
            }
            
            val jsonStr = response.substring(jsonStart, jsonEnd + 1)
            val entities = json.decodeFromString<List<ExtractedEntity>>(jsonStr)
            
            // Validate entity types
            val validTypes = NodeType.entries.map { it.name }.toSet()
            val validEntities = entities.filter { entity ->
                if (entity.type.uppercase() !in validTypes) {
                    Log.w(TAG, "Invalid entity type: ${entity.type}")
                    false
                } else if (entity.label.isBlank() || entity.description.isBlank()) {
                    Log.w(TAG, "Empty label or description")
                    false
                } else {
                    true
                }
            }.map { it.copy(type = it.type.uppercase()) }
            
            validEntities
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse entity response", e)
            null
        }
    }
    
    // ==================== RELATIONSHIP EXTRACTION ====================
    
    private suspend fun extractRelationships(
        messagesText: String,
        assistantId: String,
        entityLabels: List<String>,
        rawResponses: MutableList<String>
    ): List<ExtractedRelationship> {
        if (entityLabels.size < 2) return emptyList()
        
        val edgeTypes = EdgeType.entries.map { it.name }
        
        val prompt = """
You are a knowledge extraction system. Identify relationships between the following entities based on the conversation.

Entities: ${entityLabels.joinToString(", ")}

Valid relationship types: ${edgeTypes.joinToString(", ")}

Conversation:
$messagesText

Extract relationships as JSON array. Each relationship should have:
- source_label: The source entity (must be from entities list)
- target_label: The target entity (must be from entities list)
- relation_type: One of the valid types above (use EXACT type name)
- confidence: 0.0-1.0

Only include relationships explicitly stated or strongly implied.
Return ONLY a valid JSON array, no explanation.
If no relationships found, return: []

Example output:
[{"source_label":"Alice","target_label":"TechCorp","relation_type":"WORKS_WITH","confidence":0.8}]
""".trimIndent()
        
        return executeWithRetry(
            prompt = prompt,
            assistantId = assistantId,
            rawResponses = rawResponses,
            parser = { response -> parseRelationshipResponse(response, entityLabels) }
        ) ?: emptyList()
    }
    
    private fun parseRelationshipResponse(
        response: String,
        validLabels: List<String>
    ): List<ExtractedRelationship>? {
        return try {
            val jsonStart = response.indexOf('[')
            val jsonEnd = response.lastIndexOf(']')
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
                return null
            }
            
            val jsonStr = response.substring(jsonStart, jsonEnd + 1)
            val relationships = json.decodeFromString<List<ExtractedRelationship>>(jsonStr)
            
            val validTypes = EdgeType.entries.map { it.name }.toSet()
            val labelSet = validLabels.map { it.lowercase() }.toSet()
            
            relationships.filter { rel ->
                val typeValid = rel.relationType.uppercase() in validTypes
                val sourceValid = rel.sourceLabel.lowercase() in labelSet
                val targetValid = rel.targetLabel.lowercase() in labelSet
                
                if (!typeValid) Log.w(TAG, "Invalid relation type: ${rel.relationType}")
                if (!sourceValid) Log.w(TAG, "Invalid source: ${rel.sourceLabel}")
                if (!targetValid) Log.w(TAG, "Invalid target: ${rel.targetLabel}")
                
                typeValid && sourceValid && targetValid
            }.map { it.copy(relationType = it.relationType.uppercase()) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse relationship response", e)
            null
        }
    }
    
    // ==================== EMOTIONAL CONTEXT ====================
    
    private suspend fun extractEmotionalContext(
        messagesText: String,
        assistantId: String,
        rawResponses: MutableList<String>
    ): EmotionalContext? {
        val prompt = """
Analyze the emotional context of this conversation from the USER's perspective.

Conversation:
$messagesText

Return JSON with:
- valence: -1.0 (very negative) to 1.0 (very positive)
- arousal: 0.0 (calm) to 1.0 (excited/intense)
- emotions: array of emotion words (e.g., ["happy", "excited"])

Return ONLY valid JSON, no explanation.

Example: {"valence":0.5,"arousal":0.7,"emotions":["excited","curious"]}
""".trimIndent()
        
        return executeWithRetry(
            prompt = prompt,
            assistantId = assistantId,
            rawResponses = rawResponses,
            parser = { response -> parseEmotionalContextResponse(response) }
        )
    }
    
    private fun parseEmotionalContextResponse(response: String): EmotionalContext? {
        return try {
            val jsonStart = response.indexOf('{')
            val jsonEnd = response.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1) return null
            
            val jsonStr = response.substring(jsonStart, jsonEnd + 1)
            json.decodeFromString<EmotionalContext>(jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse emotional context", e)
            null
        }
    }
    
    // ==================== RETRY LOGIC ====================
    
    private suspend fun <T> executeWithRetry(
        prompt: String,
        assistantId: String,
        rawResponses: MutableList<String>,
        parser: (String) -> T?
    ): T? {
        var lastError: String? = null
        
        repeat(MAX_RETRIES) { attempt ->
            val fullPrompt = if (lastError != null) {
                "$prompt\n\nPREVIOUS ERROR: $lastError\nPlease fix the JSON format and try again."
            } else {
                prompt
            }
            
            try {
                val response = generateText(fullPrompt, assistantId)
                rawResponses.add(response)
                
                val result = parser(response)
                if (result != null) {
                    return result
                }
                
                lastError = "Failed to parse JSON response. Ensure output is valid JSON only."
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed on attempt ${attempt + 1}", e)
                lastError = "Generation failed: ${e.message}"
            }
        }
        
        Log.w(TAG, "Failed after $MAX_RETRIES attempts")
        return null
    }
    
    private suspend fun generateText(prompt: String, assistantId: String): String {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.assistants.find { it.id.toString() == assistantId }
            ?: throw IllegalArgumentException("Assistant not found: $assistantId")
        
        // Priority: memoryModelId (global) > backgroundModelId > chatModelId
        val memoryModelId = settings.memoryModelId
        val memoryModel = settings.findModelById(memoryModelId)
        val modelId = if (memoryModel != null) memoryModelId 
                      else assistant.backgroundModelId ?: assistant.chatModelId ?: settings.chatModelId
        val model = settings.findModelById(modelId)
            ?: throw IllegalArgumentException("Model not found: $modelId")
        val providerSetting = model.findProvider(settings.providers)
            ?: throw IllegalArgumentException("Provider not found for model")
        val provider = providerManager.getProviderByType(providerSetting)
        
        val response = provider.generateText(
            providerSetting = providerSetting,
            messages = listOf(UIMessage.user(prompt)),
            params = TextGenerationParams(
                model = model,
                temperature = 0.3f  // Lower temperature for more consistent structured output
            )
        )
        
        return response.choices.firstOrNull()?.message?.toContentText()
            ?: throw Exception("Empty response from LLM")
    }
}

