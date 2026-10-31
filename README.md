# MIdrop NFC feasibility test

A small Android prototype for testing whether two Redmi phones can perform an NFC reader-to-card-emulation handshake, including when the receiver app is backgrounded. This is not a file-sharing app. See [architecture and test plan](docs/architecture.md).

## Get the APK
Open the repository's **Actions** tab, select a successful **Android debug APK** run, and download the **midrop-nfc-debug** artifact. Extract it, transfer the APK to both phones, and install it (Android may ask you to allow installs from that source). A debug APK is for testing, not production distribution.

## Test
1. Enable NFC and unlock both phones. Install and open the app on each. Check that the receiving phone reports HCE support.
2. Select **Receiver mode** on one phone and **Sender mode** on the other. Leave sender on its screen; bring the backs of the phones close together, varying the position if needed.
3. Look for a successful response on the sender and an event on the receiver. Record results.
4. On the receiver press Home without locking it, then repeat. Reopen the app and refresh its log. Swap phone roles and repeat.

Results are hardware- and settings-dependent. Nothing is shared or saved about another person. For NFC settings use the app button or search NFC in Android settings. Background response can fail even when NFC works.

## Local build
With JDK 17, Android SDK 35 and Gradle 8.9 installed: `gradle testDebugUnitTest assembleDebug`. CI builds the same debug target.
