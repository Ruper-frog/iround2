package com.example.np.messaging

import com.example.np.config.RabbitConfig
import com.example.np.nexus.NexusUploadService
import com.example.np.storage.DownloadedS3File
import com.example.np.storage.S3DownloadService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import java.nio.file.Files

@Component
@Validated
class NexusUploadListener(
    private val s3DownloadService: S3DownloadService,
    private val nexusUploadService: NexusUploadService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [RabbitConfig.NEXUS_UPLOAD_QUEUE])
    fun handle(@Valid message: S3FileMessage) {
        var downloadedFile: DownloadedS3File? = null

        try {
            logger.info("Received Nexus upload message for bucket={} key={}", message.bucketName, message.key)

            downloadedFile = s3DownloadService.downloadToTempFile(message.bucketName, message.key)

            logger.info(
                "Downloaded object to localPath={} size={} contentType={} eTag={}",
                downloadedFile.localPath,
                downloadedFile.contentLength,
                downloadedFile.contentType,
                downloadedFile.eTag
            )

            val uploadResult = nexusUploadService.upload(downloadedFile)
            logger.info(
                "Uploaded file to Nexus repository={} key={} statusCode={}",
                uploadResult.repository,
                uploadResult.key,
                uploadResult.statusCode
            )
        } finally {
            downloadedFile?.let {
                Files.deleteIfExists(it.localPath)
                logger.info("Deleted temporary file: {}", it.localPath)
            }
        }
    }
}
