package com.jss.bitchat.core.protocol

import java.util.Arrays

/**
 * Logical representation of a packet in the mesh network.
 *
 * @property protocolVersion Wire version, must be 1 for v1.
 * @property packetId 16-byte unique identifier for this packet instance (duplicate cache key).
 * @property messageId 16-byte logical message identifier preserved across relays/fragments.
 * @property sourceId 16-byte node ID of original creator.
 * @property destinationId 16-byte node ID of target or broadcast destination.
 * @property packetType Type of packet: DATA, ACK, or CONTROL.
 * @property ttl Time-To-Live hop count (0 to 8 in v1).
 * @property sequenceNumber Sender/session sequence number, unsigned 32-bit (represented as Long).
 * @property payload Arbitrary binary payload (0 to 4022 bytes in v1).
 */
data class MeshPacket(
    val protocolVersion: Int,
    val packetId: ByteArray,
    val messageId: ByteArray,
    val sourceId: ByteArray,
    val destinationId: ByteArray,
    val packetType: PacketType,
    val ttl: Int,
    val sequenceNumber: Long,
    val payload: ByteArray
) {
    init {
        require(packetId.size == ProtocolLimits.ID_SIZE_BYTES) {
            "packetId must be exactly ${ProtocolLimits.ID_SIZE_BYTES} bytes, was ${packetId.size}"
        }
        require(messageId.size == ProtocolLimits.ID_SIZE_BYTES) {
            "messageId must be exactly ${ProtocolLimits.ID_SIZE_BYTES} bytes, was ${messageId.size}"
        }
        require(sourceId.size == ProtocolLimits.ID_SIZE_BYTES) {
            "sourceId must be exactly ${ProtocolLimits.ID_SIZE_BYTES} bytes, was ${sourceId.size}"
        }
        require(destinationId.size == ProtocolLimits.ID_SIZE_BYTES) {
            "destinationId must be exactly ${ProtocolLimits.ID_SIZE_BYTES} bytes, was ${destinationId.size}"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MeshPacket

        if (protocolVersion != other.protocolVersion) return false
        if (!packetId.contentEquals(other.packetId)) return false
        if (!messageId.contentEquals(other.messageId)) return false
        if (!sourceId.contentEquals(other.sourceId)) return false
        if (!destinationId.contentEquals(other.destinationId)) return false
        if (packetType != other.packetType) return false
        if (ttl != other.ttl) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = protocolVersion
        result = 31 * result + packetId.contentHashCode()
        result = 31 * result + messageId.contentHashCode()
        result = 31 * result + sourceId.contentHashCode()
        result = 31 * result + destinationId.contentHashCode()
        result = 31 * result + packetType.hashCode()
        result = 31 * result + ttl
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "MeshPacket(" +
                "protocolVersion=$protocolVersion, " +
                "packetId=${packetId.toHexString()}, " +
                "messageId=${messageId.toHexString()}, " +
                "sourceId=${sourceId.toHexString()}, " +
                "destinationId=${destinationId.toHexString()}, " +
                "packetType=$packetType, " +
                "ttl=$ttl, " +
                "sequenceNumber=$sequenceNumber, " +
                "payloadLength=${payload.size})"
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}

