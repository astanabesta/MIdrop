# NFC feasibility prototype

Approved scope: detect a deliberate NFC interaction on a Redmi Note 13 Pro+ and Note 14 4G, including a receiver-background trial. No file sharing or device identity storage.

## Architecture
- One native Android app, installed on both phones. Sender enables foreground NFC reader mode and sends a SELECT APDU to the receiver's application ID over ISO-DEP.
- Receiver uses Android host card emulation (HCE) to respond and record a timestamped local event. Android, device settings and NFC routing decide whether the service runs. HCE is not guaranteed on both models. Receiver must be unlocked; force-stopping the app, NFC off, default payment/routing conflicts or vendor restrictions can prevent detection.
- Reader UI stays foreground. Receiver UI may be foreground or background; the tester records which condition they tried. The app does not automatically label the activity state.
- No personal details are sent. The fixed reply proves an app responded, not the person's identity; no authentication or encryption is implemented. Do not use this protocol for actual file sharing.
- Logs remain in private app preferences; users can clear them. Android may throttle or terminate apps and routing can vary. A successful foreground result does not prove reliable background detection.

## Implementation and verification plan
1. Minimal Android NFC reader, HCE service and APDU protocol, with explicit device capability and local event display.
2. Unit-test protocol acceptance and defensive copies.
3. GitHub Actions installs JDK 17, Android SDK and Gradle 8.9, runs unit tests and assembles a debug APK; upload the APK as an artifact.
4. On real phones, enable NFC and install the same APK. Start receiver on one and sender on the other; keep both unlocked. Tap upper backs and move slowly to locate antennas. Inspect both logs. Repeat with receiver sent Home. Swap roles; note Android/HyperOS versions and any routing or battery restrictions. Repeat several times to assess reliability.
5. Decide whether to pursue NFC, Bluetooth, or a combined approach based on observed results.
