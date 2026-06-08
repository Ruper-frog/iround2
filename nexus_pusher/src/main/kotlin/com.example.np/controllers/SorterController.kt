package com.example.np.controllers

import com.example.dtos.NexusPusherDTO
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class SorterController {

    @RabbitListener(queues = ["\${rabbitmq.queue}"])
    fun listen(message: NexusPusherDTO) {
        println("Success! Received DTO:\n bucketName: ${message.bucketName} and key: ${message.key}")
    }
}
