# 04 — BLE GATT Transport

## 1. Roles and topology

A GATT connection has a client and server role. For a phone mesh, devices may need to host a GATT server and connect as a GATT client to other peers. Android hardware and OEM stacks may constrain simultaneous role/connection behaviour. Validate the actual demo phones early.

BLE GATT is the transport between adjacent phones only. Multi-hop forwarding is performed by the app's mesh layer.

## 2. Stable service and characteristic UUIDs

Define UUIDs once in `BleUuids.kt`. Use stable project UUIDs; do not generate new service UUIDs per installation.

Proposed service layout:
- Service UUID: one project-specific 128-bit UUID.
- RX characteristic: central writes frames to the server.
- TX characteristic: server sends frames using notifications or indications.
- CONTROL characteristic: capability/handshake/control messages.

Choose and freeze exact UUID constants in a protocol ADR before integration. All team members must use the same values.

## 3. Characteristic properties

| Characteristic | Properties | Direction |
|---|---|---|
| RX | WRITE, optionally WRITE_NO_RESPONSE after reliability testing | Client → Server |
| TX | NOTIFY (or INDICATE where confirmation is required) | Server → Client |
| CONTROL | READ/WRITE/NOTIFY as needed | Both directions |

The GATT server must check the characteristic UUID, offset, permissions, request length and connection identity. Respond with an appropriate GATT status. Do not accept arbitrary writes to unknown characteristics.

## 4. Connection setup

Client sequence:
1. Discover peer advertisement using service UUID.
2. Apply app-level peer identity/capability checks.
3. Call `connectGatt`.
4. Wait for `STATE_CONNECTED`.
5. Discover services.
6. Verify the expected service and characteristic UUIDs.
7. Enable TX notifications locally.
8. Write the Client Characteristic Configuration Descriptor (CCCD).
9. Exchange protocol version/capability handshake.
10. Mark link ready only after the handshake succeeds.

Server sequence:
1. Open GATT server.
2. Add the expected service and characteristics.
3. Start advertising the service UUID.
4. Accept connection callbacks.
5. Handle MTU and characteristic read/write callbacks.
6. Track notification subscriptions.
7. Remove connection state on disconnect.

## 5. GATT operation serialization

Android GATT operations are asynchronous. Do not fire multiple writes, descriptor writes, MTU requests and service discovery calls concurrently on the same connection.

Implement a per-connection `GattOperationQueue`:
- One outstanding operation at a time.
- Each operation has a timeout.
- Completion is signalled by the matching callback.
- Failure/timeout advances or resets the queue safely.
- Disconnect cancels pending operations.
- Callback results are correlated to the active operation.

Do not block the main thread waiting for callbacks.

## 6. Notifications

Client must:
- Call `setCharacteristicNotification(characteristic, true)`.
- Write CCCD descriptor to enable notifications on the remote server.
- Handle notification callback payloads.
- Disable notifications and clean up on disconnect when appropriate.

Server must:
- Track which connected clients subscribed.
- Notify only subscribed clients.
- Handle notification return values and congestion/failure.
- Queue or retry according to a bounded policy.

## 7. MTU and throughput

Do not hardcode a payload size based on a presumed MTU. Negotiate/request MTU where supported, observe the negotiated result, and calculate usable value bytes based on the ATT/GATT operation being used. Some APIs/devices behave differently. The transport framing layer must remain correct even at the smallest supported payload.

## 8. Discovery and advertising

- Filter by the project service UUID where possible.
- Use a compact advertisement; do not attempt to place full identity or messages in the advertisement.
- Advertise a short app marker/service UUID.
- Use scan throttling and bounded scan windows.
- Deduplicate scan results by a stable app-level identity learned during handshake, not solely by device name.
- Handle adapter disabled, scan failure, advertising unsupported, and permission denial.

## 9. BLE limitations

- BLE radio range is environment-dependent.
- Walls, interference, antenna design and handset placement affect reliability.
- Simultaneous advertising, scanning and multiple GATT connections must be tested on the actual phones.
- Do not promise a specific number of concurrent peers.
- A Bluetooth MAC address may be randomized and must not be treated as a permanent app identity.
