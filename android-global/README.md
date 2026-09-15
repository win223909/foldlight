# Foldlight Global · Android Fold

Public beta **0.2.4**. Package: `com.zksaga.foldlight.globaldemo`.
Installs alongside the original Photo app with separate settings and permissions.

[中文使用说明](../docs/android-fold/global-zh-CN.md) · [English guide](../docs/android-fold/global-en.md)

## Compatibility

The global browser transition has been physically tested on Samsung Fold8
(SM-F9710 / Android 17 / One UI 9). Fold7 and other models are untested for this
edition; this build does not enable their dual-display session. The separate
Photo app has been tested on Fold7 and Fold8. Firmware changes and other apps
may behave differently. This is a beta, not a universal system replacement.

## Architecture

Measured device: SM-F9710 only. Begin fully closed and unlocked in an ordinary
app. A session keeps the cover on logical display 0 and the inner on display 1
using the advertised CONCURRENT_OUTER_DEFAULT state. It moves the existing task
with startActivityFromRecents instead of changing primary displays mid-fold.
Both panels receive committed screenshot overlays before migration. A fresh
capture excludes our own surfaces, and handover waits for native window drawing
and stable geometry. Only the inner left half receives the folding effect.

This is a frozen-image transition, not live video. Screenshots stay in memory;
there is no Internet permission, screenshot file, UI-node retrieval or lock-screen
dismissal. Protected content and rotated displays are skipped. Accessibility
hosts the effect overlays; the separately authorized Shizuku helper captures the
screen and supplies continuous angles. No floating navigation controls are displayed. Home/Recents are not supported in this iteration.

Both screens remain powered during the session, which increases power use.
Stop, screen lock, app settings, helper death and lost heartbeat release the
owned request and restore tasks moved by this session. A failed visual handover
also releases the session; visual overlays have an 18-second timeout.

## Build and validation

Use JDK 17 and Android SDK 35:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The release build, Android lint and 19 logic tests pass. Physical browser folding
has been exercised during development; automated tests do not establish behavior
for every app, firmware or device. See the guides for current limitations and
restoration controls.

### Effect settings (0.2.4)

The app settings contain the same six effect controls as Foldlight Fold:

- Cover: opening dark-start (40–179°), closing light-start (41–180°).
- Inner left: opening light-start (0–179°), closing dark-start (1–180°).
- Texture: cover-right compression (0–100%), blur (0–100%).

Values save automatically in this app's global_effect_parameters preferences.
Restoring defaults affects only these six values; permissions and effect enablement
remain intact. Defaults preserve the global experiment's existing appearance:
80° / 90°, 55° / 70°, compression 60%, blur 55%. They do not import the original
app's personal settings. Rendering uses immutable snapshots, with no disk reads
inside frames. Incoming values are bounded and invalid persisted values fall back
to defaults. The inner right half remains clear and stationary.

## Attribution

Architecture and two adapted helpers: [bunkaich/Folduo](https://github.com/bunkaich/Folduo)
at `c9e5976cf1d5176652fcf0fc984bb5ec86751d29`, under MIT.
See [third-party notices](THIRD_PARTY_NOTICES.md) and
[license](licenses/Folduo-MIT.txt). A copy is included in the APK and About UI.
