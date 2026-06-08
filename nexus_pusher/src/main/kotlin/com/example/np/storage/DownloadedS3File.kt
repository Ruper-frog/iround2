package com.example.np.storage

import java.nio.file.Path

data class DownloadedS3File(
    val bucketName: String,
    val key: String,
    val localPath: Path,
    val contentLength: Long?,
    val contentType: String?,
    val eTag: String?
)
