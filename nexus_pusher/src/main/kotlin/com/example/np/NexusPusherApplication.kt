package com.example.np

import com.example.np.config.NexusProperties
import com.example.np.config.S3Properties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(S3Properties::class, NexusProperties::class)
class NexusPusherApplication

fun main(args: Array<String>) {
    runApplication<NexusPusherApplication>(*args)
}
