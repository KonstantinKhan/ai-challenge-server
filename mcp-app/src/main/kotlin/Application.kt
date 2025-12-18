package com.khan366kos

import io.ktor.server.application.*
import com.khan366kos.agentTask.configureAgentTaskRouting
import com.khan366kos.scheduler.SchedulerConfig
import com.khan366kos.scheduler.ScheduleConfig
import com.khan366kos.scheduler.TaskScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init()
    configureHTTP()
    configureSerialization()
    configureMCPRouting()
    configureRouting()
    configureAgentTaskRouting()
    configureScheduler()
}

fun Application.configureScheduler() {
    // Парсинг конфига из application.yaml
    val enabled = environment.config.propertyOrNull("scheduler.enabled")?.getString()?.toBoolean() ?: false
    val time = environment.config.propertyOrNull("scheduler.schedule.time")?.getString() ?: "09:00"
    val timezone = environment.config.propertyOrNull("scheduler.schedule.timezone")?.getString() ?: "UTC"

    val config = SchedulerConfig(
        enabled = enabled,
        schedule = ScheduleConfig(time = time, timezone = timezone)
    )

    if (!config.enabled) {
        log.info("Task Scheduler is disabled")
        return
    }

    val scheduler = TaskScheduler(config)

    // Запуск в отдельном scope
    val schedulerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    scheduler.start(schedulerScope)

    // Остановка при shutdown
    environment.monitor.subscribe(ApplicationStopped) {
        scheduler.stop()
    }
}
