# Compatibility and evidence

| Component | Build minimum | Physical validation recorded before repository extraction |
| --- | --- | --- |
| Web | Node 20+ for tools; WebGL 2 browser for effect | Mobile/desktop layouts, Chinese/English, image import and immersive view |
| iPhone | Xcode 26.5+, iOS 26.5+ | iPhone 17 Pro Max / iOS 27; native photo and GPU tests |
| Mac | Xcode with macOS SDK, target macOS 13+ | Mac14,6 / macOS 27; lid input and live capture |
| Android Photo | JDK 17; SDK 35; minSdk 30 | Samsung Fold7 SM-F9660 / Android 16 / One UI 8.5 and Fold8 SM-F9710 / Android 17 / One UI 9 |
| Android Global | JDK 17; SDK 35; minSdk 30 | Samsung Fold8 SM-F9710; browser snapshot transitions. Fold7 and other models untested and disabled in this build |

The Android build minimum does not imply that continuous hinge access works on every Android 11 device. Samsung private interfaces can change with firmware. Without compatible input, only manual/demo modes may work.

Requested 120 Hz input or rendering is not an FPS guarantee. Sparse samples, display switching, capture cadence and GPU cost all affect perceived smoothness. CI has no physical hinge, does not grant screen capture permissions and does not certify smooth folding.

Use the issue template to report model, OS/firmware, package version, screen, opening/closing direction and reproduction steps. Redact serial numbers and personal content.
