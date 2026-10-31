package dev.animeshvarma.aetherion.network.models

import kotlinx.serialization.Serializable

@Serializable
data class BloksAction(
    val action: String,
    val payload: Map<String, String>? = null
)