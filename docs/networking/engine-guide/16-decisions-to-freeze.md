# 16 — Decisions to Freeze Before Multi-Device Coding

The following decisions must be written down in an ADR and shared by all four team members.

| Decision | Recommended initial choice | Owner(s) |
|---|---|---|
| Node ID | Random 128-bit app identity | Person 3 + Person 1 |
| Packet ID | Random 128-bit per packet | Person 1 |
| Message ID | Random 128-bit per logical message | Person 1 + Person 3 |
| Wire format | Versioned binary, big-endian | Person 1 |
| Routing | Controlled flooding | Person 1 |
| Initial TTL | 5, max accepted 8 | Person 1 |
| Duplicate key | Packet ID | Person 1 |
| GATT service | One stable 128-bit UUID | Person 1 |
| RX/TX/CONTROL | Write / Notify / Control | Person 1 |
| Delivery semantics | Destination ACK required | Person 1 + Person 3 |
| Encryption | Reviewed E2E design, not custom crypto | Person 3 |
| Persistence | Room behind repository interface | Person 3 |
| UI API | NetworkRepository or use cases | Person 1 + Person 2 |
| Background operation | Foreground user-started MVP first | All |

## Freeze process

1. Review each decision with the team.
2. Test risky BLE assumptions on two physical devices.
3. Create/update an ADR.
4. Update the protocol documentation.
5. Only then implement code that depends on the decision.

Do not let different phones use different packet layouts or UUIDs during integration.
