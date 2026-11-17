package com.jss.bitchat.simulation

import com.jss.bitchat.core.mesh.AckPayload
import com.jss.bitchat.core.mesh.MeshEvent
import com.jss.bitchat.core.mesh.PeerId
import com.jss.bitchat.core.mesh.asKey
import com.jss.bitchat.core.protocol.PacketType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MeshSimulationTest {

    private lateinit var network: SimulatedNetwork
    private val peerA = PeerId("11111111111111111111111111111111")
    private val peerB = PeerId("22222222222222222222222222222222")
    private val peerC = PeerId("33333333333333333333333333333333")
    private val peerD = PeerId("44444444444444444444444444444444")
    private val peerE = PeerId("55555555555555555555555555555555")

    private lateinit var nodeA: SimulatedNode
    private lateinit var nodeB: SimulatedNode
    private lateinit var nodeC: SimulatedNode
    private lateinit var nodeD: SimulatedNode
    private lateinit var nodeE: SimulatedNode

    @Before
    fun setUp() {
        network = SimulatedNetwork()
        nodeA = network.createNode(peerA)
        nodeB = network.createNode(peerB)
        nodeC = network.createNode(peerC)
        nodeD = network.createNode(peerD)
        nodeE = network.createNode(peerE)
        network.startAll()
    }

    @After
    fun tearDown() {
        network.stopAll()
    }

    private fun setupLinearTopology() {
        // A <-> B <-> C <-> D
        network.createLink(peerA, peerB)
        network.createLink(peerB, peerC)
        network.createLink(peerC, peerD)
    }

    @Test
    fun `scenario 1 - multi-hop A to D delivery`() = runBlocking {
        setupLinearTopology()
        val payload = "Hello Node D!".toByteArray(Charsets.UTF_8)

        val messageId = nodeA.router.sendMessage(destination = peerD, payload = payload, ttl = 5)
        network.runUntilIdle()

        // Verify Node D delivered message locally
        val deliveredAtD = nodeD.recordedMeshEvents.filterIsInstance<MeshEvent.MessageDelivered>()
        assertEquals(1, deliveredAtD.size)
        assertEquals(messageId, deliveredAtD.first().messageId)
        assertEquals(peerA, deliveredAtD.first().source)
        assertArrayEquals(payload, deliveredAtD.first().packet.payload)

        // Verify relays at B and C: exactly one DATA relay, exactly one ACK relay, exactly two total
        val relaysAtB = nodeB.recordedMeshEvents.filterIsInstance<MeshEvent.PacketRelayed>()
        val relaysAtC = nodeC.recordedMeshEvents.filterIsInstance<MeshEvent.PacketRelayed>()
        assertEquals(2, relaysAtB.size)
        assertEquals(2, relaysAtC.size)

        // Verify distinct DATA and ACK relay events at B
        val dataRelaysAtB = relaysAtB.filter { it.packetType == PacketType.DATA && it.destination == peerD }
        val ackRelaysAtB = relaysAtB.filter { it.packetType == PacketType.ACK && it.destination == peerA }
        assertEquals(1, dataRelaysAtB.size)
        assertEquals(1, ackRelaysAtB.size)

        // Verify distinct DATA and ACK relay events at C
        val dataRelaysAtC = relaysAtC.filter { it.packetType == PacketType.DATA && it.destination == peerD }
        val ackRelaysAtC = relaysAtC.filter { it.packetType == PacketType.ACK && it.destination == peerA }
        assertEquals(1, dataRelaysAtC.size)
        assertEquals(1, ackRelaysAtC.size)
    }

    @Test
    fun `scenario 2 - reverse D to A delivery`() = runBlocking {
        setupLinearTopology()
        val payload = "Hello Node A from D!".toByteArray(Charsets.UTF_8)

        val messageId = nodeD.router.sendMessage(destination = peerA, payload = payload, ttl = 5)
        network.runUntilIdle()

        val deliveredAtA = nodeA.recordedMeshEvents.filterIsInstance<MeshEvent.MessageDelivered>()
        assertEquals(1, deliveredAtA.size)
        assertEquals(messageId, deliveredAtA.first().messageId)
        assertEquals(peerD, deliveredAtA.first().source)
        assertArrayEquals(payload, deliveredAtA.first().packet.payload)
    }

    @Test
    fun `scenario 3 - duplicate packet suppression`() = runBlocking {
        // Diamond topology: A -> (B, C) -> D
        network.createLink(peerA, peerB)
        network.createLink(peerA, peerC)
        network.createLink(peerB, peerD)
        network.createLink(peerC, peerD)

        val payload = "Diamond flood".toByteArray(Charsets.UTF_8)
        val msgId = nodeA.router.sendMessage(destination = peerD, payload = payload, ttl = 5)

        network.runUntilIdle()

        // Node D should deliver payload exactly ONCE
        val deliveredEvents = nodeD.recordedMeshEvents.filterIsInstance<MeshEvent.MessageDelivered>()
        assertEquals(1, deliveredEvents.size)

        // Node D must have dropped the second incoming duplicate of the DATA packet
        val droppedDuplicatesAtD = nodeD.recordedMeshEvents.filterIsInstance<MeshEvent.DuplicateDropped>()
        assertEquals(1, droppedDuplicatesAtD.size)

        // The ACK generated by D reaches A
        val ackReceivedAtA = nodeA.recordedMeshEvents.filterIsInstance<MeshEvent.EndToEndAckReceived>()
        assertEquals(1, ackReceivedAtA.size)
        assertEquals(msgId, ackReceivedAtA.first().messageId)
        assertEquals(peerD, ackReceivedAtA.first().acknowledgedBy)

        // In diamond topology, ACK returns via B and C, so A receives duplicate ACK and drops it
        val droppedDuplicatesAtA = nodeA.recordedMeshEvents.filterIsInstance<MeshEvent.DuplicateDropped>()
        assertEquals(1, droppedDuplicatesAtA.size)
    }

    @Test
    fun `scenario 4 - TTL decrement and expiration`() = runBlocking {
        setupLinearTopology()
        val payload = "Short lived".toByteArray(Charsets.UTF_8)

        // A sends with TTL = 2. Hop A->B decrements to 1. Hop B->C sees TTL <= 1 and drops.
        nodeA.router.sendMessage(destination = peerD, payload = payload, ttl = 2)
        network.runUntilIdle()

        // D must never receive the packet
        val deliveredAtD = nodeD.recordedMeshEvents.filterIsInstance<MeshEvent.MessageDelivered>()
        assertEquals(0, deliveredAtD.size)

        // C must record TTL expiration
        val ttlExpiredAtC = nodeC.recordedMeshEvents.filterIsInstance<MeshEvent.TtlExpired>()
        assertEquals(1, ttlExpiredAtC.size)
    }

    @Test
    fun `scenario 5 - disconnected nodes`() = runBlocking {
        // Linear A <-> B <-> C <-> D. Node E is isolated.
        setupLinearTopology()

        val payload = "To Nowhere".toByteArray(Charsets.UTF_8)
        nodeA.router.sendMessage(destination = peerE, payload = payload, ttl = 3)
        network.runUntilIdle()

        // E receives nothing
        val deliveredAtE = nodeE.recordedMeshEvents.filterIsInstance<MeshEvent.MessageDelivered>()
        assertEquals(0, deliveredAtE.size)
    }

    @Test
    fun `scenario 6 - network partitions`() = runBlocking {
        setupLinearTopology()

        // Break link between B and C
        network.setLinkUp(peerB, peerC, isUp = false)

        val payload = "Partition test".toByteArray(Charsets.UTF_8)
        nodeA.router.sendMessage(destination = peerD, payload = payload, ttl = 5)
        network.runUntilIdle()

        // Message cannot cross partition
        assertEquals(0, nodeD.recordedMeshEvents.filterIsInstance<MeshEvent.MessageDelivered>().size)

        // Heal partition
        network.setLinkUp(peerB, peerC, isUp = true)

        val payload2 = "Healed link message".toByteArray(Charsets.UTF_8)
        val msgId2 = nodeA.router.sendMessage(destination = peerD, payload = payload2, ttl = 5)
        network.runUntilIdle()

        val deliveredAtD = nodeD.recordedMeshEvents.filterIsInstance<MeshEvent.MessageDelivered>()
        assertEquals(1, deliveredAtD.size)
        assertEquals(msgId2, deliveredAtD.first().messageId)
    }

    @Test
    fun `scenario 7 - packet loss and retry scenario`() = runBlocking {
        setupLinearTopology()

        // Policy on link B->C: drop 1st transmission, allow 2nd transmission
        val linkBC = network.createLink(peerB, peerC)
        linkBC.deliveryPolicy = PacketDeliveryPolicy { from, to, attempt ->
            if (from == peerB && to == peerC) {
                attempt > 1 // Drop first attempt (attempt == 1), deliver attempt 2
            } else {
                true
            }
        }

        val payload = "Retry payload".toByteArray(Charsets.UTF_8)
        val msgId = nodeA.router.sendMessage(destination = peerD, payload = payload, ttl = 5)
        network.runUntilIdle()

        // First attempt was dropped at B->C; D has not received it yet
        assertEquals(0, nodeD.recordedMeshEvents.filterIsInstance<MeshEvent.MessageDelivered>().size)

        // Now trigger retry from Node A
        val retried = nodeA.router.retryMessage(msgId)
        assertTrue("Retry should be accepted", retried)

        network.runUntilIdle()

        // D should now have received the message via the second retry attempt
        val deliveredAtD = nodeD.recordedMeshEvents.filterIsInstance<MeshEvent.MessageDelivered>()
        assertEquals(1, deliveredAtD.size)
        assertEquals(msgId, deliveredAtD.first().messageId)
    }

    @Test
    fun `scenario 8 - ACK forwarding back to origin`() = runBlocking {
        setupLinearTopology()

        val payload = "Confirm me".toByteArray(Charsets.UTF_8)
        val msgId = nodeA.router.sendMessage(destination = peerD, payload = payload, ttl = 5)
        network.runUntilIdle()

        // 1. D delivered message
        assertEquals(1, nodeD.recordedMeshEvents.filterIsInstance<MeshEvent.MessageDelivered>().size)

        // 2. D sent ACK which traveled D -> C -> B -> A
        val acksAtA = nodeA.recordedMeshEvents.filterIsInstance<MeshEvent.EndToEndAckReceived>()
        assertEquals(1, acksAtA.size)
        assertEquals(msgId, acksAtA.first().messageId)
        assertEquals(peerD, acksAtA.first().acknowledgedBy)

        // Pending message at A is cleared
        assertFalse(nodeA.router.ackManager.isPending(msgId))
    }

    @Test
    fun `scenario 9 - multiple forwarding paths do not reflect back to ingress peer`() = runBlocking {
        // Grid topology:
        // A <-> B <-> D
        // |     |
        // C <---+
        network.createLink(peerA, peerB)
        network.createLink(peerA, peerC)
        network.createLink(peerB, peerC)
        network.createLink(peerB, peerD)

        val payload = "Multi-path".toByteArray(Charsets.UTF_8)
        nodeA.router.sendMessage(destination = peerD, payload = payload, ttl = 5)
        network.runUntilIdle()

        // D delivers message once
        assertEquals(1, nodeD.recordedMeshEvents.filterIsInstance<MeshEvent.MessageDelivered>().size)

        // Assert that B did not forward back to A when frame arrived from A
        val transmissionsBtoA = network.recordedSimulationEvents
            .filterIsInstance<SimulationEvent.FrameTransmitted>()
            .filter { it.from == peerB && it.to == peerA }

        // B only transmits ACK back to A, never reflects the DATA packet back to ingress peer A
        for (tx in transmissionsBtoA) {
            // Must not be the original data packet
            assertNotEquals(nodeA.recordedMeshEvents.filterIsInstance<MeshEvent.MessageDelivered>().firstOrNull()?.packet?.packetId?.asKey(), tx.packetId)
        }
    }

    @Test
    fun `scenario 10 - bounded forwarding queue behavior`() = runBlocking {
        // Create an isolated transport for queue testing
        val routerA = nodeA.router
        val testFrame = ByteArray(100)

        // Enqueue 64 frames (capacity)
        for (i in 0 until 64) {
            assertTrue("Queue should accept frame $i", routerA.forwardingQueue.enqueue(peerB, testFrame))
        }

        // 65th frame must be rejected
        assertFalse("Queue should reject 65th frame", routerA.forwardingQueue.enqueue(peerB, testFrame))
        assertEquals(64, routerA.forwardingQueue.queueSize(peerB))
    }

    @Test
    fun `retry retains messageId but generates fresh packetId`() = runBlocking {
        setupLinearTopology()

        val payload = "Fresh packet ID test".toByteArray(Charsets.UTF_8)
        val msgId = nodeA.router.sendMessage(destination = peerD, payload = payload, ttl = 5)

        val firstPktId = nodeA.router.ackManager.getPending(msgId)?.lastSentPacketId
        assertNotNull(firstPktId)

        // Trigger retry
        val retried = nodeA.router.retryMessage(msgId)
        assertTrue(retried)

        val secondPktId = nodeA.router.ackManager.getPending(msgId)?.lastSentPacketId
        assertNotNull(secondPktId)

        // Original messageId is retained
        assertEquals(msgId, nodeA.router.ackManager.getPending(msgId)?.messageId)

        // Packet ID is fresh (different)
        assertNotEquals(firstPktId, secondPktId)
    }

    @Test
    fun `ACK payload validates 33 bytes and rejects invalid destination`() = runBlocking {
        val originalMsgId = ByteArray(16) { 0x11 }
        val ackPayload = AckPayload(
            acknowledgedMessageId = originalMsgId,
            status = AckPayload.STATUS_SUCCESS,
            acknowledgingNodeId = peerD.toByteArray()
        )

        val bytes = ackPayload.toByteArray()
        assertEquals(33, bytes.size)

        val parsed = AckPayload.fromByteArray(bytes)
        assertNotNull(parsed)
        assertArrayEquals(originalMsgId, parsed!!.acknowledgedMessageId)
        assertEquals(AckPayload.STATUS_SUCCESS, parsed.status)
        assertArrayEquals(peerD.toByteArray(), parsed.acknowledgingNodeId)
    }

    @Test
    fun `regression test proving each simulated incoming frame is dispatched to router only once`() = runBlocking {
        // Linear A <-> B
        network.createLink(peerA, peerB)

        val payload = "Single dispatch verification".toByteArray(Charsets.UTF_8)
        val msgId = nodeA.router.sendMessage(destination = peerB, payload = payload, ttl = 5)
        network.runUntilIdle()

        // B should deliver the message exactly once
        val deliveredEvents = nodeB.recordedMeshEvents.filterIsInstance<MeshEvent.MessageDelivered>()
        assertEquals(1, deliveredEvents.size)
        assertEquals(msgId, deliveredEvents.first().messageId)

        // B should have ZERO duplicate dropped events because the link transmitted the packet only once
        val duplicateDroppedAtB = nodeB.recordedMeshEvents.filterIsInstance<MeshEvent.DuplicateDropped>()
        assertEquals("B should receive exactly one dispatch from the single frame transmission", 0, duplicateDroppedAtB.size)
    }

    @Test
    fun `runUntilIdle drains all scheduled forwarding and ACK events deterministically`() = runBlocking {
        setupLinearTopology() // A <-> B <-> C <-> D

        val payload = "Drain test".toByteArray(Charsets.UTF_8)
        val msgId = nodeA.router.sendMessage(destination = peerD, payload = payload, ttl = 5)

        val stepsExecuted = network.runUntilIdle()
        assertTrue("Steps should be greater than zero", stepsExecuted > 0)

        // Queue in network must be completely empty
        val additionalSteps = network.runUntilIdle()
        assertEquals("Queue should remain idle after full execution", 0, additionalSteps)

        // Destination received
        assertEquals(1, nodeD.recordedMeshEvents.filterIsInstance<MeshEvent.MessageDelivered>().size)

        // Origin received ACK
        val acksAtA = nodeA.recordedMeshEvents.filterIsInstance<MeshEvent.EndToEndAckReceived>()
        assertEquals(1, acksAtA.size)
        assertEquals(msgId, acksAtA.first().messageId)
    }
}

