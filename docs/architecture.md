# Photo-sharing prototype architecture

The initial approved NFC feasibility test remains available as a separate screen. The expanded prototype prioritizes usable sharing with device-list fallback, not an unproven NFC gesture.

- One native Android application, Android 10+ and Google Play services required, installed on both phones.
- Sender selects one image with Android's document picker or receives an image via ACTION_SEND. Receiver explicitly starts foreground Nearby Connections advertising. Sender discovers available receivers and chooses one.
- Both users must compare Nearby's authentication digits and confirm. The connected sender offers only image metadata; the receiver separately approves the image before file data is sent. Nearby's FILE payload handles the transfer. Sender and receiver display progress, and receiver saves to MediaStore Pictures/MIdrop after a successful transfer.
- No server, user account, phone identifier store or background service. No automatic NFC trigger. No claim that file exchange, background routing or performance works on the target phones until tested physically. Discovery needs Android permissions, Google Play services, Wi-Fi/Bluetooth and possibly Xiaomi configuration.
- Nearby is an encrypted transport, but authentication depends on people comparing the codes. The prototype treats incoming content as untrusted; received image bytes are not decoded or validated before saving. This is not production-hardened, and the UI is a functional prototype.
- Original NFC experiment: sender enables reader mode and SELECTs a fixed AID; receiver Android host card emulation answers and logs. HCE, unlock and routing restrictions apply; it is isolated from photo transfer.

## Verification
GitHub Actions runs the protocol unit tests and builds a debug APK. On the two real devices, test both sender roles, Gallery share, reject/accept paths, matching codes, interrupted transfer, saving, NFC experiment and battery/permission restrictions. Automated unit checks do not replace real-device tests.
