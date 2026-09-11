# Foldlight for Fold8

JDK 17, Android SDK 35 and Build Tools 35.0.0. Open this directory in Android Studio or use the committed Gradle wrapper. `local.properties` is local-only; alternatively set `ANDROID_HOME`.

```sh
./android/gradlew -p android testDebugUnitTest lintDebug assembleDebug
python3 -m unittest discover -s android/tools -p 'test_*.py'
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`. This is a development-signed build. Signing with a different key cannot update an already installed package with the same application ID.

Install Shizuku separately, pair/start it through its official instructions, then authorize Foldlight's local input button. Select hinge tracking and enter fullscreen. Hold two fingers for about 0.65 seconds to open settings. Restart Shizuku after a phone reboot. No computer/USB is needed once the local service is running.

Only SM-F9710 / Android 17 / One UI 9 has been physically tested for continuous angles and concurrent displays. Android's minimum SDK is not a compatibility promise. The inner right half remains unchanged; each screen owns its image. Four directional brightness angles, cover right-edge compression and shared blur form the six effect controls.

Instrumentation tests require an unlocked, compatible device and may temporarily change settings/display state. Use a dedicated test device, not an unattended personal device:

```sh
./android/gradlew -p android connectedDebugAndroidTest
```

`tools/hinge_usb_bridge.py` is a developer-only USB diagnostic fallback; the app normally uses its authorized on-device Shizuku service. Hardware behavior and frame smoothness require physical verification. See [BUILDING.md](../docs/BUILDING.md) and [COMPATIBILITY.md](../docs/COMPATIBILITY.md).

## Reference glass experiment (0.3.7-preview.3)

The app now uses the reference-glass rendering with exactly six effect controls:

- Cover: opening dim-start angle; closing brighten-start angle.
- Inner left: opening brighten-start angle; closing dim-start angle.
- Cover right-edge compression (0–100%): contracts the right side progressively while anchoring the left; neither inner half is affected.
- Shared blur (0–100%): applies to the cover and inner left only.

The four saved brightness angles and images are preserved. The former cover blur becomes the shared blur on first migration. Retired speed, acceleration, amplitude, gradient, rendering-mode and softness controls use built-in defaults instead of leaving hidden user overrides active. Motion follows the hinge automatically. Double-finger hold still opens settings; picture selection and local Shizuku setup remain available.

A continuous depth field replaces the moving sharp/blur threshold. Brightness envelopes retain their timing and at least 45% color transmission; the inner right half bypasses all effect shading. This is an independently implemented visual study inspired by https://bonxn.github.io/dood-iphone-duo/; no model, imagery or video from that site is bundled.

To compare the actual Android fragment shader in a desktop browser:

```sh
node android/tools/build-reference-preview.mjs
python3 -m http.server 4198 --bind 127.0.0.1 --directory build/reference-preview
```

Open http://127.0.0.1:4198 and drag the angle or play a cycle. Left: original renderer retained for development comparison. Right: current glass effect. The preview uses simplified opening curves and generated artwork; it does not simulate hinge sampling, direction-dependent brightness envelopes, Android display switching or physical GPU performance. It is not part of the public web build.

Preview.3 validation: 38 JVM tests, lint and APK builds pass; all six selected GPU/settings instrumentation checks pass on SM-F9710. Installed in place as code 31. Perceived folding smoothness still requires user feedback.
