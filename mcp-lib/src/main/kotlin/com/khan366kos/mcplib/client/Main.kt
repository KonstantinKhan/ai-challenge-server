package com.khan366kos.mcplib.client

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val libClient = LibClient()
    println(libClient.search("Алиса"))
}