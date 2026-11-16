package com.jss.bitchat.simulation

import com.jss.bitchat.core.mesh.ByteArrayKey
import com.jss.bitchat.core.mesh.PeerId
import com.jss.bitchat.core.protocol.MeshPacket

/**
 * Diagnostic simulation events recorded during deterministic network execution.
 */
sealed interface SimulationEvent {
    data class FrameTransmitted(
        val from: PeerId,
        val to: PeerId,
        val packetId: ByteArrayKey,
        val messageId: ByteArrayKey,
        val ttl: Int
    ) : SimulationEvent

    data class FrameDroppedByLink(
        val from: PeerId,
        val to: PeerId,
        val reason: String
    ) : SimulationEvent

    data class FrameDelivered(
        val node: PeerId,
        val from: PeerId,
        val packetId: ByteArrayKey
    ) : SimulationEvent
}

