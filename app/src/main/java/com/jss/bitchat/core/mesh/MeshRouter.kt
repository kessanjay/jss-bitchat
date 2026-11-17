package com.jss.bitchat.core.mesh

import com.jss.bitchat.core.protocol.MeshPacket
import com.jss.bitchat.core.protocol.PacketCodec
import com.jss.bitchat.core.protocol.PacketIdGenerator
import com.jss.bitchat.core.protocol.PacketType
import com.jss.bitchat.core.protocol.PacketValidationException
import com.jss.bitchat.core.protocol.PacketValidator
import com.jss.bitchat.core.protocol.ProtocolLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Production Mesh Router implementing controlled flooding mesh routing.
 *
 * Routing Rules:
 * 1. Checks duplicate cache; drops already-seen packet IDs.
 * 2. Caches packet ID BEFORE forwarding to prevent loops across concurrent paths.
 * 3. Delivers locally if packet.destinationId matches localNodeId.
 * 4. If destination, generates end-to-end ACK packet back to sender (if DATA packet).
 * 5. If not destination, checks TTL. If TTL <= 1, drops with TtlExpired.
 * 6. If TTL > 1, decrements TTL by 1, preserves packetId/messageId, and floods to all connected peers EXCEPT ingress peer.
 * 7. Enforces bounded forwarding queues (max 64 frames per peer); emits ForwardingQueueFull when congested.
 */
