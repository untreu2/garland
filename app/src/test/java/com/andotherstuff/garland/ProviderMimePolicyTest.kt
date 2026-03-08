package com.andotherstuff.garland

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderMimePolicyTest {
    @Test
    fun wildcardMimeTypeFallsBackToDisplayNameExtension() {
        assertEquals(
            "image/png",
            ProviderMimePolicy.resolveMimeType("image/*", "preview.png"),
        )
    }

    @Test
    fun genericBinaryMimeTypeFallsBackToDisplayNameExtension() {
        assertEquals(
            "image/jpeg",
            ProviderMimePolicy.resolveMimeType("application/octet-stream", "photo.jpg"),
        )
    }

    @Test
    fun concreteMimeTypeWinsOverDisplayNameExtension() {
        assertEquals(
            "text/plain",
            ProviderMimePolicy.resolveMimeType("text/plain", "photo.jpg"),
        )
    }

    @Test
    fun wildcardMimeTypeWithoutKnownExtensionStaysWildcard() {
        assertEquals(
            "image/*",
            ProviderMimePolicy.resolveMimeType("image/*", "preview"),
        )
    }

    @Test
    fun imageMimeTypesAdvertiseThumbnailSupport() {
        assertTrue(ProviderMimePolicy.supportsThumbnail("image/png"))
    }

    @Test
    fun blankDisplayNameGetsKnownExtensionFromMimeType() {
        assertEquals(
            "Untitled.json",
            ProviderMimePolicy.resolveDisplayName("", "application/json"),
        )
    }

    @Test
    fun extensionlessDisplayNameGetsKnownExtensionFromMimeType() {
        assertEquals(
            "notes.txt",
            ProviderMimePolicy.resolveDisplayName("notes", "text/plain"),
        )
    }

    @Test
    fun existingDisplayNameExtensionWinsOverMimeSuggestion() {
        assertEquals(
            "draft.md",
            ProviderMimePolicy.resolveDisplayName("draft.md", "text/plain"),
        )
    }
}
