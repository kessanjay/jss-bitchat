package com.jss.bitchat.core.mesh

import java.util.LinkedHashMap

/**
 * Thread-safe bounded LRU duplicate suppression cache.
 * Keyed by immutable 16-byte [ByteArrayKey].
 *
 * @param capacity Maximum number of packet IDs to retain (default 2048 per docs/networking/engine-guide/03-packet-protocol.md).
 * @param expirationMs Maximum retention duration in milliseconds (default 5 minutes = 300,000 ms).
 * @param timeProvider Lambda providing current epoch time in milliseconds.
 */
class DuplicateCache(
    private val capacity: Int = 2048,
    private val expirationMs: Long = 5 * 60 * 1000L,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val lock = Any()

    private class CacheEntry(
        val timestampMs: Long
    )

    private val map = object : LinkedHashMap<ByteArrayKey, CacheEntry>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ByteArrayKey, CacheEntry>?): Boolean {
            return size > capacity
        }
    }

    /**
     * Checks if [packetId] exists in the cache and has not expired.
     * Expired entries are removed upon lookup.
     */
    fun contains(packetId: ByteArrayKey): Boolean {
        synchronized(lock) {
            val entry = map[packetId] ?: return false
            val now = timeProvider()
            if (now - entry.timestampMs > expirationMs) {
                map.remove(packetId)
                return false
            }
            return true
        }
    }

    /**
     * Inserts [packetId] into the cache with the current timestamp.
     * Returns true if newly inserted, false if already present and refreshed.
     */
    fun insert(packetId: ByteArrayKey): Boolean {
        synchronized(lock) {
            val now = timeProvider()
            val existing = map[packetId]
            map[packetId] = CacheEntry(now)
            return existing == null || (now - existing.timestampMs > expirationMs)
        }
    }

    /**
     * Current number of non-expired entries in the cache.
     */
    fun size(): Int {
        synchronized(lock) {
            val now = timeProvider()
            val iterator = map.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value.timestampMs > expirationMs) {
                    iterator.remove()
                }
            }
            return map.size
        }
    }

    /**
     * Clears all entries.
     */
    fun clear() {
        synchronized(lock) {
            map.clear()
        }
    }
}

