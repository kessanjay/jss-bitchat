package com.jss.bitchat.core.protocol

import java.security.SecureRandom

/**
 * Thread-safe cryptographically secure packet identifier generator.
 * Produces 128-bit (16-byte) random identifiers.
 */
class PacketIdGenerator(
    private val secureRandom: SecureRandom = SecureRandom()
) {
    /**
     * Generates a cryptographically random 16-byte identifier.
     */
    fun generateId(): ByteArray {
        val id = ByteArray(ProtocolLimits.ID_SIZE_BYTES)
        secureRandom.nextBytes(id)
        return id
    }
}

