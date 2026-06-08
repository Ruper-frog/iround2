package com.example.np

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.example"])
class NexusPusherApplication

fun main(args: Array<String>) {
    runApplication<NexusPusherApplication>(*args)
}
