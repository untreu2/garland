package com.andotherstuff.garland

data class DocumentDiagnosticsOption(
    val documentId: String,
    val label: String,
    val selected: Boolean,
)

data class DocumentDiagnosticsScreenState(
    val title: String,
    val selectedDocumentId: String?,
    val selectedLabel: String,
    val documentIdLabel: String?,
    val overview: String,
    val uploadsLabel: String?,
    val uploads: String?,
    val relaysLabel: String?,
    val relays: String?,
    val historyLabel: String?,
    val history: String?,
    val troubleshootingLabel: String?,
    val troubleshootingItems: List<String>,
    val exportText: String,
    val documentOptions: List<DocumentDiagnosticsOption>,
)

object DocumentDiagnosticsScreenPresenter {
    fun build(
        records: List<LocalDocumentRecord>,
        selectedDocumentId: String?,
        readUploadPlan: (String) -> String?,
    ): DocumentDiagnosticsScreenState {
        val sortedRecords = records.sortedByDescending { it.updatedAt }
        val selectedRecord = sortedRecords.firstOrNull { it.documentId == selectedDocumentId }
            ?: sortedRecords.firstOrNull()
        val summary = selectedRecord?.let { GarlandPlanInspector.summarize(readUploadPlan(it.documentId)) }
        val sections = DocumentDiagnosticsFormatter.detailSections(selectedRecord, summary)
        val troubleshootingItems = selectedRecord?.let { troubleshootingItems(it, sections) }.orEmpty()
        return DocumentDiagnosticsScreenState(
            title = selectedRecord?.displayName?.let { "Diagnostics for $it" } ?: "Diagnostics",
            selectedDocumentId = selectedRecord?.documentId,
            selectedLabel = selectedRecord?.displayName ?: "No local Garland documents yet.",
            documentIdLabel = selectedRecord?.documentId?.let { "Document ID: $it" },
            overview = sections.overview,
            uploadsLabel = sections.uploadsLabel,
            uploads = sections.uploads,
            relaysLabel = sections.relaysLabel,
            relays = sections.relays,
            historyLabel = sections.historyLabel,
            history = sections.history,
            troubleshootingLabel = troubleshootingItems.takeIf { it.isNotEmpty() }?.let { "Troubleshooting" },
            troubleshootingItems = troubleshootingItems,
            exportText = DocumentDiagnosticsFormatter.exportText(selectedRecord, summary),
            documentOptions = sortedRecords.map { record ->
                DocumentDiagnosticsOption(
                    documentId = record.documentId,
                    label = record.displayName,
                    selected = record.documentId == selectedRecord?.documentId,
                )
            },
        )
    }

    private fun troubleshootingItems(
        record: LocalDocumentRecord,
        sections: DocumentDiagnosticsFormatter.DetailSections,
    ): List<String> {
        val items = mutableListOf<String>()
        if (record.uploadStatus in setOf("sync-queued", "sync-running", "restore-queued", "restore-running")) {
            items += "Background work is still active. Refresh after the current worker finishes."
        }
        if (sections.uploadsLabel?.contains("failed", ignoreCase = true) == true) {
            items += "Retry upload after checking Blossom server reachability and payload health."
        }
        if (sections.relaysLabel?.contains("failed", ignoreCase = true) == true) {
            items += "Retry relay publish after confirming relay connectivity and auth."
        }
        if (sections.history != null) {
            items += "Copy the report before reproducing the issue so you keep the last known-good trace."
        }
        if (items.isEmpty()) {
            items += "No active failure markers. Copy the report if behavior still looks wrong."
        }
        return items.distinct()
    }
}
