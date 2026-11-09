# 03 — Packet Protocol Specification

## 1. Design goals

- Compact and bounded for BLE.
- Versioned for future compatibility.
- Deterministic encoding.
- Explicit identifiers and message semantics.
- Validate before routing.
- No JSON in the final on-air wire format.

JSON may be used in tests, logs with redacted content, or a simulator, but the production wire format should be binary.

## 2. Logical packet model

```kotlin
enum class PacketType(val wireValue: Int) {
    DATA(1),
    ACK(2),
    CONTROL(3)
}

data class MeshPacket(
    val protocolVersion: Int,
    val packetId: ByteArray,       // fixed 16 bytes
    val messageId: ByteArray,      // fixed 16 bytes
    val sourceId: ByteArray,       // fixed 16 bytes
    val destinationId: ByteArray,  // fixed 16 bytes
    val packetType: PacketType,
    val ttl: Int,
    val sequenceNumber: Long,
    val payload: ByteArray
)
```

Use fixed-length opaque IDs in the wire model. Human-readable IDs such as `phone-A` are simulator labels, not production identity values. Avoid using Bluetooth MAC addresses as durable identity.

## 3. Suggested v1 binary layout

All multi-byte integers are big-endian. The codec must document this and test it.

| Field | Bytes | Notes |
|---|---:|---|
| Magic | 2 | Constant, e.g. `JB` |
| Version | 1 | `1` for v1 |
| Type | 1 | DATA=1, ACK=2, CONTROL=3 |
| Flags | 1 | Reserved; unknown mandatory flags reject |
| TTL | 1 | Unsigned hop budget |
| Packet ID | 16 | Unique transmission/forwarding packet |
| Message ID | 16 | Stable ID for logical message |
| Source ID | 16 | Origin peer |
| Destination ID | 16 | Destination peer or defined broadcast value |
| Sequence | 4 | Unsigned sequence within sender session |
| Payload length | 2 | Unsigned byte length |
| Payload | N | Maximum defined in `ProtocolLimits` |

This is a proposed initial layout, not an immutable standard. Freeze it in an ADR before devices begin exchanging packets. Add a checksum only if the framing/transport design needs it; BLE already has link-layer error detection, while message authentication belongs to the security design.

## 4. Limits

Centralize all limits in `ProtocolLimits`. Initial conservative values for development (tune through testing):
- Maximum logical packet size: 4 KiB.
- Maximum payload: packet cap minus header.
- Initial TTL: 5.
- Maximum accepted TTL: 8.
- Duplicate-cache entry cap: 2,048.
- Duplicate-cache retention: 5 minutes.
- Per-peer outbound queue cap: 64 frames.
- Global pending message cap: 256.
- Reassembly concurrent transfers per peer: 4.
- Reassembly lifetime: 30 seconds.

These are engineering starting values, not measured BLE performance guarantees. Keep them configurable and record any changes.

## 5. Validation rules

Reject packets when:
- Magic is incorrect.
- Version is unsupported.
- Type is unknown.
- Header is truncated.
- Payload length does not match actual bytes.
- Payload exceeds configured maximum.
- TTL is outside the allowed range for that packet type.
- IDs have invalid lengths.
- Reserved flags are set unexpectedly.
- Packet is malformed or cannot be decoded without overflow.

Never allocate memory directly from an untrusted length field before checking it against a strict cap.

## 6. Identifier semantics

- `packetId`: identifies one network packet instance; duplicate cache key.
- `messageId`: identifies the original logical user message across relays/fragments.
- `sourceId`: origin node.
- `destinationId`: intended recipient.
- `sequenceNumber`: sender/session ordering and replay bookkeeping.

A relay must preserve message/source/destination identifiers. If forwarding mutates TTL, define whether packetId stays stable (recommended for duplicate suppression) or a new forwarding packet ID is created. For the initial flooding design, preserve packetId while TTL decreases; document this in tests.

## 7. Broadcast

If broadcast is required, reserve a destination ID value and define:
- Whether the origin receives its own broadcast.
- Whether relays forward once per packet ID.
- Whether broadcast ACKs are disabled or aggregated.
- How broadcast loop prevention works.

Do not accidentally treat an all-zero destination as broadcast unless that value is explicitly reserved in the protocol spec.

## 8. Codec tests

- Encode/decode round trip.
- Stable golden byte vector.
- Every packet type.
- Empty payload.
- Maximum payload.
- Truncated header.
- Truncated payload.
- Declared length larger/smaller than actual.
- Unsupported version.
- Unknown type.
- Invalid TTL.
- Invalid magic.
- Random malformed byte arrays do not crash the decoder.
