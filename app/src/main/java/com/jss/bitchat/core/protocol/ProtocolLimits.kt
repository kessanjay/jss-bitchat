package com.jss.bitchat.core.protocol

/**
 * Protocol limits and constants for the binary packet protocol v1.
 * As defined in docs/networking/engine-guide/03-packet-protocol.md.
 */
object ProtocolLimits {
    /** Magic bytes: "JB" (0x4A, 0x42) */
    val MAGIC_BYTES = byteArrayOf(0x4A.toByte(), 0x42.toByte())

    /** Protocol version 1 */
    const val PROTOCOL_VERSION: Int = 1

    /** Required flags byte value for v1 (all bits zero) */
    const val FLAGS_V1: Byte = 0x00

    // Individual field sizes in bytes
    const val MAGIC_SIZE_BYTES: Int = 2
    const val VERSION_SIZE_BYTES: Int = 1
    const val TYPE_SIZE_BYTES: Int = 1
    const val FLAGS_SIZE_BYTES: Int = 1
    const val TTL_SIZE_BYTES: Int = 1
    const val ID_SIZE_BYTES: Int = 16 // 128-bit ID
    const val SEQUENCE_SIZE_BYTES: Int = 4 // unsigned 32-bit sequence
    const val PAYLOAD_LENGTH_SIZE_BYTES: Int = 2 // unsigned 16-bit length

    /**
     * Fixed header size in bytes:
     * 2 (magic) + 1 (version) + 1 (type) + 1 (flags) + 1 (ttl)
     * + 16 (packetId) + 16 (messageId) + 16 (sourceId) + 16 (destinationId)
     * + 4 (sequence) + 2 (payloadLength) = 76 bytes.
     */
    const val HEADER_SIZE_BYTES: Int =
        MAGIC_SIZE_BYTES +
        VERSION_SIZE_BYTES +
        TYPE_SIZE_BYTES +
        FLAGS_SIZE_BYTES +
        TTL_SIZE_BYTES +
        (ID_SIZE_BYTES * 4) +
        SEQUENCE_SIZE_BYTES +
        PAYLOAD_LENGTH_SIZE_BYTES

    /** Default maximum logical packet size in bytes (4 KiB) including header */
    const val DEFAULT_MAX_PACKET_SIZE_BYTES: Int = 4096

    /** Maximum payload size in bytes (4096 - 76 = 4020) */
    const val DEFAULT_MAX_PAYLOAD_SIZE_BYTES: Int = DEFAULT_MAX_PACKET_SIZE_BYTES - HEADER_SIZE_BYTES

    /** Initial TTL for originated packets */
    const val INITIAL_TTL: Int = 5

    /** Minimum accepted TTL */
    const val MIN_TTL: Int = 0

    /** Maximum accepted TTL (as defined in 03-packet-protocol.md section 4) */
    const val MAX_ACCEPTED_TTL: Int = 8
}
