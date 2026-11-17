package com.jss.bitchat.core.mesh

import com.jss.bitchat.core.protocol.ProtocolLimits
import kotlinx.coroutines.flow.Flow

/**
 * Canonical 16-byte identifier represented as a 32-character hexadecimal string.
 * Aligns with docs/networking/engine-guide/02-architecture.md.
 */
@JvmInline
value class PeerId(val value: String) {
    init {
        require(value.length == ProtocolLimits.ID_SIZE_BYTES * 2) {
            "PeerId value must be exactly ${ProtocolLimits.ID_SIZE_BYTES * 2} hex characters, was ${value.length}: $value"
        }
        require(value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            "PeerId must contain only valid hexadecimal characters: $value"
        }
    }

    /**
     * Converts this canonical hex PeerId into its raw 16-byte array representation.
     */
    fun toByteArray(): ByteArray {
        val bytes = ByteArray(ProtocolLimits.ID_SIZE_BYTES)
        for (i in 0 until ProtocolLimits.ID_SIZE_BYTES) {
            val high = Character.digit(value[i * 2], 16)
            val low = Character.digit(value[i * 2 + 1], 16)
            bytes[i] = ((high shl 4) or low).toByte()
        }
        return bytes
    }

    companion object {
        /**
         * Creates a canonical lowercase hex [PeerId] from raw 16 bytes.
         */
        fun fromByteArray(bytes: ByteArray): PeerId {
            require(bytes.size == ProtocolLimits.ID_SIZE_BYTES) {
                "Byte array must be exactly ${ProtocolLimits.ID_SIZE_BYTES} bytes, was ${bytes.size}"
            }
            val hex = buildString(ProtocolLimits.ID_SIZE_BYTES * 2) {
                for (b in bytes) {
                    val intVal = b.toInt() and 0xFF
                    val hexStr = Integer.toHexString(intVal)
                    if (hexStr.length == 1) append('0')
                    append(hexStr)
                }
            }
            return PeerId(hex)
        }
    }
}

/**
 * Wrapper for ByteArray keys in HashMaps/HashSets that provides content-based equals and hashCode.
 */
class ByteArrayKey(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ByteArrayKey
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = bytes.joinToString("") { "%02x".format(it) }
}

fun ByteArray.asKey(): ByteArrayKey = ByteArrayKey(this)

/**
 * Raw incoming frame received over the transport link.
 */
data class ReceivedFrame(
    val from: PeerId,
    val bytes: ByteArray,
    val receivedAtEpochMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ReceivedFrame
        if (from != other.from) return false
        if (!bytes.contentEquals(other.bytes)) return false
        if (receivedAtEpochMs != other.receivedAtEpochMs) return false
        return true
    }

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + receivedAtEpochMs.hashCode()
        return result
    }
}

/**
 * Link lifecycle events emitted by the transport layer.
 */
sealed interface LinkEvent {
    data class Connected(val peerId: PeerId) : LinkEvent
    data class Disconnected(val peerId: PeerId, val reason: String?) : LinkEvent
    data class Failed(val peerId: PeerId?, val reason: String) : LinkEvent
}

/**
 * Result of submitting a packet to the transport.
 */
sealed interface SendResult {
    data object Accepted : SendResult
    data class Rejected(val reason: String) : SendResult
}

/**
 * Low-level packet transport contract as defined in docs/networking/engine-guide/02-architecture.md.
 */
interface PacketTransport {
    val receivedFrames: Flow<ReceivedFrame>
    val linkEvents: Flow<LinkEvent>

    suspend fun send(peerId: PeerId, frame: ByteArray): SendResult
    fun connectedPeers(): Set<PeerId>
}

