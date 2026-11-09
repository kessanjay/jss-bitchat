# 10 — Observability, Diagnostics and Error Handling

## 1. Structured events

Expose typed events, not ad-hoc strings only.

Suggested event categories:
- AdapterStateChanged
- ScanStarted / ScanStopped / ScanFailed
- PeerDiscovered
- ConnectionStarted / ConnectionReady / ConnectionLost
- HandshakeSucceeded / HandshakeFailed
- PacketReceived / PacketRejected
- DuplicateDropped
- PacketForwardQueued
- PacketForwardFailed
- TtlExpired
- MessageDelivered
- MessageExpired
- QueueOverflow
- ReassemblyFailed

Events should include relevant peer/message IDs, timestamps, and safe error codes. Avoid payload plaintext.

## 2. Logging

Allowed:
- peer pseudonymous ID
- packet/message ID
- packet type
- TTL
- byte length
- GATT status code
- elapsed time
- state transition

Do not log:
- message plaintext
- private keys
- authentication tokens
- raw cryptographic material
- unnecessary device identifiers

Use log levels and a debug flag. Do not leave extremely verbose production logging enabled.

## 3. Error categories

- PermissionDenied
- BluetoothUnavailable
- AdapterDisabled
- AdvertisingUnsupported
- ScanStartFailed
- GattConnectFailed
- ServiceDiscoveryFailed
- CharacteristicMissing
- CccdWriteFailed
- GattOperationTimeout
- GattOperationRejected
- FrameMalformed
- ReassemblyTimeout
- PacketInvalid
- QueueFull
- PeerDisconnected
- ProtocolIncompatible

## 4. Recovery rules

- Recoverable link error: close/release GATT, emit disconnect, schedule bounded reconnect.
- Invalid packet: drop and count; do not crash.
- Queue full: apply documented backpressure/drop policy.
- Adapter off: stop all BLE work and update state.
- Permission revoked: stop affected operation and request user action through UI.
- Reassembly timeout: discard incomplete transfer and free memory.
- Unexpected callback: validate connection identity/state; ignore stale callbacks safely.

## 5. Diagnostics screen contract

The UI teammate may show:
- mesh session status
- local node ID (shortened)
- nearby peers
- ready connections
- last-seen timestamps
- packet counters
- forwarded/dropped/expired counters
- last error
- debug event log with no plaintext
