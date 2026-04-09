package com.pglab

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PgLabApplication

fun main(args: Array<String>) {
    runApplication<PgLabApplication>(*args)
}
