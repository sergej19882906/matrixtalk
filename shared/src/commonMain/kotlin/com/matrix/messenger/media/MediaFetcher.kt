package com.matrix.messenger.media

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.FetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import okio.Buffer
import okio.FileSystem

/**
 * Image model for Coil: plain https/mxc urls are loaded by the default network fetcher,
 * this type routes encrypted attachments through the Matrix media service (decryption).
 */
data class MatrixMedia(val url: String?, val encryptedFile: EncryptedFile?)

/**
 * Bridge to the active MatrixClient: set by the repository on login, cleared on logout.
 * `resolver` decrypts EncryptedFile payloads; `mxcResolver` downloads plain mxc://
 * media through the AUTHENTICATED client (modern /media/v1 endpoint). The legacy
 * unauthenticated download endpoint returns 404 on enforcing homeservers (e.g.
 * matrix.org), so plain https urls must not be used for Matrix content.
 */
object MediaBytesProvider {
    var resolver: (suspend (EncryptedFile) -> ByteArray)? = null
    var mxcResolver: (suspend (String) -> ByteArray)? = null
}

class MatrixMediaFetcher(
    private val media: MatrixMedia
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bytes = when (val file = media.encryptedFile) {
            null -> {
                val mxc = media.url?.takeIf { it.startsWith("mxc://") }
                    ?: error("MatrixMedia url must be an mxc:// uri: ${media.url}")
                val mxcResolver = MediaBytesProvider.mxcResolver
                    ?: error("MediaBytesProvider is not initialized")
                mxcResolver(mxc)
            }
            else -> {
                val resolver = MediaBytesProvider.resolver
                    ?: error("MediaBytesProvider is not initialized")
                resolver(file)
            }
        }
        return SourceFetchResult(
            source = ImageSource(Buffer().apply { write(bytes) }, FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.NETWORK
        )
    }

    class Factory : Fetcher.Factory<MatrixMedia> {
        // Handles BOTH encrypted and plain mxc media; coils network fetcher cannot
        // load Matrix content at all (legacy endpoint 404 + no auth header support).
        override fun create(data: MatrixMedia, options: Options, imageLoader: ImageLoader): Fetcher? =
            MatrixMediaFetcher(data)
    }
}

fun setupImageLoader() {
    SingletonImageLoader.setSafe { platformContext ->
        ImageLoader.Builder(platformContext)
            .components {
                add(MatrixMediaFetcher.Factory())
            }
            .build()
    }
}
