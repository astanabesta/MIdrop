# Tap-to-share implementation plan

Approved experience: choose files in another app, select MIdrop from the share sheet, tap phones, and have the receiver accept or decline in a notification. Receiver should not need to open the app first after setup. Preserve the current manual Nearby flow as fallback until tested.

## Constraints
- NFC HCE may be invoked by the operating system in the background, but only where device unlock, screen state, Secure NFC and routing permit it. Xiaomi battery management and force-stop can affect behavior.
- Runtime notification, Bluetooth and nearby permissions must be granted during one-time onboarding. Heads-up and HyperOS island presentation belong to the OS; actions may require expansion or opening the app. Do not use overlay permission or bypass background activity restrictions.
- A sender-side visible activity is permitted after the share sheet. Notifications must use immutable, explicit PendingIntents, unique session identifiers, bounded expiry, and reject stale actions. Must support decline without starting a transfer.
- Nearby Connections requires verification of the authentication digits. Do not silently accept all connections. Use a short-lived random secret exchanged over NFC and bind authentication digits and both session roles to a keyed proof over the encrypted Nearby channel. Do not put the secret in the publicly discoverable endpoint name. Authenticated proof is necessary before sending or displaying sensitive metadata. Even then NFC is proximity, not proof of a person's identity.

## Steps and checks
1. New unit-tested session protocol: fresh random session ID and secret, versioned length-bounded NFC messages, expiration, HMAC over the canonical authentication digits/session ID/role; constant-time verification. Reject malformed, expired, replayed or duplicate requests. Explicit single active request limits.
2. Sender ACTION_SEND and ACTION_SEND_MULTIPLE for general files, with temporary URI grants retained for the life of the transfer; read display names and sizes defensively. Start a visible foreground send screen and Nearby advertisement under a public random ID, then wait for tap.
3. Sender NFC reader performs SELECT and sends a bounded offer APDU to receiver HCE. Receiver stores a short-lived request and posts a high-priority notification with Accept/Decline. NFC must not expose file contents. Explain notification privacy and sanitize untrusted names.
4. Notification action must be explicit, immutable and session-bound. Accept leads to an Android-permitted activity/service pathway, performs Nearby discovery for that session, and handles process recreation. Decline destroys session. Do not start a background foreground service without an applicable exemption; if necessary accepting opens a transfer screen.
5. Authenticate both sides by NFC-bound proofs before sending any payload. Handle timeouts, permission denial, concurrent offers, sender departure and cancellation. No file is transmitted until receiver acceptance and successful authentication.
6. Send multiple files using file payloads with a manifest and independently mapped IDs; do not rely on ordering between payload types. Receive into private temporary storage, enforce limits, validate filenames, then use user-approved system destination / scoped storage. Protect against partial and overwritten files; clean temporary files and close descriptors on failure. Show progress and clear outcome.
7. Unit-test malformed APDU, proof mismatch, expiration and payload state; instrumentation tests for notification actions and share intents. Run GitHub CI and test both directions, locked/unlocked, foreground/background, repeated taps, deny/accept, missing permissions, process death and battery restrictions on both physical Redmi phones.
8. Keep the previous manual flow available as fallback until NFC, notifications, session authentication and transfer are verified on both devices. Report any Android/HyperOS limitation rather than claiming system-level Apple parity.

## Icon
A minimal original mark uses two rounded paths converging around a central point, a small palette and no lettering. It references the legibility of nearby-sharing glyphs without reproducing the Quick Share or AirDrop logos. Android adaptive foreground and background are separate layers; the OS chooses the outer mask.
