package com.andotherstuff.garland

import java.net.URLConnection

object ProviderMimePolicy {
    private const val GENERIC_BINARY_MIME = "application/octet-stream"
    private val preferredExtensions = mapOf(
        "application/json" to "json",
        "application/pdf" to "pdf",
        "text/html" to "html",
        "text/markdown" to "md",
        "text/plain" to "txt",
    )
    private val wildcardExtensions = mapOf(
        "application" to "bin",
        "text" to "txt",
    )

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

    fun resolveDisplayName(displayName: String?, mimeType: String): String {
        val baseName = displayName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Untitled"
        if (baseName.substringAfterLast('.', missingDelimiterValue = "").isNotBlank()) {
            return baseName
        }

        val normalizedMimeType = mimeType.lowercase()
        val extension = preferredExtensions[normalizedMimeType]
            ?: normalizedMimeType
                .takeIf { it.endsWith("/*") }
                ?.substringBefore('/')
                ?.let(wildcardExtensions::get)
            ?: return baseName
        return "$baseName.$extension"
    }

    private fun shouldResolveFromExtension(mimeType: String): Boolean {
        return mimeType == GENERIC_BINARY_MIME || mimeType.endsWith("/*")
    }
}
