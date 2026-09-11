# Foldlight for Fold8

JDK 17, Android SDK 35 and Build Tools 35.0.0. Open this directory in Android Studio or use the committed Gradle wrapper. `local.properties` is local-only; alternatively set `ANDROID_HOME`.

```sh
./android/gradlew -p android testDebugUnitTest lintDebug assembleDebug
python3 -m unittest discover -s android/tools -p 'test_*.py'
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`. This is a development-signed build. Signing with a different key cannot update an already installed package with the same application ID.

Install Shizuku separately, pair/start it through its official instructions, then authorize Foldlight's local input button. Select hinge tracking and enter fullscreen. Hold two fingers for about 0.65 seconds to open settings. Restart Shizuku after a phone reboot. No computer/USB is needed once the local service is running.

Only SM-F9710 / Android 17 / One UI 9 has been physically tested for continuous angles and concurrent displays. Android's minimum SDK is not a compatibility promise. The inner right half remains unchanged; each screen owns its image and each direction has independent deformation/brightness controls.

Instrumentation tests require an unlocked, compatible device and may temporarily change settings/display state. Use a dedicated test device, not an unattended personal device:

```sh
./android/gradlew -p android connectedDebugAndroidTest
```

`tools/hinge_usb_bridge.py` is a developer-only USB diagnostic fallback; the app normally uses its authorized on-device Shizuku service. Hardware behavior and frame smoothness require physical verification. See [BUILDING.md](../docs/BUILDING.md) and [COMPATIBILITY.md](../docs/COMPATIBILITY.md).

## Reference glass experiment (0.3.7-preview.2)

Settings → **参考玻璃 · 实验效果** switches between the new projection and the existing effect. The preview enables this option by default; its choice is persisted. Both modes share your images and settings. In reference mode the deformation angle controls virtual depth, while the four brightness envelopes retain their timing but preserve at least 45% color transmission. The inner right half bypasses all effect shading. This is an independently implemented visual study inspired by https://bonxn.github.io/dood-iphone-duo/; no model, imagery or video from that site is bundled.

To compare the actual Android fragment shader in a desktop browser:

```sh
node android/tools/build-reference-preview.mjs
python3 -m http.server 4198 --bind 127.0.0.1 --directory build/reference-preview
```

Open http://127.0.0.1:4198 and drag the angle or play a cycle. Left: existing effect. Right: reference glass. The preview uses simplified opening curves and generated demo artwork; it does not simulate hinge sampling, direction-dependent brightness envelopes, Android display switching or physical GPU performance. It is a local development page, not part of the public web build.

Validation so far: 38 JVM tests and Android lint/build pass. Desktop WebGL compilation and pixel checks pass: fixed right pane unchanged, no discontinuity around 90 degrees, and stable paused output. Three additional Android GPU tests compile but await a connected device. Real folding smoothness is not yet verified for this experiment.
