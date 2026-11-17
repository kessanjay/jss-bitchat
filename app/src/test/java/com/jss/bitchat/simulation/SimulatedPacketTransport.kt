package com.jss.bitchat.simulation

import com.jss.bitchat.core.mesh.LinkEvent
import com.jss.bitchat.core.mesh.PacketTransport
import com.jss.bitchat.core.mesh.PeerId
import com.jss.bitchat.core.mesh.ReceivedFrame
import com.jss.bitchat.core.mesh.SendResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Deterministic simulated transport for a [PeerId].
 * Integrates directly with [SimulatedNetwork].
 */
class SimulatedPacketTransport(
    val localPeerId: PeerId,
    private val network: SimulatedNetwork
) : PacketTransport {

    private val _receivedFrames = MutableSharedFlow<ReceivedFrame>(extraBufferCapacity = 128)
    override val receivedFrames: Flow<ReceivedFrame> = _receivedFrames.asSharedFlow()

    private val _linkEvents = MutableSharedFlow<LinkEvent>(extraBufferCapacity = 32)
    override val linkEvents: Flow<LinkEvent> = _linkEvents.asSharedFlow()

    private val connectedNeighbors = mutableSetOf<PeerId>()

    override suspend fun send(peerId: PeerId, frame: ByteArray): SendResult {
        if (!connectedNeighbors.contains(peerId)) {
            return SendResult.Rejected("Peer $peerId not connected to $localPeerId")
        }
        return network.enqueueTransmission(from = localPeerId, to = peerId, frame = frame)
    }

    override fun connectedPeers(): Set<PeerId> = synchronized(connectedNeighbors) {
        connectedNeighbors.toSet()
    }

    fun addNeighbor(peerId: PeerId) {
        synchronized(connectedNeighbors) {
            connectedNeighbors.add(peerId)
        }
        _linkEvents.tryEmit(LinkEvent.Connected(peerId))
    }

    fun removeNeighbor(peerId: PeerId, reason: String? = null) {
        synchronized(connectedNeighbors) {
            connectedNeighbors.remove(peerId)
        }
        _linkEvents.tryEmit(LinkEvent.Disconnected(peerId, reason))
    }

    fun deliverIncomingFrame(frame: ReceivedFrame) {
        _receivedFrames.tryEmit(frame)
    }
}

