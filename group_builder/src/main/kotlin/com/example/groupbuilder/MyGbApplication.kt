package com.example.groupbuilder

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.example"])
class MyGbApplication

fun main(args: Array<String>) {
	runApplication<MyGbApplication>(*args)
}