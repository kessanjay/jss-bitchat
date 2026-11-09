package com.jss.bitchat.core.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encodes and decodes [MeshPacket] to/from binary format following
 * docs/networking/engine-guide/03-packet-protocol.md.
 *
 * Wire format (big-endian):
 * | Offset | Field          | Size (Bytes) | Notes |
 * |--------|----------------|--------------|-------|
 * | 0      | Magic          | 2            | 'J', 'B' (0x4A, 0x42) |
 * | 2      | Version        | 1            | 1 for v1 |
 * | 3      | Type           | 1            | DATA=1, ACK=2, CONTROL=3 |
 * | 4      | Flags          | 1            | Reserved; must be 0x00 |
 * | 5      | TTL            | 1            | Unsigned hop budget [0, 8] |
 * | 6      | Packet ID      | 16           | Unique packet instance |
 * | 22     | Message ID     | 16           | Logical message ID |
 * | 38     | Source ID      | 16           | Origin peer |
 * | 54     | Destination ID | 16           | Destination peer or broadcast |
 * | 70     | Sequence       | 4            | Unsigned 32-bit sequence |
 * | 74     | Payload Length | 2            | Unsigned 16-bit byte length |
 * | 76     | Payload        | N            | 0 to 4020 bytes |
 * Total header: 76 bytes.
 */
class PacketCodec(
    private val validator: PacketValidator = PacketValidator()
) {

    /**
     * Serializes a [MeshPacket] into its canonical big-endian binary representation.
     *
     * @throws PacketValidationException if packet fails validation rules.
     */
    fun encode(packet: MeshPacket): ByteArray {
        validator.validate(packet)

        val totalSize = ProtocolLimits.HEADER_SIZE_BYTES + packet.payload.size
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        // 0..1: Magic (2 bytes)
        buffer.put(ProtocolLimits.MAGIC_BYTES)

        // 2: Version (1 byte)
        buffer.put(packet.protocolVersion.toByte())

        // 3: Type (1 byte)
        buffer.put(packet.packetType.wireValue.toByte())

        // 4: Flags (1 byte, 0x00)
        buffer.put(ProtocolLimits.FLAGS_V1)

        // 5: TTL (1 byte)
        buffer.put((packet.ttl and 0xFF).toByte())

        // 6..21: Packet ID (16 bytes)
        buffer.put(packet.packetId)

        // 22..37: Message ID (16 bytes)
        buffer.put(packet.messageId)

        // 38..53: Source ID (16 bytes)
        buffer.put(packet.sourceId)

        // 54..69: Destination ID (16 bytes)
        buffer.put(packet.destinationId)

        // 70..73: Sequence (4 bytes, unsigned 32-bit)
        buffer.putInt((packet.sequenceNumber and 0xFFFF_FFFFL).toInt())

        // 74..75: Payload Length (2 bytes, unsigned 16-bit)
        buffer.putShort((packet.payload.size and 0xFFFF).toShort())

        // 76..: Payload
        buffer.put(packet.payload)

        check(buffer.position() == totalSize) {
            "Encoded buffer position (${buffer.position()}) did not match expected total size ($totalSize)"
        }

        return buffer.array()
    }

    /**
     * Deserializes a binary array into a [MeshPacket].
     *
     * Safely validates packet structure, lengths, and magic bytes before allocating payload.
     *
     * @throws PacketValidationException if data is truncated, malformed, or invalid.
     */
    fun decode(bytes: ByteArray): MeshPacket {
        if (bytes.size < ProtocolLimits.HEADER_SIZE_BYTES) {
            throw PacketValidationException(
                "Truncated packet: size ${bytes.size} bytes is smaller than required header of ${ProtocolLimits.HEADER_SIZE_BYTES} bytes"
            )
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        // Read magic
        val magic = ByteArray(ProtocolLimits.MAGIC_SIZE_BYTES)
        buffer.get(magic)

        // Read version
        val version = buffer.get().toInt() and 0xFF

        // Read type
        val typeWire = buffer.get().toInt() and 0xFF

        // Read flags
        val flags = buffer.get()

        // Read TTL
        val ttl = buffer.get().toInt() and 0xFF

        // IDs
        val packetId = ByteArray(ProtocolLimits.ID_SIZE_BYTES)
        buffer.get(packetId)

        val messageId = ByteArray(ProtocolLimits.ID_SIZE_BYTES)
        buffer.get(messageId)

        val sourceId = ByteArray(ProtocolLimits.ID_SIZE_BYTES)
        buffer.get(sourceId)

        val destinationId = ByteArray(ProtocolLimits.ID_SIZE_BYTES)
        buffer.get(destinationId)

        // Sequence (unsigned 32-bit int)
        val sequenceInt = buffer.getInt()
        val sequenceNumber = sequenceInt.toLong() and 0xFFFF_FFFFL

        // Payload length (unsigned 16-bit int)
        val payloadLengthShort = buffer.getShort()
        val declaredPayloadLength = payloadLengthShort.toInt() and 0xFFFF

        val remainingBytes = buffer.remaining()

        // Validate header fields, limits and bounds before allocating payload
        val packetType = validator.validateHeader(
            magic = magic,
            version = version,
            type = typeWire,
            flags = flags,
            ttl = ttl,
            declaredPayloadLength = declaredPayloadLength,
            remainingBytes = remainingBytes
        )

        // Safe allocation after bounds checking
        val payload = ByteArray(declaredPayloadLength)
        buffer.get(payload)

        val packet = MeshPacket(
            protocolVersion = version,
            packetId = packetId,
            messageId = messageId,
            sourceId = sourceId,
            destinationId = destinationId,
            packetType = packetType,
            ttl = ttl,
            sequenceNumber = sequenceNumber,
            payload = payload
        )

        // Perform full validation
        validator.validate(packet)

        return packet
    }
}
