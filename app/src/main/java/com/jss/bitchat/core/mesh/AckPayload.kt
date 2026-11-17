package com.jss.bitchat.core.mesh

import com.jss.bitchat.core.protocol.MeshPacket
import com.jss.bitchat.core.protocol.PacketIdGenerator
import com.jss.bitchat.core.protocol.PacketType
import com.jss.bitchat.core.protocol.ProtocolLimits
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encapsulates the 33-byte structured payload for [PacketType.ACK] packets.
 *
 * Wire layout of ACK payload:
 * - acknowledgedMessageId (16 bytes): Original logical message being confirmed
 * - status (1 byte): 0x00 = SUCCESS, non-zero = error / failure
 * - acknowledgingNodeId (16 bytes): ID of the node issuing the ACK
 */
data class AckPayload(
    val acknowledgedMessageId: ByteArray,
    val status: Byte,
    val acknowledgingNodeId: ByteArray
) {
    init {
        require(acknowledgedMessageId.size == ProtocolLimits.ID_SIZE_BYTES) {
            "acknowledgedMessageId must be ${ProtocolLimits.ID_SIZE_BYTES} bytes"
        }
        require(acknowledgingNodeId.size == ProtocolLimits.ID_SIZE_BYTES) {
            "acknowledgingNodeId must be ${ProtocolLimits.ID_SIZE_BYTES} bytes"
        }
    }

    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(SIZE_BYTES).order(ByteOrder.BIG_ENDIAN)
        buffer.put(acknowledgedMessageId)
        buffer.put(status)
        buffer.put(acknowledgingNodeId)
        return buffer.array()
    }

    companion object {
        const val SIZE_BYTES = ProtocolLimits.ID_SIZE_BYTES + 1 + ProtocolLimits.ID_SIZE_BYTES // 33 bytes
        const val STATUS_SUCCESS: Byte = 0x00

        fun fromByteArray(bytes: ByteArray): AckPayload? {
            if (bytes.size < SIZE_BYTES) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val ackMsgId = ByteArray(ProtocolLimits.ID_SIZE_BYTES)
            buffer.get(ackMsgId)
            val status = buffer.get()
            val ackNodeId = ByteArray(ProtocolLimits.ID_SIZE_BYTES)
            buffer.get(ackNodeId)
            return AckPayload(ackMsgId, status, ackNodeId)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AckPayload
        if (!acknowledgedMessageId.contentEquals(other.acknowledgedMessageId)) return false
        if (status != other.status) return false
        if (!acknowledgingNodeId.contentEquals(other.acknowledgingNodeId)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = acknowledgedMessageId.contentHashCode()
        result = 31 * result + status
        result = 31 * result + acknowledgingNodeId.contentHashCode()
        return result
    }
}

