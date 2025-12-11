package me.rerere.rikkahub.data.memory.engine

import me.rerere.rikkahub.data.memory.entity.MemoryNode
import me.rerere.rikkahub.data.memory.entity.MemoryTier
import kotlin.math.ln
import kotlin.math.pow

/**
 * ACT-R-inspired activation engine for calculating memory accessibility.
 * 
 * Base-Level Activation (BLA) formula from ACT-R:
 * B_i = ln(Σ(t_j^(-d))) + β
 * 
 * Where:
 * - t_j = time since j-th access (in days)
 * - d = decay rate (typically 0.5)
 * - β = base constant
 * 
 * This determines how "activated" a memory is based on:
 * - Recency: how recently it was accessed
 * - Frequency: how often it has been accessed
 * - Tier: CORE memories get bonus activation
 */
object ActivationEngine {
    
    // Activation thresholds for tier transitions
    const val PROMOTE_TO_CORE_THRESHOLD = 5.0f
    const val DEMOTE_TO_ARCHIVAL_THRESHOLD = -2.0f
    const val PROMOTE_FROM_ARCHIVAL_THRESHOLD = 0.5f
    
    // Base constants
    private const val BASE_CONSTANT = 0f
    private const val MS_PER_DAY = 1000.0 * 60 * 60 * 24
    
    /**
     * Calculate full ACT-R base-level activation given all access timestamps.
     * More accurate but requires storing all access times.
     */
    fun calculateFullActivation(
        accessTimestamps: List<Long>,
        decayRate: Float = 0.5f,
        baseConstant: Float = BASE_CONSTANT,
        currentTime: Long = System.currentTimeMillis()
    ): Float {
        if (accessTimestamps.isEmpty()) return baseConstant
        
        val sum = accessTimestamps.sumOf { timestamp ->
            val ageMs = currentTime - timestamp
            val ageDays = ageMs / MS_PER_DAY
            if (ageDays <= 0) 1.0 else ageDays.pow(-decayRate.toDouble())
        }
        
        return if (sum > 0) (ln(sum) + baseConstant).toFloat() else baseConstant
    }
    
    /**
     * Calculate simplified activation using only accessCount and lastAccessedAt.
     * This is an approximation that doesn't require storing all timestamps.
     * 
     * Components:
     * - Frequency boost: ln(n + 1) where n = access count
     * - Recency boost: 1 / (1 + age * decay)
     * - Tier bonus: CORE gets +2, ARCHIVAL gets -1
     */
    fun calculateActivation(node: MemoryNode): Float {
        return calculateActivation(
            lastAccessedAt = node.lastAccessedAt,
            accessCount = node.accessCount,
            decayRate = node.decayRate,
            tier = node.tier
        )
    }
    
    fun calculateActivation(
        lastAccessedAt: Long,
        accessCount: Int,
        decayRate: Float = 0.5f,
        tier: MemoryTier = MemoryTier.RECALL,
        currentTime: Long = System.currentTimeMillis()
    ): Float {
        val ageMs = currentTime - lastAccessedAt
        val ageDays = ageMs / MS_PER_DAY
        
        // Frequency component: more accesses = higher activation
        val frequencyBoost = ln((accessCount + 1).toDouble()).toFloat()
        
        // Recency component: recent = higher activation
        // Uses a decay that halves every (1/decayRate) days
        val recencyBoost = (1.0 / (1.0 + ageDays * decayRate)).toFloat()
        
        // Tier bonus: CORE items should always be easily accessible
        val tierBonus = when (tier) {
            MemoryTier.CORE -> 2.0f
            MemoryTier.RECALL -> 0f
            MemoryTier.ARCHIVAL -> -1.0f
        }
        
        return frequencyBoost + recencyBoost + tierBonus
    }
    
    /**
     * Calculate activation with emotional context matching.
     * Memories with similar emotional valence get a boost.
     */
    fun calculateActivationWithEmotion(
        node: MemoryNode,
        currentEmotionalValence: Float? = null,
        currentEmotionalArousal: Float? = null,
        emotionWeight: Float = 0.3f
    ): Float {
        val baseActivation = calculateActivation(node)
        
        if (currentEmotionalValence == null) return baseActivation
        
        // Calculate emotional similarity
        val valenceDiff = kotlin.math.abs(node.emotionalValence - currentEmotionalValence)
        val arousalDiff = if (currentEmotionalArousal != null) {
            kotlin.math.abs(node.emotionalArousal - currentEmotionalArousal)
        } else 0f
        
        // Emotional match boost: higher when emotions are similar
        val emotionalSimilarity = 1f - (valenceDiff + arousalDiff) / 2f
        val emotionalBoost = emotionalSimilarity * emotionWeight
        
        return baseActivation + emotionalBoost
    }
    
    /**
     * Determine if a node should be promoted or demoted based on activation.
     */
    fun suggestTierChange(node: MemoryNode): MemoryTier? {
        val activation = calculateActivation(node)
        
        return when (node.tier) {
            MemoryTier.ARCHIVAL -> {
                if (activation >= PROMOTE_FROM_ARCHIVAL_THRESHOLD) MemoryTier.RECALL else null
            }
            MemoryTier.RECALL -> {
                when {
                    activation >= PROMOTE_TO_CORE_THRESHOLD -> MemoryTier.CORE
                    activation <= DEMOTE_TO_ARCHIVAL_THRESHOLD -> MemoryTier.ARCHIVAL
                    else -> null
                }
            }
            MemoryTier.CORE -> {
                // CORE items can be manually demoted but not automatically
                null
            }
        }
    }
    
    /**
     * Calculate decay factor for a given amount of time.
     * Used for projecting future activation levels.
     */
    fun calculateDecayFactor(
        daysElapsed: Double,
        decayRate: Float = 0.5f
    ): Float {
        return (1.0 / (1.0 + daysElapsed * decayRate)).toFloat()
    }
    
    /**
     * Estimate when a node's activation will drop below a threshold.
     * Useful for predicting when memories will be forgotten.
     */
    fun estimateTimeToThreshold(
        node: MemoryNode,
        threshold: Float,
        currentTime: Long = System.currentTimeMillis()
    ): Long? {
        val currentActivation = calculateActivation(node)
        if (currentActivation <= threshold) return 0L
        
        // This is an approximation - solve for t in the recency decay formula
        val frequencyBoost = ln((node.accessCount + 1).toDouble()).toFloat()
        val tierBonus = when (node.tier) {
            MemoryTier.CORE -> 2.0f
            MemoryTier.RECALL -> 0f
            MemoryTier.ARCHIVAL -> -1.0f
        }
        
        // Threshold for recency component
        val targetRecency = threshold - frequencyBoost - tierBonus
        if (targetRecency <= 0) return null // Will never reach threshold
        
        // Solve: targetRecency = 1 / (1 + ageDays * decay)
        // ageDays = (1/targetRecency - 1) / decay
        val currentAgeDays = (currentTime - node.lastAccessedAt) / MS_PER_DAY
        val targetAgeDays = (1.0 / targetRecency - 1) / node.decayRate
        val additionalDays = targetAgeDays - currentAgeDays
        
        return if (additionalDays > 0) {
            currentTime + (additionalDays * MS_PER_DAY).toLong()
        } else {
            0L
        }
    }
}
