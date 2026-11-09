package com.jss.bitchat.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PacketProtocolTest {

    private lateinit var codec: PacketCodec
    private lateinit var validator: PacketValidator
    private lateinit var idGenerator: PacketIdGenerator

    @Before
    fun setUp() {
        validator = PacketValidator()
        codec = PacketCodec(validator)
        idGenerator = PacketIdGenerator()
    }

    private fun createValidPacket(
        type: PacketType = PacketType.DATA,
        ttl: Int = ProtocolLimits.INITIAL_TTL,
        seq: Long = 100L,
        payload: ByteArray = "Hello Mesh!".toByteArray(Charsets.UTF_8)
    ): MeshPacket {
        return MeshPacket(
            protocolVersion = ProtocolLimits.PROTOCOL_VERSION,
            packetId = idGenerator.generateId(),
            messageId = idGenerator.generateId(),
            sourceId = idGenerator.generateId(),
            destinationId = idGenerator.generateId(),
            packetType = type,
            ttl = ttl,
            sequenceNumber = seq,
            payload = payload
        )
    }

    @Test
    fun `packet ID generator produces 16 byte unique IDs`() {
        val id1 = idGenerator.generateId()
        val id2 = idGenerator.generateId()

        assertEquals(16, id1.size)
        assertEquals(16, id2.size)
        assertFalse("Generated IDs should not be identical", id1.contentEquals(id2))
    }

    @Test
    fun `encode and decode round-trip for DATA packet`() {
        val original = createValidPacket(PacketType.DATA)
        val encoded = codec.encode(original)

        assertEquals(ProtocolLimits.HEADER_SIZE_BYTES + original.payload.size, encoded.size)

        val decoded = codec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `encode and decode round-trip for ACK packet`() {
        val original = createValidPacket(PacketType.ACK)
        val encoded = codec.encode(original)
        val decoded = codec.decode(encoded)

        assertEquals(original, decoded)
        assertEquals(PacketType.ACK, decoded.packetType)
    }

    @Test
    fun `encode and decode round-trip for CONTROL packet`() {
        val original = createValidPacket(PacketType.CONTROL)
        val encoded = codec.encode(original)
        val decoded = codec.decode(encoded)

        assertEquals(original, decoded)
        assertEquals(PacketType.CONTROL, decoded.packetType)
    }

    @Test
    fun `empty payload packet round-trip succeeds`() {
        val original = createValidPacket(payload = ByteArray(0))
        val encoded = codec.encode(original)

        assertEquals(ProtocolLimits.HEADER_SIZE_BYTES, encoded.size)

        val decoded = codec.decode(encoded)
        assertEquals(original, decoded)
        assertEquals(0, decoded.payload.size)
    }

    @Test
    fun `maximum allowed payload packet round-trip succeeds`() {
        val maxPayload = ByteArray(ProtocolLimits.DEFAULT_MAX_PAYLOAD_SIZE_BYTES) { (it % 256).toByte() }
        val original = createValidPacket(payload = maxPayload)
        val encoded = codec.encode(original)

        assertEquals(ProtocolLimits.DEFAULT_MAX_PACKET_SIZE_BYTES, encoded.size)

        val decoded = codec.decode(encoded)
        assertEquals(original, decoded)
        assertEquals(ProtocolLimits.DEFAULT_MAX_PAYLOAD_SIZE_BYTES, decoded.payload.size)
    }

    @Test
    fun `field offsets and byte order are verified exactly against specification`() {
        val packetId = ByteArray(16) { 0x01 }
        val messageId = ByteArray(16) { 0x02 }
        val sourceId = ByteArray(16) { 0x03 }
        val destinationId = ByteArray(16) { 0x04 }
        val payload = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())

        val packet = MeshPacket(
            protocolVersion = 1,
            packetId = packetId,
            messageId = messageId,
            sourceId = sourceId,
            destinationId = destinationId,
            packetType = PacketType.DATA, // wire value 1
            ttl = 5,
            sequenceNumber = 0x12345678L, // 4 bytes big-endian
            payload = payload
        )

        val encoded = codec.encode(packet)

        // Total size must be header (76 bytes) + payload (3 bytes) = 79 bytes
        assertEquals(76 + 3, encoded.size)
        assertEquals(ProtocolLimits.HEADER_SIZE_BYTES, 76)

        // Offset 0..1: Magic 'J', 'B' (0x4A, 0x42)
        assertEquals(0x4A.toByte(), encoded[0])
        assertEquals(0x42.toByte(), encoded[1])

        // Offset 2: Version (1)
        assertEquals(0x01.toByte(), encoded[2])

        // Offset 3: Type (DATA=1)
        assertEquals(0x01.toByte(), encoded[3])

        // Offset 4: Flags (0x00)
        assertEquals(0x00.toByte(), encoded[4])

        // Offset 5: TTL (5)
        assertEquals(0x05.toByte(), encoded[5])

        // Offset 6..21: Packet ID (16 bytes)
        for (i in 6..21) {
            assertEquals(0x01.toByte(), encoded[i])
        }

        // Offset 22..37: Message ID (16 bytes)
        for (i in 22..37) {
            assertEquals(0x02.toByte(), encoded[i])
        }

        // Offset 38..53: Source ID (16 bytes)
        for (i in 38..53) {
            assertEquals(0x03.toByte(), encoded[i])
        }

        // Offset 54..69: Destination ID (16 bytes)
        for (i in 54..69) {
            assertEquals(0x04.toByte(), encoded[i])
        }

        // Offset 70..73: Sequence (0x12345678 in big-endian)
        assertEquals(0x12.toByte(), encoded[70])
        assertEquals(0x34.toByte(), encoded[71])
        assertEquals(0x56.toByte(), encoded[72])
        assertEquals(0x78.toByte(), encoded[73])

        // Offset 74..75: Payload Length (3 in big-endian: 0x00, 0x03)
        assertEquals(0x00.toByte(), encoded[74])
        assertEquals(0x03.toByte(), encoded[75])

        // Offset 76..78: Payload
        assertEquals(0xAA.toByte(), encoded[76])
        assertEquals(0xBB.toByte(), encoded[77])
        assertEquals(0xCC.toByte(), encoded[78])
    }

    @Test
    fun `rejects invalid magic bytes`() {
        val valid = codec.encode(createValidPacket())
        valid[0] = 0x58.toByte() // change 'J' to 'X'

        val ex = assertThrows(PacketValidationException::class.java) {
            codec.decode(valid)
        }
        assertTrue(ex.message!!.contains("Invalid magic bytes"))
    }

    @Test
    fun `rejects unsupported protocol version`() {
        val valid = codec.encode(createValidPacket())
        valid[2] = 2 // version 2

        val ex = assertThrows(PacketValidationException::class.java) {
            codec.decode(valid)
        }
        assertTrue(ex.message!!.contains("Unsupported protocol version"))
    }

    @Test
    fun `rejects unknown packet type`() {
        val valid = codec.encode(createValidPacket())
        valid[3] = 99 // unknown type

        val ex = assertThrows(PacketValidationException::class.java) {
            codec.decode(valid)
        }
        assertTrue(ex.message!!.contains("Unknown packet type"))
    }

    @Test
    fun `rejects non-zero flags`() {
        val valid = codec.encode(createValidPacket())
        valid[4] = 0x01 // non-zero flag

        val ex = assertThrows(PacketValidationException::class.java) {
            codec.decode(valid)
        }
        assertTrue(ex.message!!.contains("Non-zero reserved flags"))
    }

    @Test
    fun `rejects TTL out of range`() {
        val valid = codec.encode(createValidPacket())
        valid[5] = 9 // max accepted is 8

        val ex = assertThrows(PacketValidationException::class.java) {
            codec.decode(valid)
        }
        assertTrue(ex.message!!.contains("Invalid TTL"))
    }

    @Test
    fun `rejects truncated header`() {
        val valid = codec.encode(createValidPacket())
        val truncated = valid.copyOfRange(0, ProtocolLimits.HEADER_SIZE_BYTES - 1)

        val ex = assertThrows(PacketValidationException::class.java) {
            codec.decode(truncated)
        }
        assertTrue(ex.message!!.contains("Truncated packet"))
    }

    @Test
    fun `rejects truncated payload`() {
        val valid = codec.encode(createValidPacket(payload = byteArrayOf(1, 2, 3, 4)))
        val truncated = valid.copyOfRange(0, valid.size - 2)

        val ex = assertThrows(PacketValidationException::class.java) {
            codec.decode(truncated)
        }
        assertTrue(ex.message!!.contains("Payload length mismatch"))
    }

    @Test
    fun `rejects declared payload length larger than actual`() {
        val valid = codec.encode(createValidPacket(payload = byteArrayOf(1, 2)))
        // Corrupt payload length field to declare 50 bytes (offset 74..75)
        valid[74] = 0x00
        valid[75] = 50

        val ex = assertThrows(PacketValidationException::class.java) {
            codec.decode(valid)
        }
        assertTrue(ex.message!!.contains("Payload length mismatch"))
    }

    @Test
    fun `rejects declared payload length smaller than actual bytes`() {
        val valid = codec.encode(createValidPacket(payload = byteArrayOf(1, 2, 3, 4)))
        // Declare 2 bytes instead of 4 (offset 74..75)
        valid[74] = 0x00
        valid[75] = 2

        val ex = assertThrows(PacketValidationException::class.java) {
            codec.decode(valid)
        }
        assertTrue(ex.message!!.contains("Payload length mismatch"))
    }

    @Test
    fun `rejects payload exceeding maximum size limit during encode`() {
        val oversizedPayload = ByteArray(ProtocolLimits.DEFAULT_MAX_PAYLOAD_SIZE_BYTES + 1)
        val packet = createValidPacket(payload = oversizedPayload)

        val ex = assertThrows(PacketValidationException::class.java) {
            codec.encode(packet)
        }
        assertTrue(ex.message!!.contains("exceeds maximum"))
    }

    @Test
    fun `rejects declared payload length exceeding maximum size limit during decode`() {
        // Construct a wire packet where declared payload length > 4020 bytes
        val totalWireBytes = ProtocolLimits.HEADER_SIZE_BYTES + 10
        val buffer = ByteBuffer.allocate(totalWireBytes).order(ByteOrder.BIG_ENDIAN)
        buffer.put(ProtocolLimits.MAGIC_BYTES) // 0..1
        buffer.put(1.toByte()) // 2
        buffer.put(PacketType.DATA.wireValue.toByte()) // 3
        buffer.put(0x00.toByte()) // 4
        buffer.put(5.toByte()) // 5
        buffer.put(ByteArray(16)) // 6..21
        buffer.put(ByteArray(16)) // 22..37
        buffer.put(ByteArray(16)) // 38..53
        buffer.put(ByteArray(16)) // 54..69
        buffer.putInt(1) // 70..73
        buffer.putShort(4021.toShort()) // 74..75: declared payload length > 4020
        buffer.put(ByteArray(10)) // 76..85

        val wireBytes = buffer.array()
        val ex = assertThrows(PacketValidationException::class.java) {
            codec.decode(wireBytes)
        }
        assertTrue(ex.message!!.contains("Invalid payload length"))
    }

    @Test
    fun `unsigned 32-bit sequence numbers handled correctly`() {
        val highSeq = 0xFFFF_FFFEL // 4294967294L
        val packet = createValidPacket(seq = highSeq)
        val encoded = codec.encode(packet)
        val decoded = codec.decode(encoded)

        assertEquals(highSeq, decoded.sequenceNumber)
    }

    @Test
    fun `random malformed byte arrays do not crash decoder with unhandled errors`() {
        val random = java.util.Random(42)
        for (i in 0 until 50) {
            val randomBytes = ByteArray(random.nextInt(200))
            random.nextBytes(randomBytes)

            try {
                codec.decode(randomBytes)
            } catch (e: PacketValidationException) {
                // Expected safe rejection
            }
        }
    }
}
