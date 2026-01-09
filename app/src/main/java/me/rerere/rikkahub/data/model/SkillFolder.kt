package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class SkillFolder(
    val id: Uuid = Uuid.random(),
    val name: String = "",
)

