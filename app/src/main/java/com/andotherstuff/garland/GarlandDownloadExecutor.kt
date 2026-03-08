package com.andotherstuff.garland

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64

data class DownloadExecutionResult(
    val success: Boolean,
    val attemptedServers: Int,
    val restoredBytes: Int,
    val message: String,
)

class GarlandDownloadExecutor(
    private val store: LocalDocumentStoreImpl,
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson(),
    private val recoverBlock: (String) -> String = NativeBridge::recoverSingleBlockRead,
) {
    constructor(
        context: Context,
        client: OkHttpClient = OkHttpClient(),
        gson: Gson = Gson(),
    ) : this(LocalDocumentStoreImpl(context.applicationContext.filesDir.resolve("garland-documents")), client, gson)

    fun restoreDocument(documentId: String, privateKeyHex: String): DownloadExecutionResult {
        val raw = store.readUploadPlan(documentId)
            ?: return DownloadExecutionResult(false, 0, 0, "No upload plan found").also {
                store.updateUploadStatus(documentId, "download-failed", it.message)
            }
        val response = try {
            gson.fromJson(raw, DownloadPlanEnvelope::class.java)
        } catch (_: JsonSyntaxException) {
            return DownloadExecutionResult(false, 0, 0, "Invalid upload plan").also {
                store.updateUploadStatus(documentId, "download-failed", it.message)
            }
        }
        val manifest = response?.plan?.manifest
            ?: return DownloadExecutionResult(false, 0, 0, "Upload plan is missing manifest").also {
                store.updateUploadStatus(documentId, "download-failed", it.message)
            }
        val manifestValidationError = GarlandManifestValidator.validateForDownload(
            GarlandManifestInfo(
                documentId = manifest.documentId,
                blocks = manifest.blocks.map { block ->
                    GarlandManifestBlockInfo(
                        index = block.index,
                        shareIdHex = block.shareIdHex,
                        servers = block.servers,
                    )
                },
            )
        )?.message
        if (manifestValidationError != null) {
            return DownloadExecutionResult(false, 0, 0, manifestValidationError).also {
                store.updateUploadStatus(documentId, "download-failed", it.message)
            }
        }
        val blocks = manifest.blocks

        val restoredContent = mutableListOf<Byte>()
        blocks.forEach { block ->
            val fetchResult = fetchEncryptedBody(block)
            val encryptedBody = fetchResult.body
                ?: return DownloadExecutionResult(
                    false,
                    block.servers.size,
                    restoredContent.size,
                    fetchResult.error ?: "Unable to fetch share from configured servers"
                ).also {
                    store.updateUploadStatus(documentId, "download-failed", it.message)
                }

            val requestJson = GarlandConfig.buildRecoverReadRequestJson(
                privateKeyHex = privateKeyHex,
                documentId = manifest.documentId,
                blockIndex = block.index,
                encryptedBlock = encryptedBody,
            )
            val recovery = parseRecoveryEnvelope(recoverBlock(requestJson))
                ?: return DownloadExecutionResult(false, block.servers.size, restoredContent.size, "Invalid recovery response").also {
                    store.updateUploadStatus(documentId, "download-failed", it.message)
                }
            if (!recovery.ok) {
                val message = recovery.error ?: "Recovery failed"
                store.updateUploadStatus(documentId, "download-failed", message)
                return DownloadExecutionResult(false, block.servers.size, restoredContent.size, message)
            }

            val recoveredBytes = decodeRecoveredContent(recovery.contentBase64)
                ?: return DownloadExecutionResult(false, block.servers.size, restoredContent.size, "Invalid recovery response").also {
                    store.updateUploadStatus(documentId, "download-failed", it.message)
                }
            restoredContent += recoveredBytes.toList()
        }

        val content = restoredContent.toByteArray()
        store.contentFile(documentId).writeBytes(content)
        store.updateFromContent(documentId)
        val attemptedServers = blocks.sumOf { it.servers.size }
        val message = "Restored ${content.size} bytes from ${blocks.size} Garland block(s)"
        store.updateUploadStatus(documentId, "download-restored", message)
        return DownloadExecutionResult(true, attemptedServers, content.size, message)
    }

    private fun parseRecoveryEnvelope(rawJson: String): DownloadRecoveryEnvelope? {
        return try {
            val jsonObject = JsonParser.parseString(rawJson).asJsonObject
            val ok = jsonObject.requiredBoolean("ok") ?: return null
            val contentBase64 = jsonObject.optionalStringOrNull("content_b64")
            if (!contentBase64.isValid) return null
            val error = jsonObject.optionalStringOrNull("error")
            if (!error.isValid) return null
            DownloadRecoveryEnvelope(ok = ok, contentBase64 = contentBase64.value, error = error.value)
        } catch (_: IllegalStateException) {
            null
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    private fun JsonObject.requiredBoolean(fieldName: String): Boolean? {
        val field = get(fieldName) ?: return null
        if (!field.isJsonPrimitive || !field.asJsonPrimitive.isBoolean) return null
        return field.asBoolean
    }

    private fun JsonObject.optionalStringOrNull(fieldName: String): ParsedOptionalString {
        val field = get(fieldName) ?: return ParsedOptionalString(isValid = true, value = null)
        if (field.isJsonNull) return ParsedOptionalString(isValid = true, value = null)
        if (!field.isJsonPrimitive || !field.asJsonPrimitive.isString) {
            return ParsedOptionalString(isValid = false, value = null)
        }
        return ParsedOptionalString(isValid = true, value = field.asString)
    }

    private fun fetchEncryptedBody(block: ManifestBlockEnvelope): FetchEncryptedBodyResult {
        var invalidUrlMessage: String? = null
        var attemptedValidRequest = false
        block.servers.forEach { serverUrl ->
            listOf(
                serverUrl.trimEnd('/') + "/" + block.shareIdHex,
                serverUrl.trimEnd('/') + "/upload/" + block.shareIdHex,
            ).forEach { url ->
                runCatching {
                    val request = Request.Builder().url(url).get().build()
                    attemptedValidRequest = true
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            return FetchEncryptedBodyResult(body = response.body?.bytes())
                        }
                    }
                }.onFailure { error ->
                    if (error is IllegalArgumentException && !attemptedValidRequest) {
                        invalidUrlMessage = invalidBlossomServerUrlMessage(error)
                    }
                }
            }
        }
        return FetchEncryptedBodyResult(
            body = null,
            error = if (!attemptedValidRequest) invalidUrlMessage else null,
        )
    }

    private fun invalidBlossomServerUrlMessage(error: IllegalArgumentException): String {
        val detail = error.message?.trim().orEmpty()
        return if (detail.isBlank()) "Invalid Blossom server URL" else "Invalid Blossom server URL: $detail"
    }

    private fun decodeRecoveredContent(contentBase64: String?): ByteArray? {
        val encoded = contentBase64 ?: return null
        return try {
            Base64.getDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

private data class FetchEncryptedBodyResult(
    val body: ByteArray?,
    val error: String? = null,
)

private data class ParsedOptionalString(
    val isValid: Boolean,
    val value: String?,
)

private data class DownloadPlanEnvelope(
    val plan: DownloadPreparedPlan?,
)

private data class DownloadRecoveryEnvelope(
    val ok: Boolean,
    @SerializedName("content_b64") val contentBase64: String?,
    val error: String?,
)

private data class DownloadPreparedPlan(
    val manifest: DownloadManifestEnvelope?,
)

private data class DownloadManifestEnvelope(
    @SerializedName("document_id") val documentId: String,
    val blocks: List<ManifestBlockEnvelope>,
)

private data class ManifestBlockEnvelope(
    val index: Int,
    @SerializedName("share_id_hex") val shareIdHex: String,
    val servers: List<String>,
)
