package com.example.groupbuilder.controllers

import com.example.dtos.GroupPlannedDTO
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class GroupBuilderSyncController(
    private val groupBuilderController: GroupBuilderController
) {
    @PostMapping("/group_builder")
    fun post(groupPlannedDTO: GroupPlannedDTO) {
        println("Got DTO from post request: $groupPlannedDTO")

        groupBuilderController.copyFolders(groupPlannedDTO)
    }
}