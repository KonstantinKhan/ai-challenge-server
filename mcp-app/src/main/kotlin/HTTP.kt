package com.khan366kos

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureHTTP() {
    install(CORS) {
        // В дев-режиме разрешаем любые источники,
        // чтобы исключить проблемы с CORS-конфигурацией
        anyHost()

        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)

        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        // Разрешаем нестандартный заголовок, который шлёт MCP-клиент
        allowHeader("mcp-protocol-version")
        allowNonSimpleContentTypes = true

        // credentials не включаем, чтобы браузер не ругался на anyHost() + cookies
        allowCredentials = false
    }
}
