package com.example.np.messaging

import jakarta.validation.constraints.NotBlank

data class S3FileMessage(
    @field:NotBlank
    val bucketName: String,

    @field:NotBlank
    val key: String
)
