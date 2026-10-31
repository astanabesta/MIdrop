# MIdrop — photo sharing prototype

A foreground Android prototype for sending one photo at a time between two Android phones with Google Play services. The sender can select a photo in the app or share one from Gallery; the receiver selects Receive, confirms the matching connection code, and explicitly accepts the photo. The received photo is saved under Pictures/MIdrop. Both devices must have the app, Google Play services, Bluetooth and Wi-Fi; permission and availability requirements vary by Android version.

## Build and install
Open [Actions](https://github.com/astanabesta/MIdrop/actions), select a successful **Android debug APK** workflow run for the latest commit, and download **midrop-nfc-debug**. Extract the ZIP and install the APK on both devices. This debug build is for testing, not production distribution.

## Test sharing
1. Open the app on both phones, unlock them and turn on Bluetooth and Wi-Fi. Accept nearby permissions when asked (Android 12 and earlier may also request location and storage). Keep the app open on both phones.
2. On the receiving phone, tap **Receive a photo**.
3. On the sending phone, choose a photo, then tap **Find devices and send** and choose the receiver. You can also share an image to MIdrop from Gallery.
4. Compare the code displayed on both phones. Only tap **Codes match** if they match. The receiver must then accept the photo.
5. Wait until the receiver says it saved the image to Pictures/MIdrop. If a phone cannot find the other, verify permissions, Wi-Fi, Bluetooth, Google Play services, location services where applicable, and Xiaomi battery restrictions; retry using Stop / start over.

**Limitations:** A working foreground device-list fallback is implemented, but physical device behavior has not yet been verified. NFC proximity does not trigger sharing; the separate experimental NFC test screen only tests an APDU handshake. The app does not do background discovery, auto-pairing, remembered contacts, multiple-file transfers, or Apple's animation. Never approve a connection if the displayed codes differ.

## Development
See [architecture](docs/architecture.md). With JDK 17, Android SDK 35 and Gradle 8.9: `gradle testDebugUnitTest assembleDebug`.
