package com.jss.bitchat.core.protocol

/**
 * Exception thrown when packet validation fails during decoding or packet creation.
 */
class PacketValidationException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * Validates wire packet fields, lengths, and constraints according to protocol v1.
 */
class PacketValidator(
    private val maxPacketSizeBytes: Int = ProtocolLimits.DEFAULT_MAX_PACKET_SIZE_BYTES
) {
    val maxPayloadSizeBytes: Int = maxPacketSizeBytes - ProtocolLimits.HEADER_SIZE_BYTES

    /**
     * Validates a decoded or constructed [MeshPacket].
     *
     * @throws PacketValidationException if any field violates protocol rules.
     */
    fun validate(packet: MeshPacket) {
        if (packet.protocolVersion != ProtocolLimits.PROTOCOL_VERSION) {
            throw PacketValidationException("Unsupported protocol version: ${packet.protocolVersion}. Only version 1 is supported.")
        }

        if (packet.packetId.size != ProtocolLimits.ID_SIZE_BYTES) {
            throw PacketValidationException("packetId size must be ${ProtocolLimits.ID_SIZE_BYTES} bytes, was ${packet.packetId.size}")
        }
        if (packet.messageId.size != ProtocolLimits.ID_SIZE_BYTES) {
            throw PacketValidationException("messageId size must be ${ProtocolLimits.ID_SIZE_BYTES} bytes, was ${packet.messageId.size}")
        }
        if (packet.sourceId.size != ProtocolLimits.ID_SIZE_BYTES) {
            throw PacketValidationException("sourceId size must be ${ProtocolLimits.ID_SIZE_BYTES} bytes, was ${packet.sourceId.size}")
        }
        if (packet.destinationId.size != ProtocolLimits.ID_SIZE_BYTES) {
            throw PacketValidationException("destinationId size must be ${ProtocolLimits.ID_SIZE_BYTES} bytes, was ${packet.destinationId.size}")
        }

        if (packet.ttl < ProtocolLimits.MIN_TTL || packet.ttl > ProtocolLimits.MAX_ACCEPTED_TTL) {
            throw PacketValidationException("TTL ${packet.ttl} is out of bounds [${ProtocolLimits.MIN_TTL}, ${ProtocolLimits.MAX_ACCEPTED_TTL}]")
        }

        if (packet.sequenceNumber < 0L || packet.sequenceNumber > 0xFFFF_FFFFL) {
            throw PacketValidationException("sequenceNumber $packet.sequenceNumber is outside unsigned 32-bit range [0, 4294967295]")
        }

        if (packet.payload.size > maxPayloadSizeBytes) {
            throw PacketValidationException("Payload length ${packet.payload.size} exceeds maximum allowed $maxPayloadSizeBytes bytes")
        }
    }

    /**
     * Validates the header fields before allocating or parsing the payload buffer.
     *
     * @param magic First two bytes.
     * @param version Protocol version byte.
     * @param type Wire value of the packet type.
     * @param flags Flags byte (must be 0x00 in v1).
     * @param ttl TTL byte value.
     * @param declaredPayloadLength Declared payload length from header.
     * @param remainingBytes Total bytes remaining in buffer after the 74-byte header.
     */
    fun validateHeader(
        magic: ByteArray,
        version: Int,
        type: Int,
        flags: Byte,
        ttl: Int,
        declaredPayloadLength: Int,
        remainingBytes: Int
    ): PacketType {
        if (magic.size != 2 || magic[0] != ProtocolLimits.MAGIC_BYTES[0] || magic[1] != ProtocolLimits.MAGIC_BYTES[1]) {
            val actual = if (magic.isNotEmpty()) magic.joinToString("") { "%02X".format(it) } else "empty"
            throw PacketValidationException("Invalid magic bytes: $actual. Expected 4A42 ('JB').")
        }

        if (version != ProtocolLimits.PROTOCOL_VERSION) {
            throw PacketValidationException("Unsupported protocol version: $version. Expected ${ProtocolLimits.PROTOCOL_VERSION}.")
        }

        val packetType = PacketType.fromWireValue(type)
            ?: throw PacketValidationException("Unknown packet type: $type")

        if (flags != ProtocolLimits.FLAGS_V1) {
            throw PacketValidationException("Non-zero reserved flags: 0x%02X. Protocol v1 requires all flag bits to be 0.".format(flags))
        }

        if (ttl < ProtocolLimits.MIN_TTL || ttl > ProtocolLimits.MAX_ACCEPTED_TTL) {
            throw PacketValidationException("Invalid TTL: $ttl. Must be between ${ProtocolLimits.MIN_TTL} and ${ProtocolLimits.MAX_ACCEPTED_TTL}.")
        }

        if (declaredPayloadLength < 0 || declaredPayloadLength > maxPayloadSizeBytes) {
            throw PacketValidationException("Invalid payload length: $declaredPayloadLength. Max allowed is $maxPayloadSizeBytes.")
        }

        if (declaredPayloadLength != remainingBytes) {
            throw PacketValidationException("Payload length mismatch: declared $declaredPayloadLength bytes, but buffer has $remainingBytes remaining bytes.")
        }

        return packetType
    }
}

