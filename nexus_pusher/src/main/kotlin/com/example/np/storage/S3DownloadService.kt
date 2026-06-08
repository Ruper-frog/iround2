package com.example.np.storage

import com.example.np.config.S3Properties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path

@Service
class S3DownloadService(
    private val s3Client: S3Client,
    private val properties: S3Properties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun downloadToTempFile(bucketName: String, key: String): DownloadedS3File {
        validateRequest(bucketName, key)

        val downloadDir = Path(properties.tempDownloadDir)
        Files.createDirectories(downloadDir)

        val localFile = Files.createTempFile(downloadDir, TEMP_FILE_PREFIX, extensionFromKey(key))
        logger.info("Downloading S3 object bucket={} key={} to {}", bucketName, key, localFile)

        return try {
            Files.deleteIfExists(localFile)

            val response = s3Client.getObject(
                GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build(),
                ResponseTransformer.toFile(localFile)
            )

            DownloadedS3File(
                bucketName = bucketName,
                key = key,
                localPath = localFile,
                contentLength = response.contentLength(),
                contentType = response.contentType(),
                eTag = response.eTag()
            )
        } catch (ex: NoSuchKeyException) {
            deletePartialFile(localFile)
            throw S3ObjectNotFoundException(bucketName, key, ex)
        } catch (ex: S3Exception) {
            deletePartialFile(localFile)
            throw S3DownloadException("Failed to download S3 object bucket=$bucketName key=$key", ex)
        } catch (ex: SdkClientException) {
            deletePartialFile(localFile)
            throw S3DownloadException("Failed to read S3 object response bucket=$bucketName key=$key", ex)
        } catch (ex: IOException) {
            deletePartialFile(localFile)
            throw S3DownloadException("Failed to write S3 object to local temp file bucket=$bucketName key=$key", ex)
        } catch (ex: RuntimeException) {
            deletePartialFile(localFile)
            throw ex
        }
    }

    private fun validateRequest(bucketName: String, key: String) {
        require(bucketName.isNotBlank()) { "bucketName must not be blank" }
        require(key.isNotBlank()) { "key must not be blank" }
        require(!key.startsWith("/") && !key.contains("..")) {
            "key must not start with '/' or contain '..'"
        }

        if (properties.allowedBuckets.isNotEmpty()) {
            require(bucketName in properties.allowedBuckets) {
                "bucketName is not in the allowed bucket list"
            }
        }
    }

    private fun extensionFromKey(key: String): String? {
        val fileName = key.substringAfterLast('/')
        val dotIndex = fileName.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex == fileName.lastIndex) {
            return null
        }

        val extension = fileName.substring(dotIndex)
        return extension.takeIf { it.length <= MAX_EXTENSION_LENGTH && it.all(::isSafeExtensionChar) }
    }

    private fun isSafeExtensionChar(char: Char): Boolean =
        char == '.' || char == '-' || char == '_' || char.isLetterOrDigit()

    private fun deletePartialFile(localFile: Path) {
        try {
            Files.deleteIfExists(localFile)
        } catch (cleanupError: IOException) {
            logger.warn("Failed to delete partial temp file {}", localFile, cleanupError)
        }
    }

    companion object {
        private const val TEMP_FILE_PREFIX = "s3-download-"
        private const val MAX_EXTENSION_LENGTH = 32
    }
}

class S3ObjectNotFoundException(
    bucketName: String,
    key: String,
    cause: Throwable
) : RuntimeException("S3 object not found bucket=$bucketName key=$key", cause)

class S3DownloadException(
    message: String,
    cause: Throwable
) : RuntimeException(message, cause)
