# Roadmap implementation status

This delivery implements a first substantial increment, not the entire roadmap.

## Implemented

- Additive V2/V3 Flyway migrations; existing encrypted messages are unchanged.
- Conversation-authorized cursor history with timestamp/UUID tiebreaking.
- Independent monotonic read positions, unread badges, and recent-activity sorting.
- Account-specific pin, archive, and mute settings.
- Extracted conversation list, message list, theme, privacy, and notification components.
- Automatic older-history loading with scroll preservation and a manual fallback.
- Encrypted versioned content with replies and bounded quote previews; jump to loaded replies.
- Encrypted local drafts, serialized/coalesced writes, and restoration on chat selection.
- Copy text and in-memory search of currently loaded messages.
- Backend-enforced blocking for new conversations, messages, and typing.
- Presence, typing, and receipt privacy; unread state does not depend on public receipts.
- Fingerprint verification stored per account/contact/key on this browser.
- Account-specific removal of local keys, drafts, outbox, and verification data with a key-loss warning.
- Light/dark themes and keyboard-focus improvements.
- Opt-in generic browser notifications for backgrounded open clients; mute respected.
- CI frontend tests/build/audit and backend H2/PostgreSQL jobs.

## Validation boundaries and known limitations

- Locally verified with Java 21, H2 migration/API/socket tests, Node Web Crypto tests,
  frontend content tests, production Vite build, and npm audit.
- PostgreSQL CI is configured but has not run in this workspace; Docker is unavailable.
- No automated browser/IndexedDB/scroll interaction suite or multi-instance Redis validation yet.
- Conversation-list pagination still uses offsets. Message-history cursors are stable.
- Unread positions synchronize through per-user events plus periodic reconciliation.
  Messages in the latest fetched page are considered read when the chat is visible;
  this is not viewport-level proof of reading.
- Search covers only loaded messages; reply jumps work only for loaded targets.
- Multiple tabs can overwrite the same draft; local-data removal requires other tabs signed out.
- Clearing local data does not delete the server account or exported backups.
- Mute governs implemented open-app notifications, not messages or future Web Push.
- Privacy changes cannot retract receipts/events already emitted; presence polling
  and already in-flight events can take time to reconcile.
- Blocking does not erase history or discard already queued ciphertext. An outbox
  item rejected due to blocking stays queued; explicit per-item cancellation is pending.
- Existing RSA-based encryption has no forward secrecy. Versioned message content
  is not a replacement for a reviewed encryption protocol or authenticated senders.

## Still to implement

1. First-unread marker, explicit mark-unread/read controls, richer accessibility,
   browser automation, and stronger concurrency/performance tests.
2. Message requests; robust account-deletion/retention behavior.
3. Session listing/revocation and immediate socket invalidation; password changes
   with client-side backup re-encryption; separately designed recovery keys.
4. Versioned cryptographic envelopes binding conversation and participant IDs.
5. Reactions, editing, delete-for-me/everyone, disappearing messages.
6. Encrypted images/files/voice notes, authorized ciphertext storage, quotas,
   cancellable uploads, chunking, and orphan cleanup.
7. Notification sounds, PWA and Web Push with generic ciphertext-only notifications.
8. Maintained-protocol selection, verified multi-device linking, revocation, and
   explicit encrypted-history-sharing policy; no custom ratchet implementation.
9. Group membership and secure rekeying, then call signaling and TURN integration.
10. Shared rate limiting, connection quotas, CSP/deployment hardening, monitoring,
    backup/restore drills, real multi-node Redis/PostgreSQL tests.

Attachment storage, Web Push delivery, TURN hosting, and a suitable maintained
cryptographic protocol are not selected or provisioned. Do not advertise these
features or production-ready/audited E2EE until implementation and validation exist.