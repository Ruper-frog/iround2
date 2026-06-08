package com.example.np.nexus

import com.example.np.config.NexusProperties
import com.example.np.storage.DownloadedS3File
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.util.UriComponentsBuilder

@Service
class NexusUploadService(
    private val nexusRestClient: RestClient,
    private val properties: NexusProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun upload(downloadedFile: DownloadedS3File): NexusUploadResult {
        validateKey(downloadedFile.key)

        val uri = UriComponentsBuilder.fromPath("/")
            .pathSegment("repository", properties.repository)
            .pathSegment(*downloadedFile.key.split('/').filter { it.isNotBlank() }.toTypedArray())
            .build()
            .toUriString()

        logger.info(
            "Uploading file to Nexus repository={} key={} localPath={}",
            properties.repository,
            downloadedFile.key,
            downloadedFile.localPath
        )

        return try {
            val response = nexusRestClient.put()
                .uri(uri)
                .contentType(mediaType(downloadedFile.contentType))
                .body(FileSystemResource(downloadedFile.localPath))
                .retrieve()
                .toBodilessEntity()

            NexusUploadResult(
                repository = properties.repository,
                key = downloadedFile.key,
                statusCode = response.statusCode.value()
            )
        } catch (ex: RestClientResponseException) {
            throw NexusUploadException(
                "Nexus upload failed repository=${properties.repository} key=${downloadedFile.key} " +
                    "status=${ex.statusCode.value()} response=${ex.responseBodyAsString}",
                ex
            )
        } catch (ex: ResourceAccessException) {
            throw NexusUploadException(
                "Could not connect to Nexus at ${properties.baseUrl} while uploading key=${downloadedFile.key}",
                ex
            )
        }
    }

    private fun validateKey(key: String) {
        require(key.isNotBlank()) { "Nexus upload key must not be blank" }
        require(!key.startsWith("/") && !key.contains("..")) {
            "Nexus upload key must not start with '/' or contain '..'"
        }
    }

    private fun mediaType(contentType: String?): MediaType =
        contentType
            ?.takeIf { it.isNotBlank() }
            ?.let(MediaType::parseMediaType)
            ?: MediaType.APPLICATION_OCTET_STREAM
}

data class NexusUploadResult(
    val repository: String,
    val key: String,
    val statusCode: Int
)

class NexusUploadException(
    message: String,
    cause: Throwable
) : RuntimeException(message, cause)
