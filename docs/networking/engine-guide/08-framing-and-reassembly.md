# 08 — BLE Framing, Fragmentation and Reassembly

## 1. Why framing is required

A logical mesh packet may be larger than the data that can be sent in one GATT write or notification. The transport layer must split and reassemble frames. Mesh routing should receive only complete validated logical packets.

## 2. Fragment header

A proposed fragment header:
- framing version: 1 byte
- transfer ID: 8 or 16 bytes
- fragment index: 2 bytes
- fragment count: 2 bytes
- total logical length: 4 bytes
- fragment payload: remaining permitted bytes

Freeze exact widths and endianness before coding. Do not reuse `packetId` as the only transfer ID if a packet can be retransmitted or multiple transfers can coexist.

## 3. Sender

1. Encode logical packet.
2. Calculate usable characteristic value size from negotiated MTU and operation type.
3. Subtract fragment header size.
4. Split into bounded fragments.
5. Enqueue fragments in order for that peer.
6. Do not interleave fragments from one transfer unless the receiver supports it.
7. Track transfer completion/failure and timeout.

## 4. Receiver

For each peer and transfer ID:
- Validate fragment index/count/total length.
- Reject inconsistent metadata.
- Reject total length above maximum.
- Reject duplicate fragments safely.
- Bound concurrent reassemblies.
- Expire incomplete transfers.
- Verify assembled byte count equals declared total.
- Pass complete frame to packet decoder only after full reassembly.

## 5. Security and robustness

Treat fragment metadata as untrusted. Never allocate the declared total length until it is checked against a strict cap. Avoid integer overflow in `fragmentIndex * chunkSize` calculations. Clear reassembly buffers on disconnect and timeout.

## 6. Testing

- Single-fragment frame.
- Two-fragment frame.
- Many-fragment frame.
- Out-of-order fragments (if supported).
- Duplicate fragment.
- Missing fragment.
- Inconsistent total length.
- Oversized total length.
- Reassembly timeout.
- Disconnect during transfer.
- Two simultaneous transfers from same peer.
