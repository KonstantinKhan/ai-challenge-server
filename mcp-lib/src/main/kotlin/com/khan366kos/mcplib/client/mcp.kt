package com.khan366kos.mcplib.client

import io.ktor.utils.io.streams.asInput
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonObject

fun runMcpServer() {
    val server = Server(
        Implementation(
            name = "Library",
            version = "0.0.1",
        ), ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
    )

    val transport = StdioServerTransport(
        System.`in`.asInput(),
        System.out.asSink().buffered()
    )

    val libClient = LibClient()

    runBlocking {
        server.connect(transport)

        server.addTool(
            name = "get_books",
            description = """
                Get books at the user's request
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("title") { put("type", JsonPrimitive("string")) }
                    putJsonObject("author") { put("type", JsonPrimitive("array")) }
                },
                required = listOf("title", "author")
            )
        ) { request ->
            try {
                // Extract and validate parameters
                val title = request.arguments?.get("title")?.jsonPrimitive?.content
                val authorArray = request.arguments?.get("author")?.jsonArray

                // Validate required parameters
                if (title.isNullOrEmpty()) {
                    return@addTool CallToolResult(
                        content = listOf(TextContent("The 'title' parameter is required and cannot be empty.")),
                        isError = true
                    )
                }

                // Build search query
                val query = buildString {
                    append(title)

                    // Add authors to query if provided
                    if (!authorArray.isNullOrEmpty()) {
                        append(" ")
                        append(authorArray.joinToString(" ") {
                            it.jsonPrimitive.content
                        })
                    }
                }

                // Perform search using LibClient
                val books = libClient.search(query)

                // Format results
                if (books.isEmpty()) {
                    CallToolResult(
                        content = listOf(TextContent("No books found matching query: $query"))
                    )
                } else {
                    val formattedResults = buildString {
                        appendLine("Found ${books.size} book(s):")
                        appendLine()
                        books.forEachIndexed { index, book ->
                            appendLine("${index + 1}. ${book.title}")
                            appendLine("   Authors: ${book.author.joinToString(", ")}")
                            appendLine()
                        }
                    }

                    CallToolResult(
                        content = listOf(TextContent(formattedResults))
                    )
                }

            } catch (e: Exception) {
                // Handle errors gracefully
                CallToolResult(
                    content = listOf(TextContent("Error searching for books: ${e.message}")),
                    isError = true
                )
            }
        }

        val done = Job()
        server.onClose {
            done.complete()
        }
        done.join()
    }
}