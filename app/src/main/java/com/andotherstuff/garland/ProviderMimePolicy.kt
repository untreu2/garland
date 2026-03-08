package com.andotherstuff.garland

import java.net.URLConnection

object ProviderMimePolicy {
    private const val GENERIC_BINARY_MIME = "application/octet-stream"

    fun supportsThumbnail(mimeType: String): Boolean = mimeType.startsWith("image/", ignoreCase = true)

    fun resolveMimeType(mimeType: String?, displayName: String): String {
        val requestedMimeType = mimeType
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()
            ?: GENERIC_BINARY_MIME
        if (!shouldResolveFromExtension(requestedMimeType)) {
            return requestedMimeType
        }

        return URLConnection.guessContentTypeFromName(displayName) ?: requestedMimeType
    }

    private fun shouldResolveFromExtension(mimeType: String): Boolean {
        return mimeType == GENERIC_BINARY_MIME || mimeType.endsWith("/*")
    }
}
