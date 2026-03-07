package com.andotherstuff.garland

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Files

class LocalDocumentStoreTest {
    @Test
    fun upsertsPreparedDocumentWithContentAndPlan() {
        val tempDir = Files.createTempDirectory("garland-store-test").toFile()
        val store = LocalDocumentStoreImpl(tempDir)

        val record = store.upsertPreparedDocument(
            documentId = "doc123",
            displayName = "note.txt",
            mimeType = "text/plain",
            content = "hello".toByteArray(),
            uploadPlanJson = "{\"ok\":true}",
        )

        assertEquals("doc123", record.documentId)
        assertEquals("upload-plan-ready", record.uploadStatus)
        assertEquals("hello", store.contentFile("doc123").readText())
        assertNotNull(store.readUploadPlan("doc123"))
        assertEquals("upload-plan-ready", store.readRecord("doc123")?.uploadStatus)
    }
}
