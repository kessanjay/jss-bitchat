# JSS BitChat — Networking Engine Implementation Guide

This package is the implementation specification for the networking engine owned by Person 1.

## Project baseline

- App: JSS BitChat, Android-only offline BLE mesh messaging MVP
- Language: Kotlin
- Package: `com.jss.bitchat`
- Minimum SDK: API 26 (Android 8.0)
- UI: Jetpack Compose (owned by Person 2)
- Persistence/security/QA: Person 3
- Networking owner: Person 1 (you)
- Initial routing: bounded controlled flooding
- Demo: four physical Android phones, A → B → C → D, with internet disabled

## Read this in order

1. `01-scope-and-acceptance.md`
2. `02-architecture.md`
3. `03-packet-protocol.md`
4. `04-ble-gatt-transport.md`
5. `05-peer-and-connection-management.md`
6. `06-mesh-routing.md`
7. `07-ack-retry-and-delivery.md`
8. `08-framing-and-reassembly.md`
9. `09-android-runtime-and-permissions.md`
10. `10-observability-and-error-handling.md`
11. `11-testing-and-demo-plan.md`
12. `12-integration-contracts.md`
13. `13-security-boundaries.md`
14. `14-build-plan-and-github-workflow.md`
15. `15-antigravity-prompts.md`

## Important implementation rule

Do not ask an AI coding agent to implement the entire networking engine in one shot. Implement one milestone at a time, run tests, inspect the diff, and commit only working increments.

## Scope boundary

This package specifies the networking engine and its integration contracts. It does not replace the full Android application, the UI implementation, Room persistence implementation, or a reviewed end-to-end encryption design.

## Current technical caveats

- BLE does not automatically route application messages across multiple phones. The app must implement forwarding.
- Android phones may need to act as both BLE central and peripheral. Confirm both roles are supported on each test phone.
- Simultaneous connections, advertising, scanning, background behaviour, throughput, and MTU behaviour vary by device and Android version. Do not assume a universal connection count or throughput.
- Treat API 26 as the minimum app SDK, not as a guarantee that every device supports every BLE capability needed by the demo.
- The first MVP should run a user-visible mesh session while the app is foregrounded. Add background operation only after validating current Android foreground-service rules on the chosen target SDK.
