package com.khan366kos

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import java.io.File

// Table definitions
object Tasks : Table("tasks") {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 500)
    val description = text("description").nullable()
    val status = varchar("status", 20)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object TaskSummaries : Table("task_summaries") {
    val id = integer("id").autoIncrement()
    val summaryText = text("summary_text")
    val generatedAt = timestamp("generated_at")
    val delivered = bool("delivered").default(false)
    val deliveredAt = timestamp("delivered_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object PendingAgentTasks : Table("pending_agent_tasks") {
    val id = integer("id").autoIncrement()
    val type = varchar("type", 50)
    val params = text("params").nullable()
    val status = varchar("status", 20)
    val createdAt = timestamp("created_at")
    val completedAt = timestamp("completed_at").nullable()
    val error = text("error").nullable()

    override val primaryKey = PrimaryKey(id)
}

// Database initialization singleton
object DatabaseFactory {
    private var initialized = false

    fun init() {
        if (initialized) return

        val dbFile = File("mcp-app/tasks.db")
        val url = "jdbc:sqlite:${dbFile.absolutePath}"

        Database.connect(url, driver = "org.sqlite.JDBC")

        transaction {
            SchemaUtils.create(Tasks, TaskSummaries, PendingAgentTasks)
        }

        initialized = true
    }
}

// Task repository
object TaskRepository {
    fun addTask(title: String, description: String?): Int {
        return transaction {
            val now = Clock.System.now()
            val id = Tasks.insert {
                it[Tasks.title] = title
                it[Tasks.description] = description
                it[Tasks.status] = "pending"
                it[Tasks.createdAt] = now
                it[Tasks.updatedAt] = now
            } get Tasks.id
            id
        }
    }

    fun getPendingTasks(): List<TaskData> {
        return transaction {
            Tasks.selectAll()
                .where { Tasks.status eq "pending" }
                .orderBy(Tasks.createdAt, SortOrder.ASC)
                .map { row ->
                    TaskData(
                        id = row[Tasks.id],
                        title = row[Tasks.title],
                        description = row[Tasks.description],
                        status = row[Tasks.status],
                        createdAt = row[Tasks.createdAt].toString()
                    )
                }
        }
    }

    fun completeTask(taskId: Int): Boolean {
        return transaction {
            val updated = Tasks.update({ Tasks.id eq taskId }) {
                it[status] = "completed"
                it[updatedAt] = Clock.System.now()
            }
            updated > 0
        }
    }
}

// Summary repository
object SummaryRepository {
    fun addSummary(summaryText: String): Int {
        return transaction {
            val id = TaskSummaries.insert {
                it[TaskSummaries.summaryText] = summaryText
                it[TaskSummaries.generatedAt] = Clock.System.now()
                it[TaskSummaries.delivered] = false
            } get TaskSummaries.id
            id
        }
    }

    fun getUndeliveredSummaries(): List<SummaryData> {
        return transaction {
            TaskSummaries.selectAll()
                .where { TaskSummaries.delivered eq false }
                .orderBy(TaskSummaries.generatedAt, SortOrder.ASC)
                .map { row ->
                    SummaryData(
                        id = row[TaskSummaries.id],
                        summaryText = row[TaskSummaries.summaryText],
                        generatedAt = row[TaskSummaries.generatedAt].toString()
                    )
                }
        }
    }

    fun markSummaryDelivered(summaryId: Int): Boolean {
        return transaction {
            val updated = TaskSummaries.update({ TaskSummaries.id eq summaryId }) {
                it[delivered] = true
                it[deliveredAt] = Clock.System.now()
            }
            updated > 0
        }
    }
}

// Data classes for serialization
@Serializable
data class TaskData(
    val id: Int,
    val title: String,
    val description: String?,
    val status: String,
    val createdAt: String
)

@Serializable
data class SummaryData(
    val id: Int,
    val summaryText: String,
    val generatedAt: String
)

@Serializable
data class AgentTaskData(
    val id: Int,
    val type: String,
    val params: String?,
    val status: String,
    val createdAt: String
)

// Agent task repository
object AgentTaskRepository {
    fun createTask(type: String, params: String? = null): Int {
        return transaction {
            val id = PendingAgentTasks.insert {
                it[PendingAgentTasks.type] = type
                it[PendingAgentTasks.params] = params
                it[PendingAgentTasks.status] = "pending"
                it[PendingAgentTasks.createdAt] = Clock.System.now()
            } get PendingAgentTasks.id
            id
        }
    }

    fun getPendingTasks(): List<AgentTaskData> {
        return transaction {
            PendingAgentTasks.selectAll()
                .where { PendingAgentTasks.status eq "pending" }
                .orderBy(PendingAgentTasks.createdAt, SortOrder.ASC)
                .map { row ->
                    AgentTaskData(
                        id = row[PendingAgentTasks.id],
                        type = row[PendingAgentTasks.type],
                        params = row[PendingAgentTasks.params],
                        status = row[PendingAgentTasks.status],
                        createdAt = row[PendingAgentTasks.createdAt].toString()
                    )
                }
        }
    }

    fun completeTask(taskId: Int): Boolean {
        return transaction {
            val updated = PendingAgentTasks.update({ PendingAgentTasks.id eq taskId }) {
                it[status] = "completed"
                it[completedAt] = Clock.System.now()
            }
            updated > 0
        }
    }

    fun failTask(taskId: Int, errorMessage: String): Boolean {
        return transaction {
            val updated = PendingAgentTasks.update({ PendingAgentTasks.id eq taskId }) {
                it[status] = "failed"
                it[completedAt] = Clock.System.now()
                it[error] = errorMessage
            }
            updated > 0
        }
    }
}