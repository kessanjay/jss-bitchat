# 07 — ACK, Retry and Delivery Semantics

## 1. Three different success levels

Do not collapse these into one “sent” state.

1. `QUEUED_LOCAL`: local app accepted the message.
2. `LINK_ACCEPTED` / `LINK_CONFIRMED`: local GATT operation was accepted or the remote GATT operation was acknowledged, depending on write mode and callback.
3. `DELIVERED_END_TO_END`: destination application received and accepted the logical message, then returned a valid end-to-end ACK.

The UI must use these meanings accurately.

## 2. ACK model

An ACK packet should refer to the original `messageId` and identify the acknowledging node. For unicast, only the destination's authenticated ACK should mark end-to-end delivery.

A relay's GATT write callback is not a destination ACK.

Suggested ACK payload fields:
- acknowledged message ID (16 bytes)
- ACK status
- acknowledging node ID (16 bytes)
- optional reason code

The ACK itself is a routable packet with its own packet ID, source and destination. Define whether it travels by the same flooding strategy or a reverse-path cache. For MVP, controlled flooding is simpler but may create extra traffic. Limit ACK lifetime and duplicates.

## 3. Retry policy

- Bounded attempts (e.g. 3).
- Exponential backoff with jitter.
- Per-peer/link retry and end-to-end message retry are distinct.
- Stop retrying after expiry.
- Avoid retry storms after reconnect.
- Do not retry malformed or unsupported packets.
- Avoid retrying if the destination has already acknowledged the message.

## 4. Message delivery state

Suggested domain states:
- CREATED
- QUEUED
- SENDING
- RELAYING (optional telemetry)
- DELIVERED
- FAILED
- EXPIRED

Keep hop-level transport telemetry separate from end-to-end message state.

## 5. Duplicate ACK handling

ACK packets also need duplicate suppression. Repeated ACKs must not create infinite ACK loops. Normally ACK packets should not themselves trigger ACKs, unless a future reliability design explicitly requires it.
