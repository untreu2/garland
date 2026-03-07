package com.andotherstuff.garland

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class RelayPublishResult(
    val attemptedRelays: Int,
    val successfulRelays: Int,
    val message: String,
)

class NostrRelayPublisher(
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson(),
    private val ackTimeoutMillis: Long = 5_000,
) {
    fun publish(relayUrls: List<String>, event: SignedRelayEvent): RelayPublishResult {
        val normalizedRelays = relayUrls.map { it.trim() }.filter { it.isNotEmpty() }
        if (normalizedRelays.isEmpty()) {
            return RelayPublishResult(0, 0, "No relays configured")
        }

        var successfulRelays = 0
        val failures = mutableListOf<String>()
        normalizedRelays.forEach { relayUrl ->
            if (publishToRelay(relayUrl, event)) {
                successfulRelays += 1
            } else {
                failures += relayUrl
            }
        }

        val message = if (failures.isEmpty()) {
            "Published to $successfulRelays/${normalizedRelays.size} relays"
        } else {
            "Published to $successfulRelays/${normalizedRelays.size} relays; failed: ${failures.joinToString()}"
        }

        return RelayPublishResult(normalizedRelays.size, successfulRelays, message)
    }

    private fun publishToRelay(relayUrl: String, event: SignedRelayEvent): Boolean {
        val ack = RelayAck()
        val latch = CountDownLatch(1)
        val request = Request.Builder().url(relayUrl).build()
        val websocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(gson.toJson(listOf("EVENT", event.toRelayEventPayload())))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { JsonParser.parseString(text).asJsonArray }.getOrNull() ?: return
                if (message.size() < 3 || message[0].asString != "OK") return
                if (message[1].asString != event.id) return

                ack.accepted = message[2].asBoolean
                latch.countDown()
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                latch.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                latch.countDown()
            }
        })

        latch.await(ackTimeoutMillis, TimeUnit.MILLISECONDS)
        websocket.cancel()
        return ack.accepted
    }
}

data class SignedRelayEvent(
    @SerializedName("id_hex") val id: String,
    @SerializedName("pubkey_hex") val pubkey: String,
    @SerializedName("created_at") val createdAt: Long,
    val kind: Long,
    val tags: List<List<String>>,
    val content: String,
    @SerializedName("sig_hex") val sig: String,
) {
    fun toRelayEventPayload(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "pubkey" to pubkey,
            "created_at" to createdAt,
            "kind" to kind,
            "tags" to tags,
            "content" to content,
            "sig" to sig,
        )
    }
}

private class RelayAck {
    @Volatile
    var accepted: Boolean = false
}
