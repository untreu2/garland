package com.andotherstuff.garland

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderSearchRankingTest {
    @Test
    fun displayNameMatchesSortAheadOfDiagnosticOnlyMatches() {
        val diagnosticMatch = LocalDocumentRecord(
            documentId = "diag",
            displayName = "report.txt",
            mimeType = "text/plain",
            sizeBytes = 1,
            updatedAt = 500,
            uploadStatus = "relay-published-partial",
            lastSyncMessage = "relay timeout on wss://needle.example",
        )
        val nameMatch = LocalDocumentRecord(
            documentId = "name",
            displayName = "needle-report.txt",
            mimeType = "text/plain",
            sizeBytes = 1,
            updatedAt = 100,
            uploadStatus = "waiting-for-identity",
        )

        val ranked = ProviderSearchRanking.sortMatches(listOf(diagnosticMatch, nameMatch), "needle")

        assertEquals(listOf("name", "diag"), ranked.map { it.documentId })
    }

    @Test
    fun newerResultsWinWithinTheSameRank() {
        val older = LocalDocumentRecord(
            documentId = "older",
            displayName = "needle-alpha.txt",
            mimeType = "text/plain",
            sizeBytes = 1,
            updatedAt = 100,
            uploadStatus = "waiting-for-identity",
        )
        val newer = LocalDocumentRecord(
            documentId = "newer",
            displayName = "needle-beta.txt",
            mimeType = "text/plain",
            sizeBytes = 1,
            updatedAt = 200,
            uploadStatus = "waiting-for-identity",
        )

        val ranked = ProviderSearchRanking.sortMatches(listOf(older, newer), "needle")

        assertEquals(listOf("newer", "older"), ranked.map { it.documentId })
    }
}
