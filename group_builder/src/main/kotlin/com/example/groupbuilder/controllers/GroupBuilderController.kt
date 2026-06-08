package com.example.groupbuilder.controllers

import com.example.dtos.ArchiveReadyToSendDTO
import com.example.dtos.GroupPlannedDTO
import com.example.groupbuilder.services.GroupBuilderService
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component


@Component
class GroupBuilderController(
    private val rabbitTemplate: RabbitTemplate,
    private val groupBuilderService: GroupBuilderService,

    @Value("\${folder.destination.root}")
    private val rootFolder: String,
    @Value("\${rabbitmq.exchange}")
    private val exchange: String,
    @Value("\${rabbitmq.routing_key}")
    private val routingKey: String
) {

    fun copyFolders(groupPlannedDTO: GroupPlannedDTO) {
        val destinationFolder = rootFolder + "/" + groupPlannedDTO.groupKey
        println("create destinationFolder: $destinationFolder")

        groupPlannedDTO.sourceFilesPaths.forEach { sourceFilePath ->
            val fileName = sourceFilePath.substringAfterLast('/')
            println("get the file name: $fileName")

            val destinationFilePath = "$destinationFolder/$fileName"
            println("create dest path: $destinationFilePath")

            groupBuilderService.copySourceToDestination(sourceFilePath, destinationFilePath)
            rabbitTemplate.convertAndSend(
                exchange,
                routingKey,
                ArchiveReadyToSendDTO(
                    groupPlannedDTO.groupKey,
                    sourceFilePath,
                    destinationFilePath
                )
            )
        }
    }
}