class MeshRouter(
    val localPeerId: PeerId,
    private val transport: PacketTransport,
    private val codec: PacketCodec = PacketCodec(),
    private val validator: PacketValidator = PacketValidator(),
    private val idGenerator: PacketIdGenerator = PacketIdGenerator(),
    val duplicateCache: DuplicateCache = DuplicateCache(),
    val forwardingQueue: ForwardingQueue = ForwardingQueue(maxQueueSize = 64),
    val ackManager: AckManager = AckManager(localPeerId, idGenerator),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _events = MutableSharedFlow<MeshEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<MeshEvent> = _events.asSharedFlow()

    private val sequenceCounter = AtomicLong(0L)
    private var receiveJob: Job? = null

    /**
     * Starts listening to inbound frames from the transport.
     */
    fun start() {
        if (receiveJob != null) return
        receiveJob = scope.launch {
            transport.receivedFrames.collect { frame ->
                processReceivedFrame(frame)
            }
        }
    }

    fun stop() {
        receiveJob?.cancel()
        receiveJob = null
        forwardingQueue.clearAll()
    }

    /**
     * Originates a logical DATA message from this local node to [destination].
     *
     * @return The unique 16-byte messageId representing this logical message.
     */
    suspend fun sendMessage(
        destination: PeerId,
        payload: ByteArray,
        ttl: Int = ProtocolLimits.INITIAL_TTL
    ): ByteArrayKey {
        val messageId = idGenerator.generateId()
        val packetId = idGenerator.generateId()
        val msgKey = messageId.asKey()
        val pktKey = packetId.asKey()

        val packet = MeshPacket(
            protocolVersion = ProtocolLimits.PROTOCOL_VERSION,
            packetId = packetId,
            messageId = messageId,
            sourceId = localPeerId.toByteArray(),
            destinationId = destination.toByteArray(),
            packetType = PacketType.DATA,
            ttl = ttl,
            sequenceNumber = sequenceCounter.getAndIncrement(),
            payload = payload
        )

        // Track for end-to-end delivery and retransmissions
        ackManager.trackOutboundMessage(msgKey, destination, payload, ttl, pktKey)

        // Mark our own packet as seen to suppress loops back to self
        duplicateCache.insert(pktKey)

        // Flood to all currently connected peers
        floodPacket(packet, ingressPeer = null)

        return msgKey
    }

    /**
     * Retries sending a previously originated message using a fresh packet ID.
     */
    suspend fun retryMessage(messageId: ByteArrayKey): Boolean {
        val retryPacket = ackManager.prepareRetry(messageId) ?: return false
        val newPktKey = retryPacket.packetId.asKey()

        // Cache the new packetId so our own node won't process reflections
        duplicateCache.insert(newPktKey)

        floodPacket(retryPacket, ingressPeer = null)
        return true
    }

    /**
     * Handles an incoming frame from [transport].
     */
    suspend fun processReceivedFrame(frame: ReceivedFrame) {
        val packet = try {
            codec.decode(frame.bytes)
        } catch (e: PacketValidationException) {
            _events.emit(MeshEvent.PacketRejected("Validation failed: ${e.message}", e))
            return
        } catch (e: Exception) {
            _events.emit(MeshEvent.PacketRejected("Decode exception: ${e.message}"))
            return
        }

        handlePacket(packet, ingressPeer = frame.from)
    }

    /**
     * Core controlled flooding decision logic.
     */
    suspend fun handlePacket(packet: MeshPacket, ingressPeer: PeerId?) {
        val packetKey = packet.packetId.asKey()
        val messageKey = packet.messageId.asKey()

        // 1. Duplicate check: drop immediately if already seen
        if (duplicateCache.contains(packetKey)) {
            _events.emit(MeshEvent.DuplicateDropped(packetKey))
            return
        }

        // 2. Insert into duplicate cache BEFORE forwarding to prevent loops across multi-paths
        duplicateCache.insert(packetKey)

        val destinationPeer = PeerId.fromByteArray(packet.destinationId)

        // 3. Check if destination is local node
        if (destinationPeer == localPeerId) {
            when (packet.packetType) {
                PacketType.DATA -> {
                    // Deliver locally
                    _events.emit(
                        MeshEvent.MessageDelivered(
                            messageId = messageKey,
                            source = PeerId.fromByteArray(packet.sourceId),
                            packet = packet
                        )
                    )

                    // Generate ACK back to sender
                    val ackPacket = ackManager.createAckFor(packet)
                    val ackPktKey = ackPacket.packetId.asKey()
                    duplicateCache.insert(ackPktKey)
                    floodPacket(ackPacket, ingressPeer = null)
                }

                PacketType.ACK -> {
                    val pending = ackManager.onAckReceived(packet)
                    if (pending != null) {
                        _events.emit(
                            MeshEvent.EndToEndAckReceived(
                                messageId = messageKey,
                                acknowledgedBy = PeerId.fromByteArray(packet.sourceId)
                            )
                        )
                    }
                }

                PacketType.CONTROL -> {
                    _events.emit(
                        MeshEvent.MessageDelivered(
                            messageId = messageKey,
                            source = PeerId.fromByteArray(packet.sourceId),
                            packet = packet
                        )
                    )
                }
            }
            return
        }

        // 4. Packet is not for local node: check TTL budget for relaying
        if (packet.ttl <= 1) {
            _events.emit(MeshEvent.TtlExpired(packetKey, messageKey))
            return
        }

        // 5. Forward with decremented TTL
        val forwardedPacket = packet.copy(ttl = packet.ttl - 1)
        floodPacket(forwardedPacket, ingressPeer = ingressPeer)
    }

    /**
     * Floods [packet] to all connected peers except [ingressPeer].
     */
    private suspend fun floodPacket(packet: MeshPacket, ingressPeer: PeerId?) {
        val targets = transport.connectedPeers().filter { it != ingressPeer }
        if (targets.isEmpty()) return

        val encoded = codec.encode(packet)
        val packetKey = packet.packetId.asKey()
        val messageKey = packet.messageId.asKey()
        val destination = PeerId.fromByteArray(packet.destinationId)
        val forwardedPeers = mutableSetOf<PeerId>()

        for (peer in targets) {
            val enqueued = forwardingQueue.enqueue(peer, encoded)
            if (!enqueued) {
                _events.emit(MeshEvent.ForwardingQueueFull(peer, packetKey))
            } else {
                // Poll and send directly via transport
                val frameToSend = forwardingQueue.poll(peer)
                if (frameToSend != null) {
                    when (val res = transport.send(peer, frameToSend)) {
                        is SendResult.Accepted -> forwardedPeers.add(peer)
                        is SendResult.Rejected -> {
                            // Link-level rejection
                        }
                    }
                }
            }
        }

        if (forwardedPeers.isNotEmpty()) {
            _events.emit(
                MeshEvent.PacketRelayed(
                    packetId = packetKey,
                    messageId = messageKey,
                    packetType = packet.packetType,
                    destination = destination,
                    forwardedTo = forwardedPeers
                )
            )
        }
    }
}

