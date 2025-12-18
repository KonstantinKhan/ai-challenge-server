package com.khan366kos.scheduler

import kotlinx.serialization.Serializable

@Serializable
data class SchedulerConfig(
    val enabled: Boolean = true,
    val schedule: ScheduleConfig
)

@Serializable
data class ScheduleConfig(
    val time: String,                    // "09:00"
    val timezone: String = "UTC"
)
