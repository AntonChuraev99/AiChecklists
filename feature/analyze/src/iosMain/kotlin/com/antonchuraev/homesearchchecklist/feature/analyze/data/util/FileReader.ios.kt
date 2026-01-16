package com.antonchuraev.homesearchchecklist.feature.analyze.data.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.stringWithContentsOfFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual object FileReader {
    actual fun readBytes(filePath: String): ByteArray? {
        return try {
            // Handle file:// URLs
            val path = if (filePath.startsWith("file://")) {
                NSURL.URLWithString(filePath)?.path ?: filePath
            } else {
                filePath
            }

            val data = NSData.dataWithContentsOfFile(path) ?: return null
            val bytes = ByteArray(data.length.toInt())
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
            bytes
        } catch (e: Exception) {
            null
        }
    }

    actual fun readText(filePath: String): String? {
        return try {
            // Handle file:// URLs
            val path = if (filePath.startsWith("file://")) {
                NSURL.URLWithString(filePath)?.path ?: filePath
            } else {
                filePath
            }

            NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
        } catch (e: Exception) {
            null
        }
    }
}
