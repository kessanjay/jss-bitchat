package com.jss.bitchat.core.mesh

import com.jss.bitchat.core.protocol.MeshPacket
import com.jss.bitchat.core.protocol.PacketIdGenerator
import com.jss.bitchat.core.protocol.PacketType
import com.jss.bitchat.core.protocol.ProtocolLimits
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages end-to-end delivery tracking, retry policies, and ACK generation.
 *
 * Requirements:
 * - Every retry retains the original messageId.
 * - Every retry transmission generates a fresh packetId.
 * - Duplicate suppression is keyed by packetId, so retransmissions are not blocked by duplicate cache.
 * - Bounded retry attempts (default 3 attempts).
 */
class AckManager(
    private val localPeerId: PeerId,
    private val packetIdGenerator: PacketIdGenerator = PacketIdGenerator(),
    private val maxRetries: Int = 3
) {
    data class PendingMessage(
        val messageId: ByteArrayKey,
        val destination: PeerId,
        val payload: ByteArray,
        val originalTtl: Int,
        var attempts: Int = 1,
        var lastSentPacketId: ByteArrayKey? = null
    )

    private val pendingMessages = ConcurrentHashMap<ByteArrayKey, PendingMessage>()

    /**
     * Registers a newly originated outbound message for delivery tracking.
     */
    fun trackOutboundMessage(
        messageId: ByteArrayKey,
        destination: PeerId,
        payload: ByteArray,
        ttl: Int,
        initialPacketId: ByteArrayKey
    ) {
        pendingMessages[messageId] = PendingMessage(
            messageId = messageId,
            destination = destination,
            payload = payload,
            originalTtl = ttl,
            attempts = 1,
            lastSentPacketId = initialPacketId
        )
    }

    /**
     * Prepares a retransmission for a message if retries remain.
     * Retains the SAME messageId, but generates a FRESH packetId.
     *
     * @return Fresh [MeshPacket] ready to be encoded and flooded, or null if retries exhausted.
     */
    fun prepareRetry(messageId: ByteArrayKey): MeshPacket? {
        val pending = pendingMessages[messageId] ?: return null
        if (pending.attempts >= maxRetries) {
            pendingMessages.remove(messageId)
            return null
        }

        pending.attempts++
        val freshPacketId = packetIdGenerator.generateId()
        pending.lastSentPacketId = freshPacketId.asKey()

        return MeshPacket(
            protocolVersion = ProtocolLimits.PROTOCOL_VERSION,
            packetId = freshPacketId,
            messageId = pending.messageId.bytes,
            sourceId = localPeerId.toByteArray(),
            destinationId = pending.destination.toByteArray(),
            packetType = PacketType.DATA,
            ttl = pending.originalTtl,
            sequenceNumber = 0L,
            payload = pending.payload
        )
    }

    /**
     * Called when an ACK packet is received.
     *
     * @return The [PendingMessage] that was acknowledged, or null if not found/invalid.
     */
    fun onAckReceived(ackPacket: MeshPacket): PendingMessage? {
        if (ackPacket.packetType != PacketType.ACK) return null

        val ackPayload = AckPayload.fromByteArray(ackPacket.payload) ?: return null

        // Must acknowledge an expected message
        val msgKey = ackPayload.acknowledgedMessageId.asKey()
        val pending = pendingMessages[msgKey] ?: return null

        // Validate that ACK originated from the expected destination
        val ackSender = PeerId.fromByteArray(ackPacket.sourceId)
        if (ackSender != pending.destination) {
            return null
        }

        // Successfully acknowledged: remove from pending
        pendingMessages.remove(msgKey)
        return pending
    }

    /**
     * Creates an ACK packet to confirm receipt of [receivedPacket].
     *
     * Wire rules:
     * - Preserves the received message's messageId.
     * - Generates a FRESH packetId for the ACK.
     * - PacketType = ACK.
     * - Destination = original sourceId.
     * - Source = localPeerId.
     * - Payload = 33-byte AckPayload.
     */
    fun createAckFor(receivedPacket: MeshPacket): MeshPacket {
        val freshPacketId = packetIdGenerator.generateId()
        val ackPayload = AckPayload(
            acknowledgedMessageId = receivedPacket.messageId,
            status = AckPayload.STATUS_SUCCESS,
            acknowledgingNodeId = localPeerId.toByteArray()
        )

        return MeshPacket(
            protocolVersion = ProtocolLimits.PROTOCOL_VERSION,
            packetId = freshPacketId,
            messageId = receivedPacket.messageId,
            sourceId = localPeerId.toByteArray(),
            destinationId = receivedPacket.sourceId,
            packetType = PacketType.ACK,
            ttl = ProtocolLimits.INITIAL_TTL,
            sequenceNumber = 0L,
            payload = ackPayload.toByteArray()
        )
    }

    fun isPending(messageId: ByteArrayKey): Boolean = pendingMessages.containsKey(messageId)

    fun getPending(messageId: ByteArrayKey): PendingMessage? = pendingMessages[messageId]

    fun removePending(messageId: ByteArrayKey) {
        pendingMessages.remove(messageId)
    }

    fun clear() {
        pendingMessages.clear()
    }
}

