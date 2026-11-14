package com.personalmedia.android

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.FileOutputStream
import java.io.IOException

/** SAF folder-based storage: never assumes all-files permission or raw filesystem paths. */
internal object DownloadStorage {
    fun prepare(ctx: Context, item: DownloadQueueItem): Uri {
        if (item.destinationUri.isNotBlank()) return Uri.parse(item.destinationUri)
        val treeUri = Uri.parse(item.treeUri)
        val granted = ctx.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }
        if (!granted) throw IOException("Downloads folder permission lost; reselect the folder and retry")
        val folder = DocumentFile.fromTreeUri(ctx, treeUri)
            ?.takeIf { it.isDirectory && it.canWrite() }
            ?: throw IOException("Downloads folder unavailable; reconnect storage")
        val mime = when (item.filename.substringAfterLast('.', "").lowercase()) {
            "mp4", "m4v" -> "video/mp4"; "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"; "mkv" -> "video/x-matroska"
            "ts" -> "video/mp2t"; else -> "application/octet-stream"
        }
        return folder.createFile(mime, item.filename)?.uri
            ?: throw IOException("Storage provider could not create a download document")
    }
    /** SAF providers are not universally seekable. Refuse a broken resume rather
     * than quietly appending corrupt bytes. Caller closes both stream and descriptor. */
    fun openAt(ctx: Context, uri: Uri, offset: Long): SeekableOutput {
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "rw")
            ?: throw IOException("Download destination is no longer writable")
        try {
            val output = FileOutputStream(pfd.fileDescriptor)
            val channel = output.channel
            if (offset == 0L) channel.truncate(0L)
            val onDisk = channel.size()
            val staleTail = DownloadQueuePolicy.uncommittedTail(onDisk, offset)
                ?: throw IOException("Partial file is shorter than saved progress; select Restart")
            // A crash can leave uncheckpointed bytes beyond the saved offset.
            // Trimming the tail before resuming prevents stale bytes from surviving
            // if the new response ends earlier than the old interrupted response.
            if (staleTail > 0L) channel.truncate(offset)
            channel.position(offset)
            if (channel.position() != offset) throw IOException("Storage provider does not support seeking")
            return SeekableOutput(pfd, output)
        } catch (e: Exception) {
            runCatching { pfd.close() }
            throw e
        }
    }
    /** Size of the saved SAF document, -1 if the provider cannot report it.
     * A missing/revoked document throws instead of being called complete. */
    fun documentSize(ctx: Context, uri: Uri): Long {
        val descriptor = ctx.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Saved download is unavailable; reconnect storage")
        return descriptor.use { pfd ->
            pfd.statSize.takeIf { it >= 0L } ?: runCatching {
                DocumentFile.fromSingleUri(ctx, uri)?.length()?.takeIf { it > 0L }
            }.getOrNull() ?: -1L
        }
    }

    class SeekableOutput(private val pfd: android.os.ParcelFileDescriptor,
        val output: FileOutputStream) : java.io.Closeable {
        override fun close() {
            runCatching { output.channel.force(true) }
            runCatching { output.close() }
            runCatching { pfd.close() }
        }
    }
}
