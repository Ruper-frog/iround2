package com.example.np.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.nexus")
data class NexusProperties(
    val baseUrl: String,
    val repository: String,
    val username: String,
    val password: String
)
