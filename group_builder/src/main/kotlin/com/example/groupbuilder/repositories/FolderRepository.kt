package com.example.groupbuilder.repositories
import org.springframework.stereotype.Repository
import java.io.File

@Repository
class FolderRepository {
    fun copyFile(sourcePath: String, destinationPath: String) {
        val source = File(sourcePath)
        val destination = File(destinationPath)

        destination.parentFile?.mkdirs()

        source.copyTo(destination, overwrite = true)
    }
}