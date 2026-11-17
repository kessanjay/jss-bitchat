package com.jss.bitchat.core.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardingQueueTest {

    private val peerA = PeerId("11111111111111111111111111111111")
    private val peerB = PeerId("22222222222222222222222222222222")

    @Test
    fun `enqueues and polls in FIFO order`() {
        val queue = ForwardingQueue(maxQueueSize = 10)
        val frame1 = byteArrayOf(1, 2)
        val frame2 = byteArrayOf(3, 4)

        assertTrue(queue.enqueue(peerA, frame1))
        assertTrue(queue.enqueue(peerA, frame2))
        assertEquals(2, queue.queueSize(peerA))

        val polled1 = queue.poll(peerA)
        assertTrue(frame1.contentEquals(polled1))

        val polled2 = queue.poll(peerA)
        assertTrue(frame2.contentEquals(polled2))

        assertNull(queue.poll(peerA))
        assertEquals(0, queue.queueSize(peerA))
    }

    @Test
    fun `rejects frames when queue capacity is reached`() {
        val queue = ForwardingQueue(maxQueueSize = 3)

        assertTrue(queue.enqueue(peerA, byteArrayOf(1)))
        assertTrue(queue.enqueue(peerA, byteArrayOf(2)))
        assertTrue(queue.enqueue(peerA, byteArrayOf(3)))

        // 4th frame must be rejected
        assertFalse(queue.enqueue(peerA, byteArrayOf(4)))
        assertEquals(3, queue.queueSize(peerA))

        // PeerB queue should be unaffected
        assertTrue(queue.enqueue(peerB, byteArrayOf(10)))
        assertEquals(1, queue.queueSize(peerB))
    }
}

