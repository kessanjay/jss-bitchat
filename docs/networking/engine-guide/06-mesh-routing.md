# 06 — Mesh Routing: Controlled Flooding

## 1. Why controlled flooding

For the MVP, controlled flooding is easier to implement and demonstrate than maintaining routing tables. A relay forwards a packet to ready peers except the peer it arrived from. Duplicate suppression and TTL bound repeated traffic.

It is not the most bandwidth-efficient algorithm. Keep queues and limits strict.

## 2. Receive pipeline

```text
Frame from BLE
   |
   v
Reassemble complete frame
   |
   v
Decode + validate packet
   |
   v
Check duplicate cache
   | duplicate
   +--------------------> drop
   |
   v
Insert packet ID into cache
   |
   v
Is destination local?
   | yes
   +--------------------> emit local delivery + ACK policy
   |
   v no
Is TTL <= 1?
   | yes
   +--------------------> drop (expired)
   |
   v no
Create forwarded packet with TTL - 1
   |
   v
Forward to ready peers except ingress peer
```

The duplicate cache should be updated before forwarding to reduce loops when packets arrive over multiple paths.

## 3. Routing pseudocode

```kotlin
suspend fun onPacketReceived(
    packet: MeshPacket,
    ingressPeer: PeerId
) {
    validator.validate(packet).getOrElse {
        emit(MeshEvent.PacketRejected(it))
        return
    }

    if (duplicateCache.contains(packet.packetId)) {
        emit(MeshEvent.DuplicateDropped(packet.packetId))
        return
    }

    duplicateCache.insert(packet.packetId)

    if (packet.destinationId.contentEquals(localNodeId)) {
        deliverLocally(packet)
        ackManager.onDestinationReceived(packet)
        return
    }

    if (packet.ttl <= 1) {
        emit(MeshEvent.TtlExpired(packet.packetId))
        return
    }

    val forwarded = packet.copy(ttl = packet.ttl - 1)

    val targets = peerDirectory.readyPeers() - ingressPeer
    for (peer in targets) {
        forwardingQueue.enqueue(peer, forwarded)
    }
}
```

This is conceptual pseudocode. Adapt byte-array equality and immutable packet design correctly. Do not mutate shared byte arrays.

## 4. Important routing decisions

- Preserve packet ID across relays so duplicate suppression works.
- Decrement TTL exactly once when forwarding, not on local delivery.
- Do not forward back to ingress peer.
- Do not forward to peers that are not READY.
- Do not block packet receive callbacks while writing to multiple peers.
- Queue per peer and apply backpressure.
- A failed send to one peer must not stop forwarding to other peers.
- For unicast, other relays may not know whether the destination is reachable. Use ACK/retry policy rather than assuming delivery.
- If the destination is unknown, flooding is still allowed while TTL remains.
- Broadcast semantics must be separately defined.

## 5. Duplicate cache

Key: packet ID.

Store:
- packet ID
- first-seen timestamp
- optional ingress peer
- optional packet type
- expiration time

Requirements:
- bounded entry count
- time-based expiry
- deterministic eviction (e.g. oldest expiry)
- thread-safe access
- no unbounded memory growth

## 6. Forwarding queue

Use per-peer bounded queues or a global queue with per-peer scheduling. Each item should include:
- target peer
- encoded frame
- packet/message ID
- enqueue time
- attempt count
- priority/type

Define queue-full behaviour: reject, drop lowest-priority eligible item, or apply backpressure. Do not silently grow memory.

## 7. Topology note

A → B → C → D only works if B and C can establish links to the neighbours required by the topology, or if the demo topology is arranged so each relay has the necessary adjacent connections. A phone cannot relay through a peer it cannot connect to. Verify physical BLE range and connection-role support early.
