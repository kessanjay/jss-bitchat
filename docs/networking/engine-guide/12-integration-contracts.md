# 12 — Team Integration Contracts

## 1. Ownership

### Person 1 — Networking (you)
- `core/protocol/`
- `core/mesh/`
- `core/bluetooth/`
- network-facing use cases/facade
- mesh simulator and networking tests
- networking integration and physical demo

### Person 2 — UI
- Compose screens and navigation
- ViewModels and UI state
- onboarding, discovery, chat, mesh dashboard, settings
- no direct Bluetooth API usage

### Person 3 — Data, security and QA
- Room and DataStore
- repository implementations and migrations
- reviewed cryptographic design and Android Keystore
- security tests, test matrix and QA
- coordinate identity and message persistence contracts

## 2. Contracts to agree before parallel coding

1. `PeerId` format and creation owner.
2. `MeshPacket` fields and byte widths.
3. `PacketTransport` interface.
4. `MeshEngine` public API.
5. `MeshEvent` sealed hierarchy.
6. `DeliveryState` definitions.
7. ACK meaning and payload.
8. Repository event for received messages.
9. Error model.
10. UUID constants and GATT characteristic directions.

## 3. Suggested public facade

```kotlin
interface NetworkRepository {
    val networkState: StateFlow<NetworkState>
    val peers: StateFlow<List<Peer>>
    val events: SharedFlow<NetworkEvent>

    suspend fun startMesh(): Result<Unit>
    suspend fun stopMesh()
    suspend fun sendText(destination: PeerId, text: String): Result<MessageId>
}
```

This is a proposed app-facing contract. The team may split it into use cases. Keep Compose types out of it.

## 4. Events for Person 2

- NetworkStarted
- NetworkStopped
- PeerDiscovered
- PeerConnecting
- PeerReady
- PeerDisconnected
- MessageQueued
- MessageRelayed (telemetry only)
- MessageDelivered
- MessageFailed
- NetworkError

The UI must not infer delivery from a relay event.

## 5. Events/data for Person 3

On destination receipt, emit a domain message object or repository event containing:
- message ID
- source ID
- destination ID
- received timestamp
- encrypted payload or plaintext only according to agreed security boundary
- transport metadata needed for diagnostics

Do not let the mesh layer write directly to Room.

## 6. Change control

- One owner per package.
- Agree interface changes in a PR before implementation.
- Avoid editing another person's files without coordination.
- Keep PRs focused and buildable.
- Do not merge generated code without reviewing it.
