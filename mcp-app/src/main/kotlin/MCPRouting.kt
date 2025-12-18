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
import kotlinx.coroutines.awaitCancellation
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
//                        val title = request.arguments?.get("title")?.jsonPrimitive?.content
                        val author = request.arguments?.get("author")?.jsonPrimitive?.content

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

                // Register add_task tool
                mcpServer.addTool(
                    name = "add_task",
                    description = """Add a new task to the task list.

**When to use**: Only when the user explicitly asks to create, add, or track a new task.

**How to use**: Generate an appropriate task title based on the conversation context. Analyze what the user is discussing or working on, and create a clear, concise task title that captures the intent.
Examples: 'Implement user authentication', 'Fix bug in payment processing', 'Review pull request #123'.
The description field is optional - use it to add relevant details if needed.""",
                    inputSchema = Tool.Input(
                        properties = buildJsonObject {
                            putJsonObject("title") {
                                put("type", JsonPrimitive("string"))
                                put("description", JsonPrimitive("Task title"))
                            }
                            putJsonObject("description") {
                                put("type", JsonPrimitive("string"))
                                put("description", JsonPrimitive("Optional task description"))
                            }
                        },
                        required = listOf("title")
                    )
                ) { request ->
                    try {
                        val title = request.arguments?.get("title")?.jsonPrimitive?.content
                        val description = request.arguments?.get("description")?.jsonPrimitive?.content

                        if (title.isNullOrBlank()) {
                            return@addTool CallToolResult(
                                content = listOf(TextContent("The 'title' parameter is required and cannot be empty.")),
                                isError = true
                            )
                        }

                        val taskId = TaskRepository.addTask(title, description)

                        CallToolResult(
                            content = listOf(TextContent("""{"id": $taskId}"""))
                        )
                    } catch (e: Exception) {
                        CallToolResult(
                            content = listOf(TextContent("Error adding task: ${e.message}")),
                            isError = true
                        )
                    }
                }

                // Register get_pending_tasks tool
                mcpServer.addTool(
                    name = "get_pending_tasks",
                    description = """Retrieve all pending (incomplete) tasks from the task list.

**When to use**: Only when the user asks to see, list, view, or check tasks. Also use before completing a task if the user asks to complete one.

**Returns**: JSON array of tasks with their IDs, titles, descriptions, and creation timestamps.""",
                    inputSchema = Tool.Input(
                        properties = buildJsonObject {},
                        required = listOf()
                    )
                ) { request ->
                    try {
                        val tasks = TaskRepository.getPendingTasks()

                        val json = Json { prettyPrint = true }
                        val tasksJson = json.encodeToString(
                            kotlinx.serialization.builtins.ListSerializer(TaskData.serializer()),
                            tasks
                        )

                        CallToolResult(
                            content = listOf(TextContent(tasksJson))
                        )
                    } catch (e: Exception) {
                        CallToolResult(
                            content = listOf(TextContent("Error getting pending tasks: ${e.message}")),
                            isError = true
                        )
                    }
                }

                // Register complete_task tool
                mcpServer.addTool(
                    name = "complete_task",
                    description = """Mark a specific task as completed.

**When to use**: Only when the user explicitly asks to complete, finish, mark as done, or close a task.

**How to use**: First call 'get_pending_tasks' to see available tasks, then analyze which task is most relevant based on conversation context. Select the appropriate task ID - do not ask the user for the ID, make an intelligent decision.""",
                    inputSchema = Tool.Input(
                        properties = buildJsonObject {
                            putJsonObject("id") {
                                put("type", JsonPrimitive("number"))
                                put("description", JsonPrimitive("Task ID to complete"))
                            }
                        },
                        required = listOf("id")
                    )
                ) { request ->
                    try {
                        val taskId = request.arguments?.get("id")?.jsonPrimitive?.int

                        if (taskId == null) {
                            return@addTool CallToolResult(
                                content = listOf(TextContent("The 'id' parameter is required and must be a number.")),
                                isError = true
                            )
                        }

                        val success = TaskRepository.completeTask(taskId)

                        if (success) {
                            CallToolResult(
                                content = listOf(TextContent("""{"success": true}"""))
                            )
                        } else {
                            CallToolResult(
                                content = listOf(TextContent("Task with ID $taskId not found.")),
                                isError = true
                            )
                        }
                    } catch (e: Exception) {
                        CallToolResult(
                            content = listOf(TextContent("Error completing task: ${e.message}")),
                            isError = true
                        )
                    }
                }

                // Register add_task_summary tool
                mcpServer.addTool(
                    name = "add_task_summary",
                    description = """Create a summary of recent task activities.

**When to use**: Only when the user explicitly asks to create, generate, or add a task summary or report.

**How to use**: Generate the summary text by analyzing recently completed tasks or discussions. Create a concise summary (2-3 sentences) with relevant details like task names and outcomes.
Example: 'Completed 3 tasks today: implemented login feature, fixed payment bug, and updated documentation. All tests passing.'""",
                    inputSchema = Tool.Input(
                        properties = buildJsonObject {
                            putJsonObject("summary_text") {
                                put("type", JsonPrimitive("string"))
                                put("description", JsonPrimitive("Summary text"))
                            }
                        },
                        required = listOf("summary_text")
                    )
                ) { request ->
                    try {
                        val summaryText = request.arguments?.get("summary_text")?.jsonPrimitive?.content

                        if (summaryText.isNullOrBlank()) {
                            return@addTool CallToolResult(
                                content = listOf(TextContent("The 'summary_text' parameter is required and cannot be empty.")),
                                isError = true
                            )
                        }

                        val summaryId = SummaryRepository.addSummary(summaryText)

                        CallToolResult(
                            content = listOf(TextContent("""{"id": $summaryId}"""))
                        )
                    } catch (e: Exception) {
                        CallToolResult(
                            content = listOf(TextContent("Error adding summary: ${e.message}")),
                            isError = true
                        )
                    }
                }

                // Register get_undelivered_summaries tool
                mcpServer.addTool(
                    name = "get_undelivered_summaries",
                    description = """Retrieve all task summaries that have not yet been delivered via SSE.

**When to use**: Only when the user asks to see, list, view, or check summaries. Also use before marking a summary as delivered.

**Returns**: JSON array with summary IDs, text content, and generation timestamps.""",
                    inputSchema = Tool.Input(
                        properties = buildJsonObject {},
                        required = listOf()
                    )
                ) { request ->
                    try {
                        val summaries = SummaryRepository.getUndeliveredSummaries()

                        val json = Json { prettyPrint = true }
                        val summariesJson = json.encodeToString(
                            kotlinx.serialization.builtins.ListSerializer(SummaryData.serializer()),
                            summaries
                        )

                        CallToolResult(
                            content = listOf(TextContent(summariesJson))
                        )
                    } catch (e: Exception) {
                        CallToolResult(
                            content = listOf(TextContent("Error getting undelivered summaries: ${e.message}")),
                            isError = true
                        )
                    }
                }

                // Register mark_summary_delivered tool
                mcpServer.addTool(
                    name = "mark_summary_delivered",
                    description = """Mark a specific summary as delivered.

**When to use**: Only when the user explicitly asks to mark a summary as delivered or sent.

**How to use**: First call 'get_undelivered_summaries' to see available summaries, then select which one to mark (typically the oldest or most relevant). Make an intelligent decision - do not ask the user for the summary ID.""",
                    inputSchema = Tool.Input(
                        properties = buildJsonObject {
                            putJsonObject("id") {
                                put("type", JsonPrimitive("number"))
                                put("description", JsonPrimitive("Summary ID to mark as delivered"))
                            }
                        },
                        required = listOf("id")
                    )
                ) { request ->
                    try {
                        val summaryId = request.arguments?.get("id")?.jsonPrimitive?.int

                        if (summaryId == null) {
                            return@addTool CallToolResult(
                                content = listOf(TextContent("The 'id' parameter is required and must be a number.")),
                                isError = true
                            )
                        }

                        val success = SummaryRepository.markSummaryDelivered(summaryId)

                        if (success) {
                            CallToolResult(
                                content = listOf(TextContent("""{"success": true}"""))
                            )
                        } else {
                            CallToolResult(
                                content = listOf(TextContent("Summary with ID $summaryId not found.")),
                                isError = true
                            )
                        }
                    } catch (e: Exception) {
                        CallToolResult(
                            content = listOf(TextContent("Error marking summary as delivered: ${e.message}")),
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

                    // Connect server to transport
                    // Note: connect() now calls start() automatically in SDK 0.8.x
                    mcpServer.connect(transport)

                    // Keep the SSE connection alive until the client disconnects
                    // This suspends forever until the coroutine is cancelled (when SSE connection closes)
                    awaitCancellation()
                } catch (e: Exception) {
                    println("SSE connection closed: ${e.message}")
                } finally {
                    // При закрытии соединения очищаем
                    if (currentTransport === transport) {
                        currentTransport = null
                    }
                }
            }

            // POST endpoint for client-to-server messages (for SSE transport)
            post("/messages") {
                val transport = currentTransport
                if (transport == null) {
                    call.respond(HttpStatusCode.NotFound, "No active MCP session")
                    return@post
                }
                try {
                    // handlePostMessage processes the message and sends response via SSE
                    // It doesn't need us to send an HTTP response - it handles everything internally
                    transport.handlePostMessage(call)
                } catch (e: Exception) {
                    println("Error in handlePostMessage: ${e.message}")
                    e.printStackTrace()
                    if (!call.response.isCommitted) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            "Error processing message: ${e.message}"
                        )
                    }
                }
            }
        } // End of route("/mcp")
    }
}