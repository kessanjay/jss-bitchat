# 15 — Google Antigravity Implementation Prompts

Use one prompt per milestone. Ask the agent to inspect the project and report planned file changes before modifying files. Review every diff.

## Prompt 0 — Repository inspection

You are implementing the networking engine for JSS BitChat, an Android-only offline BLE mesh messaging app.

Project facts:
- Kotlin, package com.jss.bitchat
- minSdk 26
- BLE GATT adjacent-device transport
- App-level controlled flooding for multi-hop forwarding
- No cloud backend
- Person 1 owns protocol, mesh, BLE and networking integration

Read AGENTS.md, PROJECT_SPEC.md and all networking documents in docs/networking (or the actual location where these files are stored).

Do not change files yet. Report:
1. Existing Gradle/app structure.
2. Missing project setup.
3. Proposed package structure.
4. Public interfaces and dependencies.
5. Android BLE hardware/platform risks.
6. Milestone plan and test plan.
Identify contradictions or missing decisions. Wait for approval before coding.

## Prompt 1 — Protocol

Implement only the versioned binary packet protocol described in the networking documentation. Create packet models, packet type, protocol limits, deterministic codec, validator and JVM tests. Do not import Android Bluetooth APIs. Do not use JSON as the final wire format. Do not implement cryptography. Inspect existing code first and avoid unrelated changes. Run unit tests and report exact results.

## Prompt 2 — Mesh simulator

Create a pure Kotlin mesh simulator with simulated peers and transport. Simulate A-B-C-D and demonstrate delivery from A to D through B and C. Add tests for duplicate suppression, TTL expiry, ingress-peer exclusion, queue bounds, disconnected peers and malformed packets. Keep it independent of Android and Compose.

## Prompt 3 — Mesh router

Implement the mesh router and supporting duplicate cache, peer directory abstraction, forwarding queue, ACK state machine and bounded retry policy according to the spec. Preserve packet/message ID semantics. Never mark end-to-end delivered on local GATT write success. Add deterministic JVM tests. Do not implement BLE.

## Prompt 4 — BLE capability and permissions

Implement Android BLE capability checks and version-aware runtime permission helper for the project's target SDK. Handle Bluetooth unavailable/disabled, scan, advertise and connect permission states. Do not request permissions from a background class without an Activity/UI permission flow. Inspect current manifest and target SDK first. Add tests for pure decision logic where possible.

## Prompt 5 — BLE discovery

Implement BLE scanning and advertising using the stable service UUID constants. Include lifecycle-safe start/stop, scan failure handling, adapter state handling, result deduplication and capability checks. Do not put message payloads in advertisements. Do not implement GATT data exchange in this milestone. Test on physical devices.

## Prompt 6 — GATT client/server

Implement the agreed GATT service with RX, TX and CONTROL characteristics. Add server startup, client connection, service discovery, CCCD notification subscription, callback handling, per-connection serialized GATT operation queue, timeouts and disconnect cleanup. Use the exact shared UUID constants. Do not claim a link is READY until the protocol handshake succeeds. Add structured events and device test instructions.

## Prompt 7 — Framing and reassembly

Implement fragmenter and reassembler using the documented framing header. Derive fragment payload size from the negotiated MTU and actual GATT operation constraints. Bound transfer size, concurrent reassemblies and lifetime. Reject inconsistent metadata and oversized lengths before allocation. Add tests for out-of-order, duplicate, missing, malformed and expired fragments.

## Prompt 8 — BLE transport adapter

Implement PacketTransport using the BLE GATT layer. Expose received complete logical frames and link events as Flow. Serialize writes per connection. Ensure transport acceptance is not represented as end-to-end delivery. Do not leak BluetoothGatt objects outside the Bluetooth package. Add fake transport tests and physical two-phone test instructions.

## Prompt 9 — Mesh/BLE integration

Connect the pure Kotlin mesh engine to PacketTransport. Support multiple ready peers, ingress exclusion, duplicate suppression, TTL, bounded queueing, ACK and retry semantics. Ensure one peer's send failure does not prevent forwarding to other eligible peers. Add integration tests with fake transport and A-B-C-D topology.

## Prompt 10 — Integration review

Review the networking engine against every acceptance criterion. Run all unit tests, build debug, inspect lifecycle cleanup, permission handling, stale GATT callbacks, memory bounds, queue bounds and logging. Produce a checklist of verified, unverified and failing criteria. Do not claim physical multi-hop works unless it has been tested on actual phones.

## Agent operating rules

- Never overwrite user changes without asking.
- Do not create a second Git repository.
- Do not add cloud dependencies.
- Do not add unreviewed crypto.
- Do not invent BLE success from emulator tests.
- Keep changes inside the milestone scope.
- Run tests after each milestone.
- Report files changed, commands run, test outcomes and remaining risks.
