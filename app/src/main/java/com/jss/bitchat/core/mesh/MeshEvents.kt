package com.jss.bitchat.core.mesh

import com.jss.bitchat.core.protocol.MeshPacket
import com.jss.bitchat.core.protocol.PacketValidationException

/**
 * Observable events emitted by the [MeshRouter] for higher layers (e.g. Shreya H's domain repository / UI).
 */
sealed interface MeshEvent {
    /**
     * Emitted when a packet targeting this local node has been accepted and delivered locally.
     */
    data class MessageDelivered(
        val messageId: ByteArrayKey,
        val source: PeerId,
        val packet: MeshPacket
    ) : MeshEvent

    /**
     * Emitted when a message originated by this node has been confirmed delivered end-to-end via an ACK.
     */
    data class EndToEndAckReceived(
        val messageId: ByteArrayKey,
        val acknowledgedBy: PeerId
    ) : MeshEvent

    /**
     * Emitted for relay telemetry when a packet not addressed to this node is forwarded.
     */
    data class PacketRelayed(
        val packetId: ByteArrayKey,
        val messageId: ByteArrayKey,
        val packetType: com.jss.bitchat.core.protocol.PacketType,
        val destination: PeerId,
        val forwardedTo: Set<PeerId>
    ) : MeshEvent

    /**
     * Emitted when an incoming packet is dropped because its packetId was already seen.
     */
    data class DuplicateDropped(
        val packetId: ByteArrayKey
    ) : MeshEvent

    /**
     * Emitted when an incoming packet's hop budget is exhausted (TTL <= 1) and cannot be forwarded.
     */
    data class TtlExpired(
        val packetId: ByteArrayKey,
        val messageId: ByteArrayKey
    ) : MeshEvent

    /**
     * Emitted when an outbound frame is dropped due to queue congestion for a peer.
     */
    data class ForwardingQueueFull(
        val targetPeer: PeerId,
        val packetId: ByteArrayKey
    ) : MeshEvent

    /**
     * Emitted when an unroutable or malformed packet is received.
     */
    data class PacketRejected(
        val reason: String,
        val cause: PacketValidationException? = null
    ) : MeshEvent

    /**
     * Emitted when a message could not be delivered after all retries were exhausted.
     */
    data class MessageDeliveryFailed(
        val messageId: ByteArrayKey,
        val destination: PeerId,
        val reason: String
    ) : MeshEvent
}

