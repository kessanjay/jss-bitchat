# 02 — Networking Architecture

## 1. Layering

```text
Compose UI (Person 2)
       |
       v
Use cases / domain-facing network facade
       |
       v
Mesh Engine (platform independent)
  - packet validation
  - duplicate cache
  - routing / controlled flooding
  - ACK and retry coordination
  - bounded forwarding queue
       |
       v
PacketTransport interface
       |
       v
BLE Transport (Android-specific)
  - scan / advertise
  - GATT client / server
  - characteristic read/write/notify
  - framing / reassembly
  - connection lifecycle
       |
       v
Android Bluetooth stack
```

Persistence is a separate boundary. The mesh engine emits message/network events or calls repository interfaces; it must not directly depend on Room entities or DAOs.

## 2. Package structure

Use the existing Gradle app module and package root `com.jss.bitchat`.

```text
app/src/main/java/com/jss/bitchat/
  core/
    protocol/
      MeshPacket.kt
      PacketType.kt
      PacketCodec.kt
      PacketValidator.kt
      PacketIdGenerator.kt
      ProtocolLimits.kt
    mesh/
      MeshRouter.kt
      PeerDirectory.kt
      DuplicateCache.kt
      ForwardingQueue.kt
      AckManager.kt
      RetryPolicy.kt
      MeshEvents.kt
      MeshConfig.kt
    bluetooth/
      BleCapabilities.kt
      BlePermissions.kt
      BleScanner.kt
      BleAdvertiser.kt
      BleGattClient.kt
      BleGattServer.kt
      BleConnectionManager.kt
      BleUuids.kt
      BlePacketTransport.kt
      GattOperationQueue.kt
      FrameFragmenter.kt
      FrameReassembler.kt
  domain/
    model/
      PeerId.kt
      Peer.kt
      LinkState.kt
      DeliveryState.kt
    repository/
      NetworkRepository.kt
  service/
    MeshForegroundService.kt
```

Names can be adapted to the actual project. Do not create duplicate models if the repository already defines equivalent contracts.

## 3. Core transport contract

The routing engine must depend on an interface, not Android Bluetooth classes.

```kotlin
interface PacketTransport {
    val receivedFrames: Flow<ReceivedFrame>
    val linkEvents: Flow<LinkEvent>

    suspend fun send(
        peerId: PeerId,
        frame: ByteArray
    ): SendResult

    fun connectedPeers(): Set<PeerId>
}
```

Suggested supporting models:

```kotlin
@JvmInline
value class PeerId(val value: String)

data class ReceivedFrame(
    val from: PeerId,
    val bytes: ByteArray,
    val receivedAtEpochMs: Long
)

sealed interface LinkEvent {
    data class Connected(val peerId: PeerId) : LinkEvent
    data class Disconnected(val peerId: PeerId, val reason: String?) : LinkEvent
    data class Failed(val peerId: PeerId?, val reason: String) : LinkEvent
}

sealed interface SendResult {
    data object Accepted : SendResult
    data class Rejected(val reason: String) : SendResult
}
```

`Accepted` means the local transport accepted/queued the operation. It does not mean the remote app parsed or delivered the message.

## 4. Mesh-facing contract

The mesh layer should accept validated logical packets and produce outbound packet intents.

```kotlin
interface MeshEngine {
    val events: Flow<MeshEvent>

    suspend fun start(localPeerId: PeerId)
    suspend fun stop()
    suspend fun sendMessage(destination: PeerId, payload: ByteArray): MessageId
}
```

The final API should be agreed before implementation. Keep UI state models separate from transport models.

## 5. Role model

A phone may need to operate as:
- BLE central/client: scans and initiates GATT connections.
- BLE peripheral/server: advertises and hosts the GATT service.

Do not assume the two roles are interchangeable on every handset. Check hardware capability and test the exact devices used for the demo.

## 6. Dependency rules

Allowed:
- Mesh depends on protocol and `PacketTransport`.
- BLE depends on Android Bluetooth APIs and transport contracts.
- Domain-facing facade depends on mesh and repository interfaces.

Not allowed:
- Mesh imports `android.bluetooth.*`.
- UI directly creates `BluetoothGatt` or `BluetoothLeScanner`.
- BLE transport writes directly to Room.
- Protocol codec depends on Android Context.
