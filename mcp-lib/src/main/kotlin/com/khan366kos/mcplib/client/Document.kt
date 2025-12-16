package com.khan366kos.mcplib.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Document(
    @SerialName("author_name")
    val authorName: List<String>,
    val title: String,
)
