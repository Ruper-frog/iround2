package com.example.groupbuilder.services

import com.example.groupbuilder.repositories.FolderRepository
import org.springframework.stereotype.Service

@Service
class GroupBuilderService(
    private val folderRepository: FolderRepository
) {
    fun copySourceToDestination(sourcePath: String, destinationPath: String){
        println("got source $sourcePath and dest $destinationPath  in service")
        folderRepository.copyFile(sourcePath, destinationPath)
    }
}