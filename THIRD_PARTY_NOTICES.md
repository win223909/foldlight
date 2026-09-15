# Third-party notices and artwork

## Upstream

Foldlight builds on [DuoLikeAnimation](https://github.com/elijah-semyonov/DuoLikeAnimation), originally by Elijah Semyonov. Initial upstream source revision: `be92768`. The upstream MIT copyright and permission notice are retained in `LICENSE` and the iPhone app's `OpenSourceLicense.txt`.

## Global edition reference

The global edition references [bunkaich/Folduo](https://github.com/bunkaich/Folduo), MIT, for stable-primary display routing and covered task handover. Adapted capture and window-readiness helpers retain attribution. Copyright (c) 2026 bunkaich. The full license is in `android-global/licenses/Folduo-MIT.txt` and included in the APK. This is not an upstream endorsement or a claim that its device support carries over.

## Dependencies

- AndroidX (Core, Window, Test): Apache-2.0; resolved from Google's Maven repository.
- Shizuku API: Apache-2.0; [source](https://github.com/RikkaApps/Shizuku-API). Shizuku itself is installed separately by the user.
- Gradle wrapper: Apache-2.0; wrapper JAR is the only checked-in build-tool binary. The distribution checksum is pinned.
- Apple and Android SDKs/frameworks are provided by their respective vendors; they are not relicensed by this repository.

## Artwork and fixtures

`web/assets/demo-screen.svg` and `docs/images/preview.svg` are original Foldlight illustrations distributed under this repository's MIT license. Native demos generate their sample scenes in code. The app icon is generated for Foldlight; its source is `scripts/generate-icon.swift`.

No Swisscom/Apple device-guide screenshots or user-selected wallpapers are included in this source tree. The deployed demo or historical downloads may contain different sample artwork; their attribution is separate from the source license. Test motion fixtures contain synthetic relative angles only.
