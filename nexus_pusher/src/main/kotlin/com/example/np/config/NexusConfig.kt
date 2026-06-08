package com.example.np.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class NexusConfig {

    @Bean
    fun nexusRestClient(builder: RestClient.Builder, properties: NexusProperties): RestClient =
        builder
            .baseUrl(properties.baseUrl.trimEnd('/'))
            .defaultHeaders { headers ->
                headers.setBasicAuth(properties.username, properties.password)
            }
            .build()
}
