package com.jss.bitchat.core.mesh

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded FIFO outbound queue manager for peer packet transmission.
 *
 * @param maxQueueSize Maximum frames queued per peer (default 64 per docs/networking/engine-guide/03-packet-protocol.md).
 */
class ForwardingQueue(
    private val maxQueueSize: Int = 64
) {
    private val queues = ConcurrentHashMap<PeerId, ArrayDeque<ByteArray>>()
    private val locks = ConcurrentHashMap<PeerId, Any>()

    private fun getLock(peerId: PeerId): Any = locks.computeIfAbsent(peerId) { Any() }

    /**
     * Enqueues a raw frame for [peerId].
     *
     * @return true if enqueued, false if queue is full (frame dropped/rejected).
     */
    fun enqueue(peerId: PeerId, frame: ByteArray): Boolean {
        val lock = getLock(peerId)
        synchronized(lock) {
            val q = queues.computeIfAbsent(peerId) { ArrayDeque() }
            if (q.size >= maxQueueSize) {
                return false
            }
            q.addLast(frame)
            return true
        }
    }

    /**
     * Polls the next frame queued for [peerId].
     */
    fun poll(peerId: PeerId): ByteArray? {
        val lock = getLock(peerId)
        synchronized(lock) {
            val q = queues[peerId] ?: return null
            val item = q.pollFirst()
            if (q.isEmpty()) {
                queues.remove(peerId)
                locks.remove(peerId)
            }
            return item
        }
    }

    /**
     * Returns current queue depth for [peerId].
     */
    fun queueSize(peerId: PeerId): Int {
        val lock = getLock(peerId)
        synchronized(lock) {
            return queues[peerId]?.size ?: 0
        }
    }

    /**
     * Clears queues for all peers or a specific peer upon disconnection.
     */
    fun clearPeer(peerId: PeerId) {
        val lock = getLock(peerId)
        synchronized(lock) {
            queues.remove(peerId)
            locks.remove(peerId)
        }
    }

    fun clearAll() {
        queues.clear()
        locks.clear()
    }
}

