# Hyper Island design and implementation paths

## Design direction
Use the prior interactive concept as a direction, not a promise of pixel-exact OS rendering. Incoming: concise anonymous request with accept/decline. Connecting: compact device mark and a short state. Transferring: MIdrop mark, compact percentage/progress; expanded notification includes file count and cancel. Completion: concise check or completed text and dismissal. No identity claim without verified identity; no file names in the small public state. On failure use a normal notification with a useful error.

## Platform facts
- Android 16: use a genuine `Notification.ProgressStyle`, ongoing notification, short critical text and request promotion. The OS/user settings choose if and where it displays and control shape/layout. Existing devices keep a standard notification fallback. This app is not using a fake media session.
- Xiaomi publishes Super Island developer guidance and compact/expanded template specifications. The official compact design specification identifies template 5 (image/text + progress/text) for upload-like operations; colors and components must follow Xiaomi's template. It does not establish that a circular outline around the entire island is supported. Incoming request actions and progress each need confirmation against the approved expanded and compact templates.
- Xiaomi's onboarding documentation describes developer/application registration, app ID, certificate fingerprints, scene/design review and whitelist testing before formal access. The guide also states China and overseas platforms are separate and overseas applications do not currently use the China platform capability. Availability for the user's region must be verified with Xiaomi. Do not claim vendor permission until granted, or add placeholder credentials to production.
- Current GitHub builds use ephemeral debug signing. A stable privately stored signing key and a secure CI setup are needed for a persistent test/release fingerprint; never commit the key or credentials to the repo.

## Sources
- Xiaomi guide: https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131
- Xiaomi compact design: https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2143
- Xiaomi onboarding/requirements: https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2132
- Android progress notifications: https://developer.android.com/about/versions/16/features/progress-centric-notifications

## Verification
On a real Android 16 Xiaomi, test notification permission and system Live Updates settings, incoming/connecting/progress/complete/error and multi-file behavior. Promotion or a specific island presentation is not guaranteed. Transfer reliability and notification presentation must be tested independently.
