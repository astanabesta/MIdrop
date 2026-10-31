# MIdrop — NFC tap-to-share prototype

A test Android app for sharing files after an NFC tap. It requires both phones to have NFC, Google Play services, Wi-Fi, Bluetooth and MIdrop installed. The intended flow has not yet been verified end-to-end on physical phones; keep the manual Nearby fallback if needed.

## Install
Open the [Actions tab](https://github.com/astanabesta/MIdrop/actions), select the most recent successful **Android debug APK** run, download **midrop-debug**, extract the ZIP, and install the APK on both phones. Debug APKs are for testing, not production distribution. On Android you may need to allow installation from your browser or file manager.

## One-time setup, on each phone
1. Open MIdrop and tap **Set up background receiving**. Grant nearby, notification and any other requested permissions. Enable notification alerts for MIdrop. Enable NFC, Bluetooth and Wi-Fi.
2. Keep the phones unlocked for the NFC test. Android and Xiaomi may restrict NFC with the screen off, when locked, after force-stop, or under battery restrictions. If needed, allow MIdrop background activity in Xiaomi settings. Do not assume NFC will work when the screen is off.

## NFC share test
1. On the sending phone, select one or more files in Gallery or a file app. Tap **Share → MIdrop**. Grant permissions if prompted and keep the MIdrop transfer screen open.
2. Touch the backs of both phones together, moving slowly to find the NFC antennas. The receiving phone does not have to open MIdrop before tapping, but must have completed setup and be unlocked.
3. The receiver should see a request notification showing the number of files. Tap **Accept** or **Decline**. Android may require expanding the notification; the OS decides whether it appears as a heads-up or HyperOS island notification. Accept currently opens MIdrop's transfer screen.
4. Keep both transfer screens open until complete. Images save to Pictures/MIdrop; other files save to Download/MIdrop. The sender shows completion after the receiver confirms saving.

The tap exchanges a short-lived secret; Nearby authentication digits are bound to it using an HMAC proof before files are sent. The notification says **nearby device**, not a verified person's name. It supports up to 25 files in one share. Keep both devices' time reasonably accurate. These are prototype safeguards, not an independent security audit. Failure, device incompatibility, OS restrictions and limitations remain possible.

## Manual fallback
Open the app and use the existing **Receive a photo** and **Find devices and send** buttons. This flow requires manually comparing connection codes and both apps open.

## Testing
The NFC handshake test remains accessible from the home screen. See [architecture](docs/architecture.md) and [tap-sharing plan](docs/design/tap-sharing-plan.md). With JDK 17, Android SDK 35 and Gradle 8.9: `gradle testDebugUnitTest assembleDebug`. CI tests and builds, but does not test real NFC, notifications, Nearby connections or phone-to-phone transfers.

### Hyper Island experiment (Android 16)
The short-lived incoming request now asks Android to promote it as an ongoing notification while the sender is waiting. Accept and Decline remain available, and the notification expires when the two-minute request expires. On supported Xiaomi software this may be shown in Hyper Island; the system and user settings make the final decision. Check MIdrop's notification settings for Live Updates or promoted notifications if available. On unsupported devices it remains a normal notification. The transfer must not be assumed to continue in the background.
