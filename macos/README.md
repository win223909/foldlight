# Foldlight for Mac

A menu bar app using ScreenCaptureKit, Metal and validated lid sensor reports. Requires macOS 13+ and Screen Recording permission for the live effect. Supports universal Apple Silicon / Intel builds; hardware lid input varies by model.

```sh
./macos/build.sh
./scripts/test-macos.sh
```

Output: `build/MacApp/Foldlight.app`. Default signing is ad-hoc for local development. Set `FOLDLIGHT_SIGN_IDENTITY` to your own identity when appropriate; this alone does not notarize a build.

Open the app, grant Screen Recording permission, restart if requested, then calibrate at your normal lid angle. Control+Option+Command+D toggles the effect. Close settings to keep running in the menu bar. Pause before precise interaction at large angles because clicks retain original desktop coordinates.

Tests use synthetic angle fixtures and generated GPU pixels, never live desktop captures. A working Metal GPU is needed to run them; CI only builds the universal app. Full lid closure follows macOS sleep policy.
