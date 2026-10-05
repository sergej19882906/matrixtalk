package com.matrix.messenger.platform

data class FileResult(
    val path: String,
    val name: String,
    val size: Long,
    val mimeType: String
)

expect class PlatformContext

expect fun pickFile(allowedTypes: List<String> = emptyList()): FileResult?

expect fun openUrl(url: String)

expect fun readFileBytes(path: String): ByteArray

expect fun writeFileBytes(path: String, bytes: ByteArray)

expect fun attachmentsDir(): String

expect fun sessionStorePath(): String

expect fun createTrixnityRepositoriesModule(): org.koin.core.module.Module
