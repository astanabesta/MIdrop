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
