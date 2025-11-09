package org.elly

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class EllyApplication

fun main(args: Array<String>) {
    runApplication<EllyApplication>(*args)
}
