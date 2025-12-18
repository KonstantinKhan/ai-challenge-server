package com.khan366kos

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        get("/json/kotlinx-serialization") {
            call.respond(mapOf("hello" to "world"))
        }

        // SSE endpoint for task summaries
        sse("/api/summaries/stream") {
            try {
                // Get all undelivered summaries
                val summaries = SummaryRepository.getUndeliveredSummaries()

                // Send each summary as an event
                for (summary in summaries) {
                    val json = Json.encodeToString(SummaryData.serializer(), summary)
                    send(
                        ServerSentEvent(
                            data = json,
                            event = "new_summary",
                            id = summary.id.toString()
                        )
                    )

                    // Mark as delivered immediately after sending
                    SummaryRepository.markSummaryDelivered(summary.id)
                }

                // Send completion event
                send(
                    ServerSentEvent(
                        data = """{"delivered": ${summaries.size}}""",
                        event = "complete"
                    )
                )

                // Keep connection alive with heartbeats every 30 seconds
                while (true) {
                    delay(30_000)
                    send(
                        ServerSentEvent(
                            data = "heartbeat",
                            event = "ping"
                        )
                    )
                }
            } catch (e: Exception) {
                send(
                    ServerSentEvent(
                        data = """{"error": "${e.message}"}""",
                        event = "error"
                    )
                )
            }
        }
    }
}
