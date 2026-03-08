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
    val headlineTone: String,
    val headline: String,
    val summary: String,
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
        val narrative = buildNarrative(selectedRecord)
        val troubleshootingItems = selectedRecord?.let { troubleshootingItems(it, narrative) }.orEmpty()
        return DocumentDiagnosticsScreenState(
            title = selectedRecord?.displayName?.let { "Diagnostics for $it" } ?: "Diagnostics",
            selectedDocumentId = selectedRecord?.documentId,
            selectedLabel = selectedRecord?.displayName ?: "No local Garland documents yet.",
            documentIdLabel = selectedRecord?.documentId?.let { "Document ID: $it" },
            headlineTone = narrative.tone,
            headline = narrative.headline,
            summary = narrative.summary,
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

    private data class DiagnosticNarrative(
        val tone: String,
        val headline: String,
        val summary: String,
        val primaryStep: String? = null,
        val followUpStep: String? = null,
    )

    private fun buildNarrative(record: LocalDocumentRecord?): DiagnosticNarrative {
        if (record == null) {
            return DiagnosticNarrative(
                tone = "neutral",
                headline = "No local documents yet",
                summary = "Prepare or restore a document to inspect Garland diagnostics.",
            )
        }

        val diagnostics = DocumentSyncDiagnosticsCodec.decode(record.lastSyncDetailsJson)
        val failingPlan = diagnostics?.plan?.firstOrNull { it.status != "ok" }
        val failingUpload = diagnostics?.uploads?.firstOrNull { it.status != "ok" }
        val failingRelay = diagnostics?.relays?.firstOrNull { it.status != "ok" }
        val uploadFailureCount = diagnostics?.uploads?.count { it.status != "ok" } ?: 0
        val relayFailureCount = diagnostics?.relays?.count { it.status != "ok" } ?: 0
        val activeBackground = record.uploadStatus in ACTIVE_BACKGROUND_STATUSES

        if (failingPlan != null) {
            val field = normalizePlanField(failingPlan.field)
            return DiagnosticNarrative(
                tone = "error",
                headline = "Upload plan needs to be rebuilt",
                summary = "Garland stopped before network work started. First blocker: $field - ${humanizeDetail(failingPlan.detail)}.",
                primaryStep = "Prepare the document again so Garland can rebuild a clean upload plan.",
                followUpStep = "If the same field fails again, copy the report and compare the plan metadata before retrying.",
            )
        }

        if (activeBackground) {
            val preservedFailure = failingUpload?.let {
                "Last completed attempt still shows $uploadFailureCount upload ${pluralize(uploadFailureCount, "failure", "failures")}, starting at ${normalizeTarget(it.target)}."
            } ?: failingRelay?.let {
                "Last completed attempt still shows $relayFailureCount relay ${pluralize(relayFailureCount, "failure", "failures")}, starting at ${normalizeTarget(it.target)}."
            } ?: "No fresh failure is recorded yet. Refresh after the worker finishes to see the latest result."
            return DiagnosticNarrative(
                tone = "warning",
                headline = "Background work is still running",
                summary = preservedFailure,
                primaryStep = "Wait for the active worker to finish, then refresh diagnostics before retrying anything.",
                followUpStep = failingUpload?.let {
                    "If the retry still fails, check Blossom reachability for ${normalizeTarget(it.target)} and retry upload."
                } ?: failingRelay?.let {
                    "If the retry still fails, check relay auth or connectivity for ${normalizeTarget(it.target)} and retry publish."
                },
            )
        }

        if (failingUpload != null) {
            val target = normalizeTarget(failingUpload.target)
            return DiagnosticNarrative(
                tone = "error",
                headline = "Share upload needs attention",
                summary = "$uploadFailureCount upload ${pluralize(uploadFailureCount, "target", "targets")} failed. First blocker: $target - ${humanizeDetail(failingUpload.detail)}.",
                primaryStep = "Check Blossom reachability and available storage for $target, then retry upload.",
                followUpStep = "If the next retry fails, copy the report before changing server defaults.",
            )
        }

        if (failingRelay != null) {
            val target = normalizeTarget(failingRelay.target)
            return DiagnosticNarrative(
                tone = "error",
                headline = "Relay publish needs attention",
                summary = "$relayFailureCount relay ${pluralize(relayFailureCount, "target", "targets")} failed. First blocker: $target - ${humanizeDetail(failingRelay.detail)}.",
                primaryStep = "Check write access, auth, and connectivity for $target, then retry relay publish.",
                followUpStep = "If publish keeps failing, copy the report and compare it with the last known-good attempt.",
            )
        }

        if (record.lastSyncMessage.isNullOrBlank()) {
            return DiagnosticNarrative(
                tone = "info",
                headline = "Document is ready for the next step",
                summary = "Garland has local state for this document, but no sync result has been recorded yet.",
                primaryStep = "Run upload or restore to produce the first network trace for this document.",
            )
        }

        return DiagnosticNarrative(
            tone = "success",
            headline = "Last sync looks healthy",
            summary = "The latest recorded attempt completed without structured upload or relay failures.",
            primaryStep = "If behavior still looks wrong, copy the report so you can compare it with a failing run.",
        )
    }

    private fun troubleshootingItems(
        record: LocalDocumentRecord,
        narrative: DiagnosticNarrative,
    ): List<String> {
        val items = mutableListOf<String>()
        narrative.primaryStep?.let(items::add)
        narrative.followUpStep?.let(items::add)
        if (record.syncHistoryJson != null) {
            items += "Copy the report before reproducing the issue so you keep the last known-good trace."
        }
        if (items.isEmpty()) {
            items += "No active failure markers. Copy the report if behavior still looks wrong."
        }
        return items.distinct()
    }

    private fun humanizeDetail(detail: String): String {
        val normalized = detail.trim().replace("\n", " ")
        return when {
            normalized.contains("timeout", ignoreCase = true) -> "request timed out"
            normalized.contains("auth", ignoreCase = true) -> normalized.replaceFirstChar { it.lowercase() }
            normalized.contains("HTTP 500", ignoreCase = true) -> "server returned HTTP 500"
            normalized.contains("HTTP 401", ignoreCase = true) -> "server returned HTTP 401"
            normalized.contains("HTTP 403", ignoreCase = true) -> "server returned HTTP 403"
            normalized.contains("HTTP 404", ignoreCase = true) -> "server returned HTTP 404"
            else -> normalized.replaceFirstChar { it.lowercase() }
        }
    }

    private fun normalizeTarget(target: String): String {
        return target.trim().replace(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")
    }

    private fun normalizePlanField(field: String): String {
        return field.trim().removePrefix("plan.").ifBlank { "plan" }
    }

    private fun pluralize(count: Int, singular: String, plural: String): String {
        return if (count == 1) singular else plural
    }

    private val ACTIVE_BACKGROUND_STATUSES = setOf("sync-queued", "sync-running", "restore-queued", "restore-running")
}
