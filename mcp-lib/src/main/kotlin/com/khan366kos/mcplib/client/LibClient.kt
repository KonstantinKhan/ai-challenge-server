package com.khan366kos.mcplib.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class LibClient {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    suspend fun search(query: String): List<Book> {
        val response = httpClient.get("https://openlibrary.org/search.json") {
            url {
                parameters.append("q", "author_name:$query")
            }
        }
        val results = response.body<SearchResponse>().docs
        return results.map {
            Book(
                it.title,
                it.authorName,
                hasScan = it.publicScanB
            )
        }.filter { it.hasScan }
    }
}