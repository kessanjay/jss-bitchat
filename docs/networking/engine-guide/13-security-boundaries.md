# 13 — Security Boundaries and Threat Considerations

## 1. Networking versus cryptography

The networking engine transports packets. Person 3 owns the security implementation, in coordination with Person 1 on packet boundaries and identity fields.

Do not invent encryption, key exchange, signatures, authentication tags or cryptographic primitives.

## 2. Minimum transport hardening

- Validate every packet and fragment.
- Bound all lengths, queues, caches and retries.
- Reject unsupported protocol versions.
- Apply TTL and duplicate suppression.
- Treat peer names and role claims as untrusted.
- Do not log message plaintext or secrets.
- Avoid using MAC addresses as identities.
- Rate-limit malformed packet sources where feasible.
- Ensure relay nodes forward opaque protected payloads once E2E encryption is integrated.

## 3. End-to-end encryption boundary

For private messaging, encryption should occur at the sender before the payload enters the mesh. Relays should forward opaque ciphertext and should not need message plaintext.

Person 3 must select and document a vetted cryptographic library/protocol and Android Keystore key lifecycle. The team must review:
- identity binding
- key generation/storage
- key verification
- key rotation/recovery
- nonce uniqueness
- replay protection
- authenticated metadata
- group messaging implications

## 4. Faculty/admin broadcast

Do not trust a client-supplied string such as `role=FACULTY`. Role-aware broadcasts need authenticated credentials or signed authorization, including an offline verification strategy. This is later scope and should not block the basic mesh demo.
