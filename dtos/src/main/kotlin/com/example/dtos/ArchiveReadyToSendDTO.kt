package com.example.dtos

data class ArchiveReadyToSendDTO(
    val groupKey: String,
    val sourcePath: String,
    val destinationPath: String
)