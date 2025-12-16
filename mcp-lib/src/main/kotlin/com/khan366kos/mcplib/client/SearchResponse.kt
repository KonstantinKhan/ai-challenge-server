package com.khan366kos.mcplib.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer

@Serializable
data class SearchResponse(
    val numFound: Int,
    val docs: List<Document>

)
