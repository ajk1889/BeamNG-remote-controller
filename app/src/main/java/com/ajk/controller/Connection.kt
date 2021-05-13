package com.ajk.controller

import org.java_websocket.client.WebSocketClient
import org.java_websocket.enums.ReadyState
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Connection (uri: URI) : WebSocketClient(uri) {

    override fun onOpen(handshakedata: ServerHandshake?) {
        if (readyState == ReadyState.OPEN) {
            sendMessage(eventsQueue)
            eventsQueue.clear()
        }
    }
    override fun onMessage(message: String?) {}
    override fun onClose(code: Int, reason: String?, remote: Boolean) {}
    override fun onError(ex: Exception?) {}

    override fun onMessage(bytes: ByteBuffer?) {
        val jsonObject = JSONObject(bytes?.array()?.decodeToString() ?: "{}")
        jsonObject.keys().forEach { eventCodeMap[it] = jsonObject.getInt(it).toShort() }
        println(eventCodeMap)
    }

    private val eventsQueue = mutableListOf<Event>()
    fun sendMessage(event: Event) = sendMessage(listOf(event))
    fun sendMessage(events: List<Event>) {
        if (events.isEmpty()) return
        if (readyState == ReadyState.OPEN) {
            val data = ShortArray(events.size * 2)
            events.forEachIndexed { index, event ->
                data[2*index] = event.eventCode
                data[2*index + 1] = event.value
            }
            val byteBuffer = ByteBuffer.allocate(data.size * Short.SIZE_BYTES)
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
            byteBuffer.asShortBuffer().put(data)
            this.send(byteBuffer)
        } else eventsQueue.addAll(events)
    }
}