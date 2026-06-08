package com.example.mygb.controllers

import com.example.dtos.GroupPlannedDTO
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class SorterController {

    @RabbitListener(queues = ["\${rabbitmq.queue}"])
    fun listen(message: GroupPlannedDTO) {
        println("Success! Received DTO: ${message.groupKey}")
    }
}
