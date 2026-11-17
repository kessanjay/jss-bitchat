package com.jss.bitchat.simulation

import com.jss.bitchat.core.mesh.ByteArrayKey
import com.jss.bitchat.core.mesh.MeshRouter
import com.jss.bitchat.core.mesh.PeerId
import com.jss.bitchat.core.mesh.ReceivedFrame
import com.jss.bitchat.core.mesh.SendResult
import com.jss.bitchat.core.mesh.asKey
import com.jss.bitchat.core.protocol.PacketCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.ArrayDeque

/**
 * Deterministic, step-driven virtual network environment.
 * Orchestrates topology links, message queues, and discrete simulation steps.
 */
class SimulatedNetwork(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)
) {
    data class ScheduledTransmission(
        val from: PeerId,
        val to: PeerId,
        val frame: ByteArray,
        val scheduledStep: Long
    )

    private var currentVirtualTime: Long = 1000L
    private val nodes = mutableMapOf<PeerId, SimulatedNode>()
    private val links = mutableMapOf<Pair<PeerId, PeerId>, SimulatedLink>()
    private val transmissionQueue = ArrayDeque<ScheduledTransmission>()
    val recordedSimulationEvents = mutableListOf<SimulationEvent>()
    private val codec = PacketCodec()

    fun currentTime(): Long = currentVirtualTime

    fun createNode(peerId: PeerId): SimulatedNode {
        val transport = SimulatedPacketTransport(peerId, this)
        val router = MeshRouter(
            localPeerId = peerId,
            transport = transport,
            codec = codec,
            scope = scope
        )
        val node = SimulatedNode(peerId, transport, router, scope)
        nodes[peerId] = node
        return node
    }

    /**
     * Connects two nodes bidirectionally.
     */
    fun createLink(nodeA: PeerId, nodeB: PeerId): SimulatedLink {
        val link = SimulatedLink(nodeA, nodeB, isUp = true)
        links[nodeA to nodeB] = link
        links[nodeB to nodeA] = link

        nodes[nodeA]?.transport?.addNeighbor(nodeB)
        nodes[nodeB]?.transport?.addNeighbor(nodeA)
        return link
    }

    fun setLinkUp(nodeA: PeerId, nodeB: PeerId, isUp: Boolean) {
        links[nodeA to nodeB]?.isUp = isUp
        links[nodeB to nodeA]?.isUp = isUp
        if (isUp) {
            nodes[nodeA]?.transport?.addNeighbor(nodeB)
            nodes[nodeB]?.transport?.addNeighbor(nodeA)
        } else {
            nodes[nodeA]?.transport?.removeNeighbor(nodeB, "Link down")
            nodes[nodeB]?.transport?.removeNeighbor(nodeA, "Link down")
        }
    }

    fun enqueueTransmission(from: PeerId, to: PeerId, frame: ByteArray): SendResult {
        val link = links[from to to] ?: return SendResult.Rejected("No link between $from and $to")
        if (!link.isUp) {
            return SendResult.Rejected("Link down")
        }

        transmissionQueue.addLast(
            ScheduledTransmission(
                from = from,
                to = to,
                frame = frame,
                scheduledStep = currentVirtualTime + 1
            )
        )
        return SendResult.Accepted
    }

    /**
     * Advances simulation by executing one discrete pending transmission.
     *
     * @return true if a transmission was executed, false if queue is idle.
     */
    suspend fun step(): Boolean {
        if (transmissionQueue.isEmpty()) return false

        val item = transmissionQueue.pollFirst() ?: return false
        currentVirtualTime++

        val link = links[item.from to item.to]
        if (link == null || !link.isUp) {
            recordedSimulationEvents.add(
                SimulationEvent.FrameDroppedByLink(item.from, item.to, "Link disconnected")
            )
            return true
        }

        if (!link.shouldDeliver(item.from, item.to)) {
            recordedSimulationEvents.add(
                SimulationEvent.FrameDroppedByLink(item.from, item.to, "Policy drop")
            )
            return true
        }

        // Decode for diagnostic simulation event recording
        try {
            val parsed = codec.decode(item.frame)
            recordedSimulationEvents.add(
                SimulationEvent.FrameTransmitted(
                    from = item.from,
                    to = item.to,
                    packetId = parsed.packetId.asKey(),
                    messageId = parsed.messageId.asKey(),
                    ttl = parsed.ttl
                )
            )
        } catch (_: Exception) {
            // Malformed frame diagnostic
        }

        val destinationNode = nodes[item.to]
        if (destinationNode != null) {
            val received = ReceivedFrame(
                from = item.from,
                bytes = item.frame,
                receivedAtEpochMs = currentVirtualTime
            )
            // Deliver exclusively through the transport Flow, matching production architecture
            destinationNode.transport.deliverIncomingFrame(received)
        }

        return true
    }

    /**
     * Executes pending events until no more transmissions remain or [maxSteps] is reached.
     */
    suspend fun runUntilIdle(maxSteps: Int = 100): Int {
        var steps = 0
        while (steps < maxSteps && step()) {
            steps++
        }
        return steps
    }

    fun startAll() {
        nodes.values.forEach { it.start() }
    }

    fun stopAll() {
        nodes.values.forEach { it.stop() }
    }
}

