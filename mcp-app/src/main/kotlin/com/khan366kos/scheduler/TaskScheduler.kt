package com.khan366kos.scheduler

import com.khan366kos.AgentTaskRepository
import kotlinx.coroutines.*
import kotlinx.datetime.*

class TaskScheduler(private val config: SchedulerConfig) {
    private var schedulerJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (!config.enabled) {
            println("Task Scheduler is disabled in configuration")
            return
        }

        schedulerJob = scope.launch {
            println("Task Scheduler started. Schedule: ${config.schedule.time}")

            while (isActive) {
                val nextRunTime = calculateNextRunTime()
                val delay = nextRunTime - Clock.System.now()

                println("Next scheduler run at: $nextRunTime (in ${delay.inWholeMinutes} minutes)")

                if (delay.isPositive()) {
                    delay(delay)
                }

                if (isActive) {
                    runScheduledTask()
                }
            }
        }
    }

    private suspend fun runScheduledTask() {
        println("=== Creating agent task for summary generation ===")

        try {
            val taskId = AgentTaskRepository.createTask(
                type = "generate_summary",
                params = null
            )

            println("Created agent task with ID: $taskId")
            println("React app will process this task when user opens the application")

        } catch (e: Exception) {
            println("Error creating agent task: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun calculateNextRunTime(): Instant {
        val now = Clock.System.now()
        val timezone = TimeZone.of(config.schedule.timezone)
        val nowLocal = now.toLocalDateTime(timezone)

        // Парсим время из конфига (формат "HH:mm")
        val (hour, minute) = config.schedule.time.split(":").map { it.toInt() }

        // Создаем время на сегодня
        var nextRun = LocalDateTime(
            nowLocal.year,
            nowLocal.monthNumber,
            nowLocal.dayOfMonth,
            hour,
            minute,
            0
        ).toInstant(timezone)

        // Если время уже прошло сегодня, планируем на завтра
        if (nextRun <= now) {
            nextRun = nextRun.plus(1, DateTimeUnit.DAY, timezone)
        }

        return nextRun
    }

    fun stop() {
        schedulerJob?.cancel()
        println("Task Scheduler stopped")
    }
}
