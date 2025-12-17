package com.khan366kos

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import com.khan366kos.mcplib.client.LibClient

// Store current transport globally (single client)
private var currentTransport: SseServerTransport? = null

fun Application.configureMCPRouting() {
    install(SSE)

    val libClient = LibClient()

    routing {
        route("/mcp") {
            // GET request - will handle SSE connections
            // Note: The SSE route needs to come before the general GET route to avoid conflicts
            sse {
                // Create MCP Server
                val mcpServer = Server(
                    Implementation(
                        name = "Library",
                        version = "0.0.1"
                    ),
                    ServerOptions(
                        capabilities = ServerCapabilities(
                            tools = ServerCapabilities.Tools(listChanged = true)
                        )
                    )
                )

                // Register get_books tool
                mcpServer.addTool(
                    name = "get_books",
                    description = "Get books at the user's request",
                    inputSchema = Tool.Input(
                        properties = buildJsonObject {
//                            putJsonObject("title") {
//                                put("type", JsonPrimitive("string"))
//                                put("description", JsonPrimitive("Book title to search"))
//                            }
                            putJsonObject("author") {
                                put("type", JsonPrimitive("string"))
                                put("description", JsonPrimitive("Author names"))
//                                putJsonObject("items") {
//                                    put("type", JsonPrimitive("string"))
//                                }
                            }
                        },
                        required = listOf("author")
                    )
                ) { request ->
                    try {
//                        val title = request.arguments["title"]?.jsonPrimitive?.content
                        val author = request.arguments["author"]?.jsonPrimitive?.content

                        if (author.isNullOrEmpty()) {
                            return@addTool CallToolResult(
                                content = listOf(TextContent("The 'author' parameter is required and cannot be empty.")),
                                isError = true
                            )
                        }

                        val query = buildString {
                            append(author)
//                            if (!authorArray.isNullOrEmpty()) {
//                                append(" ")
//                                append(authorArray.joinToString(" ") {
//                                    it.jsonPrimitive.content
//                                })
//                            }
                        }

                        val books = libClient.search(query)

                        println(books.filter { it.hasScan })

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
                        CallToolResult(
                            content = listOf(TextContent("Error searching for books: ${e.message}")),
                            isError = true
                        )
                    }
                }

                // Create SSE transport for this session
                val transport = SseServerTransport(
                    endpoint = "/mcp/messages",
                    session = this
                )

                // Запоминаем текущий транспорт
                currentTransport = transport

                try {
                    // Отправляем endpoint клиенту (без sessionId)
                    send(
                        ServerSentEvent(
                            data = "/mcp/messages",
                            event = "endpoint",
                            id = "endpoint"
                        )
                    )

                    // Connect server to transport in a coroutine scope
//                    launch {
                        mcpServer.connect(transport)
                        // Keep connection alive
                        transport.start()
//                    }
                } finally {
                    // При закрытии соединения очищаем
                    if (currentTransport === transport) {
                        currentTransport = null
                    }
                }
            }
        }

        // CORS preflight for client-to-server messages (for browsers)
        options("/mcp/messages") {
            call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "http://localhost:5173")
            call.response.headers.append(HttpHeaders.AccessControlAllowMethods, "GET, POST, OPTIONS")
            call.response.headers.append(HttpHeaders.AccessControlAllowHeaders, "Content-Type")
            call.response.headers.append(HttpHeaders.AccessControlAllowCredentials, "true")
            call.respond(HttpStatusCode.OK)
        }

        // POST endpoint for client-to-server messages (for SSE transport)
        post("/mcp/messages") {
            val transport = currentTransport
            if (transport == null) {
                call.respond(HttpStatusCode.NotFound, "No active MCP session")
                return@post
            }
            try {
                transport.handlePostMessage(call)
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    "Error processing message: ${e.message}"
                )
            }
        }
    }
}