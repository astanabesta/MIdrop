# iPhone tap-to-AirDrop feasibility (experimental)

Goal: select files on Android, tap an iPhone without an iPhone app, and trigger the intended iPhone's AirDrop acceptance prompt without an Android device picker. iPhone XR / iOS 18.7.4 is an unverified test target supplied by the user. Do not claim compatibility until actually tested.

## Known versus unknown
- The user's Redmi reportedly sees iPhones in system Quick Share. That does not demonstrate a documented API allowing this app to select an AirDrop receiver by address or control the system Quick Share UI.
- The existing MIdrop tap protocol requires our NFC HCE receiver on the other Android phone. iOS AirDrop is not our HCE receiver. No assumption is made that an iPhone responds to MIdrop's AID.
- Nearby Connections is used for MIdrop-to-MIdrop transfers. It is not itself a documented generic AirDrop target API. We cannot simply send a file to an address without a compatible protocol, discovery, session negotiation and receiver consent.
- AirDrop visibility, device and iOS versions, radio capabilities and security all matter. The iPhone owner must approve receipt. Bluetooth signal strength by itself does not reliably identify the tapped phone or protect against choosing a different nearby person.

## Test procedure when an iPhone is available
1. Record Redmi model, HyperOS/Android versions, iPhone model and exact iOS version. Enable both devices' Wi-Fi/Bluetooth/NFC where available; unlock both. Enable iPhone AirDrop Everyone for 10 Minutes.
2. Verify the baseline: Android system Quick Share discovers the iPhone and can send a harmless test photo with the iPhone accepting. Do not assume iOS 18.7.4 compatibility without testing.
3. Open MIdrop > Test iPhone NFC (experimental) on the Redmi, then slowly touch the backs of the phones at several positions. Note whether there is an event, shown NFC technologies and displayed outcome. If the iPhone shows Wallet/payment UI, dismiss it and do not approve anything. The diagnostic does not record hardware addresses or identifiers and only attempts selection of MIdrop's own AID.
4. Repeat with AirDrop enabled and disabled and record screen/unlock conditions. A missing NFC event is inconclusive; a detected NFC event does not prove the other phone is an iPhone or make AirDrop possible.
5. With two iPhones near the Android phone, confirm that any future discovery and association method cannot mistake the tapped phone for the other. Do not send files to either without explicit consent and a trustworthy association mechanism.

## Not implemented
No app-free iPhone tap-to-AirDrop transfer, target selection, detection of iPhone identity, automated bypass of Quick Share UI, iOS acceptance bypass or full background transfer is implemented. The NFC diagnostic exists to gather evidence; it does not promise feasibility. Any future approach needs supported APIs or a carefully reviewed implementation, hardware testing, robust privacy/security and an explicit fallback when an intended recipient cannot be verified.

## November 2026 source audit and next experiment

- [Universal AirDrop](https://github.com/Hritish-Narayanan/Airdrop-Reverse-Engineering) claims iPhone support in its README but its own detailed compatibility matrix and limitations say physical iPhone testing is pending. Its documented Mac/Android test logs use the project's own CLI receiver, not evidence of a native iPhone receiving. Its sender disables certificate verification; it must not be integrated as a trustworthy, automatic-target transport without a separate security review and real native-iPhone testing. Shared infrastructure Wi-Fi or manually joined hotspot also does not satisfy the requested tap-only flow.
- [AndroidAirDrop](https://github.com/j4dy/net.j4dy.AndroidAirDrop) explicitly targets Android-to-Mac on shared Wi-Fi, not iPhone. OpenDrop requires a suitable Apple Wireless Direct Link transport; availability on stock Redmi devices is not established.
- Google has announced a [Quick Share SDK](https://9to5google.com/2026/11/02/quick-share-sdk-apps/) for the first half of 2027. Its exact support for Android-to-native-iPhone tap without a picker or recipient app remains unverified.
- The opt-in **Test iPhone radio (experimental)** is passive BLE observation, not a transfer implementation. It reports counts of manufacturer 0x004c messages and structurally valid Continuity type 0x05 records, without retaining names, addresses, RSSI or raw bytes. Counts may include other devices, duplicates, false positives and spoofed packets; Android may filter beacons. Neither a hit nor absence proves AirDrop availability, the iPhone identity, or the desired wireless transport. Scan lasts at most 20 seconds, stops when the activity pauses, and logs aggregate counts only.
- Never transmit any selected file based solely on an observed radio frame, signal strength, nearest-device assumption or unauthenticated LAN address. A trustworthy association, recipient consent, transport and end-to-end interoperability remain open requirements.

### NFC and wireless capability check
Android 15+ exposes optional NFC observe mode, which can deliver filtered nonstandard polling frames to a registered HCE service. Android 17 adds the public reader polling annotation API used by DapTap to emit a custom frame; API availability and hardware support are separate. The read-only check in the radio research screen reports these flags and generic Wi-Fi Aware availability. It does not enable observe mode, register polling filters, create a wireless link, assess access to Quick Share's Apple link, identify an iPhone, or transfer a file. DapTap's reported real-phone testing is Galaxy S26 Ultra / Android 17 for contact exchange, not a Redmi Android 16 file transfer. Sources: https://developer.android.com/develop/connectivity/nfc/hce ; https://developer.android.com/reference/android/nfc/NfcAdapter ; https://github.com/CodingGenius0001/namedrop-android .
