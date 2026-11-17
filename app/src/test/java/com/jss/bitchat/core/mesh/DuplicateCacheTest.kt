package com.jss.bitchat.core.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateCacheTest {

    @Test
    fun `inserts and detects duplicates correctly`() {
        var mockTime = 1000L
        val cache = DuplicateCache(capacity = 10, expirationMs = 5000L, timeProvider = { mockTime })

        val keyA = ByteArray(16) { 0x01 }.asKey()
        val keyB = ByteArray(16) { 0x02 }.asKey()

        assertFalse(cache.contains(keyA))
        assertTrue(cache.insert(keyA))
        assertTrue(cache.contains(keyA))

        // Re-inserting existing non-expired key returns false
        assertFalse(cache.insert(keyA))

        assertFalse(cache.contains(keyB))
        assertTrue(cache.insert(keyB))
        assertEquals(2, cache.size())
    }

    @Test
    fun `enforces bounded capacity with LRU eviction`() {
        var mockTime = 1000L
        val cache = DuplicateCache(capacity = 3, expirationMs = 10000L, timeProvider = { mockTime })

        val key1 = ByteArray(16) { 1 }.asKey()
        val key2 = ByteArray(16) { 2 }.asKey()
        val key3 = ByteArray(16) { 3 }.asKey()
        val key4 = ByteArray(16) { 4 }.asKey()

        cache.insert(key1)
        cache.insert(key2)
        cache.insert(key3)

        assertEquals(3, cache.size())
        assertTrue(cache.contains(key1))

        // Inserting 4th key should evict oldest (key2, because key1 was accessed by contains)
        cache.insert(key4)
        assertEquals(3, cache.size())

        assertTrue(cache.contains(key4))
        assertTrue(cache.contains(key1))
        assertTrue(cache.contains(key3))
        assertFalse(cache.contains(key2))
    }

    @Test
    fun `expires entries after retention period`() {
        var mockTime = 10_000L
        val cache = DuplicateCache(capacity = 10, expirationMs = 5000L, timeProvider = { mockTime })

        val key = ByteArray(16) { 0xAA.toByte() }.asKey()
        cache.insert(key)
        assertTrue(cache.contains(key))

        // Advance clock past expiration
        mockTime += 5001L

        assertFalse(cache.contains(key))
        assertEquals(0, cache.size())
    }
}
