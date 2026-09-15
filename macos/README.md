# Foldlight for Mac

A menu bar app using ScreenCaptureKit, Metal and validated lid sensor reports. Requires macOS 13+ and Screen Recording permission for the live effect. Supports universal Apple Silicon / Intel builds; hardware lid input varies by model.

```sh
./macos/build.sh
./scripts/test-macos.sh
```

Output: `build/MacApp/Foldlight.app`. Default signing is ad-hoc for local development. Set `FOLDLIGHT_SIGN_IDENTITY` to your own identity when appropriate; this alone does not notarize a build.

For updates on a Mac that has already granted Screen Recording, build with the same signing identity as the installed app. Replacing a development-signed app with an ad-hoc build breaks its saved macOS permission identity even when System Settings still shows the permission switch enabled. Inspect the installed identity with `codesign -dv --verbose=2 /path/to/Foldlight.app`, then supply that identity through `FOLDLIGHT_SIGN_IDENTITY` when building.

Use the guarded installer for local updates (substitute your installed path). It verifies the original designated code requirement before replacing anything and retains a rollback copy. Add `--check-only` to validate without installing. It does not change macOS permissions or security settings.

```sh
python3 macos/install-local.py build/MacApp/Foldlight.app ~/Applications/Foldlight.app
```

Open the app, grant Screen Recording permission, restart if requested, then calibrate at your normal lid angle. Control+Option+Command+D toggles the effect. Close settings to keep running in the menu bar. Pause before precise interaction at large angles because clicks retain original desktop coordinates.

Tests use synthetic angle fixtures and generated GPU pixels, never live desktop captures. A working Metal GPU is needed to run them; CI only builds the universal app. Full lid closure follows macOS sleep policy.

## Fixed observer projection (0.2.5)

The original desktop is a stationary content plane. Each physical lid pixel rotates around the bottom hinge, and its ray from a fixed observer intersects that content plane to find the captured pixel to show. This is one optical projection; the captured image and overlay window are not rotated again. The assumed observer is centered on the calibrated screen, three screen heights away. There is no camera or head tracking, so physical agreement depends on viewing near that assumed position and calibrating at the usual lid angle.

Defocus increases continuously with the distance between the moving display and original content plane. Small kernels use a separable Gaussian directly; larger kernels use a Gaussian pyramid beginning at full capture resolution, interpolated by variance. Depth does not add a separate black fog. Outside the finite source content, the same blur radius feathers the boundary; a panel turned 90 degrees or farther from its reference shows black. The existing capture, frame pacing, sensor interpolation, and transparent-rest lifecycle remain in place.

For a separate review build without replacing another build or installing it:

```sh
FOLDLIGHT_BUILD_DIR="$PWD/build/MacApp/FixedPlane-0.2.5" ./macos/build.sh
```

The full-resolution blur pyramid uses approximately 16 bytes per captured pixel (about 65 MB at 2560 × 1600, excluding the captured/display surfaces). It is rebuilt only for new capture frames. GPU tests include fixed-observer invariance, smooth blur-level transitions and constant-light preservation; they do not replace physical lid acceptance.
