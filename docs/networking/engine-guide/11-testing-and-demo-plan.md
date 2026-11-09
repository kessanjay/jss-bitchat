# 11 — Testing Strategy and Four-Phone Demo

## 1. Testing pyramid

### JVM unit tests
No Android device required:
- PacketCodec and PacketValidator.
- DuplicateCache.
- TTL handling.
- MeshRouter.
- ACK state machine.
- Retry policy.
- Queue bounds.
- Fragmentation/reassembly using fake MTU.
- Simulator topology.

### Android instrumentation/device tests
- Permission flow.
- Adapter capability checks.
- Scan/advertise.
- GATT server startup.
- GATT client connect.
- Service discovery.
- CCCD subscription.
- Bidirectional data.
- MTU behaviour.
- Disconnect cleanup.

### Physical multi-device tests
Required for BLE reliability. Emulator-only success is not acceptance.

## 2. Test progression

1. Protocol-only round trip.
2. Mesh simulator with A-B-C-D.
3. Two phones discover each other.
4. Two phones connect and exchange a short packet.
5. Two phones exchange a packet larger than one GATT operation.
6. Three phones relay a message.
7. Four phones relay A → B → C → D.
8. Disconnect C during transfer and verify bounded failure/retry behaviour.
9. Send duplicate packets through two paths and confirm one local delivery.
10. Turn off Wi-Fi and mobile data; repeat A → D.
11. Repeat with screen off/background only after the foreground MVP works.

## 3. Four-device topology

Before demo day, verify the actual topology. If each relay needs to connect to two neighbours, confirm the phone can maintain the required simultaneous GATT links while also advertising/scanning as needed.

Start with a linear chain:
- A connected to B.
- B connected to A and C.
- C connected to B and D.
- D connected to C.

If a device cannot maintain this topology reliably, adapt the demo topology and document the limitation. Do not claim a general four-peer capability from a single successful run.

## 4. Demo script

1. Install the same build on four phones.
2. Grant nearby-device permissions.
3. Enable Bluetooth.
4. Open JSS BitChat and start a mesh session on all devices.
5. Wait until intended adjacent peers show READY.
6. Disable Wi-Fi and mobile data on all phones.
7. Send a unique text message from A to D.
8. Show B and C forwarding telemetry.
9. Show D receiving the message.
10. Show destination ACK returning and A reaching DELIVERED.
11. Repeat with duplicate injection or a deliberately interrupted link.
12. Record device models, Android versions, test duration and result.

## 5. Test report template

| Field | Value |
|---|---|
| Build commit | |
| Phone A model / Android | |
| Phone B model / Android | |
| Phone C model / Android | |
| Phone D model / Android | |
| Wi-Fi/mobile data disabled | |
| Topology | |
| Message size | |
| A → D delivered | |
| End-to-end ACK received | |
| Forward count | |
| Duplicate drops | |
| Disconnect test result | |
| Notes/log reference | |
