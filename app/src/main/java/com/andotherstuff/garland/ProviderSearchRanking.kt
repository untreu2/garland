package com.andotherstuff.garland

object ProviderSearchRanking {
    fun sortMatches(records: List<LocalDocumentRecord>, rawQuery: String): List<LocalDocumentRecord> {
        val needle = rawQuery.trim().lowercase()
        if (needle.isBlank()) return records

        return records.sortedWith(
            compareBy<LocalDocumentRecord> { matchRank(it, needle) }
                .thenByDescending { it.updatedAt }
                .thenBy { it.displayName.lowercase() }
        )
    }

    private fun matchRank(record: LocalDocumentRecord, needle: String): Int {
        val displayName = record.displayName.lowercase()
        val lastSyncMessage = record.lastSyncMessage?.lowercase().orEmpty()
        val uploadStatus = record.uploadStatus.lowercase()
        val mimeType = record.mimeType.lowercase()

        return when {
            displayName.startsWith(needle) -> 0
            displayName.contains(needle) -> 1
            lastSyncMessage.contains(needle) -> 2
            uploadStatus.contains(needle) -> 3
            mimeType.contains(needle) -> 4
            else -> 5
        }
    }
}
