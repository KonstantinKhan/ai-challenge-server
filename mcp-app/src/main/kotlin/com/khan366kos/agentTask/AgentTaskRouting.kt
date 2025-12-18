package com.khan366kos.agentTask

import com.khan366kos.AgentTaskRepository
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.request.receiveText

fun Application.configureAgentTaskRouting() {
    routing {
        route("/api/agent/tasks") {
            // Получить все pending задачи
            get("/pending") {
                try {
                    val tasks = AgentTaskRepository.getPendingTasks()
                    call.respond(tasks)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to e.message)
                    )
                }
            }

            // Отметить задачу как выполненную
            post("/{id}/complete") {
                try {
                    val taskId = call.parameters["id"]?.toIntOrNull()
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Invalid task ID")
                        )

                    val success = AgentTaskRepository.completeTask(taskId)

                    if (success) {
                        call.respond(mapOf("success" to true))
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to "Task not found")
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to e.message)
                    )
                }
            }

            // Отметить задачу как failed
            post("/{id}/fail") {
                try {
                    val taskId = call.parameters["id"]?.toIntOrNull()
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Invalid task ID")
                        )

                    val errorMessage = call.receiveText()

                    val success = AgentTaskRepository.failTask(taskId, errorMessage)

                    if (success) {
                        call.respond(mapOf("success" to true))
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to "Task not found")
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to e.message)
                    )
                }
            }
        }
    }
}
