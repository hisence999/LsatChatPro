package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class Skill(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val folderId: Uuid? = null,
)
