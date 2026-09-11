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
