package com.khan366kos.mcplib.client

import kotlinx.serialization.Serializable

@Serializable
data class Book(
    val title: String,
    val author: List<String>,
)
