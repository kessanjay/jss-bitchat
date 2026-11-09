# 01 — Scope and Acceptance Criteria

## 1. Purpose

Build a local, decentralized networking engine that lets JSS BitChat devices discover one another and relay application packets over Bluetooth Low Energy (BLE), without internet access or a central server.

## 2. In-scope MVP

1. BLE capability and permission checks.
2. BLE advertising and scanning using a stable app service UUID.
3. GATT client and GATT server roles.
4. Bidirectional application packet exchange between two phones.
5. App-level peer identity (not Bluetooth MAC address as identity).
6. Packet encoding, decoding and validation.
7. Multi-hop controlled flooding.
8. Duplicate suppression and TTL.
9. Bounded send queues and retries.
10. Acknowledgement semantics that distinguish link-level success from end-to-end delivery.
11. Fragmentation/reassembly for payloads that exceed one GATT operation.
12. Connection lifecycle handling and structured network events.
13. JVM tests, Android BLE tests and four-device physical demo.

## 3. Explicitly out of scope for first networking milestone

- Cloud backend, Firebase or internet-based message delivery.
- Wi-Fi Direct or Wi-Fi mesh.
- Audio/video calling.
- Large-file transfer.
- Group key management and production-grade E2E encryption.
- Automatic operation after force-stop.
- Claims of guaranteed delivery when devices are disconnected or out of range.
- Dynamic optimal routing. Initial routing is controlled flooding.

## 4. Definition of done

### Protocol
- [ ] Valid packets encode and decode deterministically.
- [ ] Invalid version, type, length, identifiers and TTL are rejected.
- [ ] Maximum frame and payload limits are enforced.
- [ ] Unit tests cover boundary cases and malformed data.

### BLE
- [ ] App checks BLE hardware and peripheral/advertising capability.
- [ ] Runtime permissions are requested correctly for the OS version.
- [ ] Two phones discover the same JSS BitChat service.
- [ ] Two phones establish GATT connection and discover characteristics.
- [ ] TX notifications and RX writes work in both directions.
- [ ] Long logical packets are fragmented and reassembled safely.
- [ ] Disconnects and GATT errors are surfaced as events.

### Mesh
- [ ] Router delivers packets addressed to the local node.
- [ ] Router does not forward duplicates.
- [ ] Router decrements TTL exactly once per forwarding hop.
- [ ] Router never forwards to the ingress peer.
- [ ] Router uses bounded queues, caches and retries.
- [ ] A → B → C → D works on four physical phones.

### Integration
- [ ] UI can observe peer/link/network state without using Android Bluetooth APIs.
- [ ] Database owner receives message events through agreed interfaces.
- [ ] Network engine can be tested without Compose or Room.
- [ ] Demo works with mobile data and Wi-Fi internet disabled.

## 5. Non-functional goals

- Predictable resource use: bounded caches, queues, timers and payload sizes.
- Resilience: a single peer disconnect must not crash the process.
- Privacy: do not log message plaintext or secrets.
- Maintainability: protocol and routing core must be platform-independent.
- Transparency: UI must not label a message “delivered” merely because a local BLE write succeeded.
