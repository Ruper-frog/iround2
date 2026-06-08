package com.example.groupbuilder.controllers

import com.example.dtos.ArchiveReadyToSendDTO
import com.example.dtos.GroupPlannedDTO
import com.example.groupbuilder.services.GroupBuilderService
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component


@Component
class GroupBuilderAsyncController(
    private val groupBuilderController: GroupBuilderController
) {

    @RabbitListener(queues = ["\${rabbitmq.queue}"])
    fun listen(groupPlannedDTO: GroupPlannedDTO) {
        println("Got DTO from rabbit: $groupPlannedDTO")

        groupBuilderController.copyFolders(groupPlannedDTO)
    }
}
