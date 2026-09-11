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
