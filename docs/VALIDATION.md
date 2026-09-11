# Source preparation checks — 2026-09-11

The following checks ran from the independent Foldlight source directory, without using production configuration or installing over the user's apps:

- Web: 10 tests, static build, original SVG example loaded in WebGL 2 at phone and desktop widths; no overflow or GL error.
- Android: 38 JVM tests, lint and debug APK build; 4 Python bridge tests. New source was not used for a separate physical-device rollout.
- macOS: universal Apple Silicon / Intel build with ad-hoc signing; generated-pixel Metal checks and synthetic 10 Hz angle replay passed.
- iOS: shared Foldlight scheme built for a generic iPhone Simulator with signing disabled.
- Analytics: 5 tests covering normalization, collection and authenticated access; all fixtures use example identities.
- Public-content scan passed. Gradle wrapper JAR matches upstream Gradle v8.11.1; distribution checksum is pinned.

The initial Mac synthetic fixture moved until the end of the replay and failed the stationary-tail assertion. It was corrected to include a stationary tail; the assertion and algorithm remain unchanged.

This validates the extracted buildable source. It does not replace physical hinge, screen-recording-permission, installation or real-motion acceptance testing. GitHub CI results are tracked on the Actions page.

## Fold8 0.3.7 and web glass — 2026-09-12

- Android JVM checks, lint, APK and instrumentation APK builds passed.
- Installed 0.3.7 / build 33 in place on the connected SM-F9710. The six persisted settings remained 80 / 90 / 55 / 70 / 0.6 / 1.0.
- Eleven selected on-device tests passed: six-control layout, restore button slider synchronization, default migration/reset persistence, and five GPU checks for stationary panes, continuity, focus, compression and full brightness range. These synthetic checks do not substitute for a new manual hinge-motion acceptance pass.
- Web: 12 unit tests, static build, WebGL rendering and reset behavior checked at desktop and 390-pixel phone width. The new controls fit without horizontal overflow; English controls are translated.
- The public release retains the existing production iPhone sample and attribution. The source tree continues to use original vector artwork. Production HTML and JavaScript were fetched through HTTPS and matched the release files.
- Website downloads carry the same development-signed APK installed on the phone. macOS and iOS binaries were not changed in this release.
