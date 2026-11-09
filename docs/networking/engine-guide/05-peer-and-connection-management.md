# 05 — Peer and Connection Management

## 1. Peer identity

Generate an app-level node ID during first-run setup using a cryptographically secure random source. Persist it through the app's identity/security component. Do not derive it from the MAC address, phone number, Android ID, or user name.

A peer record may contain:
- PeerId
- display label (optional and untrusted)
- protocol version
- capability flags
- current link state
- last seen timestamp
- connection role(s)
- last error

Do not treat a display name or claimed role as authenticated identity.

## 2. Peer states

```text
UNKNOWN
  -> DISCOVERED
  -> CONNECTING
  -> CONNECTED
  -> HANDSHAKING
  -> READY
  -> DISCONNECTING
  -> DISCONNECTED

Any state may transition to FAILED, then retry/backoff or DISCOVERED.
```

`READY` means the app-level handshake completed and the transport can carry framed packets. A raw GATT connection is not yet a ready mesh link.

## 3. Peer directory

The mesh engine needs a platform-independent view of ready peers:

```kotlin
interface PeerDirectory {
    fun readyPeers(): Set<PeerId>
    fun isReady(peerId: PeerId): Boolean
    fun peerCapabilities(peerId: PeerId): PeerCapabilities?
}
```

The BLE connection manager owns the real GATT connection objects. Never expose `BluetoothDevice`, `BluetoothGatt`, or `BluetoothGattServer` to mesh/domain code.

## 4. Connection policy

For the first MVP:
- Keep connection attempts bounded.
- Avoid repeatedly connecting to the same peer.
- Use exponential backoff with jitter for reconnect attempts.
- Apply a maximum simultaneous connection setting that is configurable and experimentally validated.
- Prefer a simple stable topology for the four-phone demo.
- Do not create a full-mesh all-to-all topology unless the hardware test proves it works.

## 5. Link lifecycle

On disconnect:
1. Mark peer not ready.
2. Stop sending new frames to that peer.
3. Fail or requeue its in-flight frame according to retry policy.
4. Cancel pending GATT operations.
5. Close/release GATT resources.
6. Emit a link event.
7. Schedule reconnect only if session policy allows.

On Bluetooth adapter off:
- Stop scan/advertising.
- Mark all peers disconnected.
- Cancel queues/operations.
- Inform UI through network events.
- Resume only after user/system enables Bluetooth and permissions remain granted.

## 6. Handshake

At link setup, exchange a CONTROL message containing:
- protocol version
- node ID
- supported packet types
- supported framing version
- capability flags
- maximum accepted logical packet size

Reject or downgrade safely if there is no compatible protocol version. The handshake is not authentication. Security identity verification must be designed separately.
