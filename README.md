# Foldlight · 折光

[![CI](https://github.com/win223909/foldlight/actions/workflows/ci.yml/badge.svg)](https://github.com/win223909/foldlight/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Perspective, frosted glass and light that follow how you move a screen.**

[中文说明](README.zh-CN.md) · [Live demo & downloads](https://duo.zksaga.com/) · [Build guide](docs/BUILDING.md) · [Architecture](docs/ARCHITECTURE.md)

![Original demo screen](docs/images/preview.svg)

Foldlight is a collection of native and web experiments inspired by [DuoLikeAnimation](https://github.com/elijah-semyonov/DuoLikeAnimation) by Elijah Semyonov. It uses cached Gaussian glass textures, perspective projection and continuous motion interpolation.

| Platform | What it does | Requirements / current version |
| --- | --- | --- |
| Web | Drag or tilt an image, upload your own, switch Chinese/English, enter immersive view | Modern WebGL 2 browser; HTTPS for phone motion |
| iPhone | Native photo demo driven by device orientation | iOS 26.5+; 1.0.3 |
| macOS | Menu bar app rendering a live desktop effect as the lid moves | macOS 13+; Apple Silicon / Intel; 0.2.6 |
| Android Fold · Photo | Separate pictures for the cover and inner screen | Tested on Samsung Fold7 and Fold8; 0.3.8-fold7.1 |
| Android Fold · Global | Snapshot-based folding transitions across compatible apps | Tested on Samsung Fold8 only; 0.2.4 Beta |

Other Android models and firmware are untested. The global build does not enable its dual-screen session on Fold7. The two Android apps install separately and should not run their dual-screen effects simultaneously.

The web, iPhone and Android photo edition render their own images. The global edition uses in-memory screenshots of compatible apps; content briefly freezes during transitions. It is not live video and does not support Home, Recents, landscape or protected pages. Android requires separately authorized Shizuku; the global edition also needs its Accessibility service. The Mac app needs Screen Recording permission.

[Android photo guide](docs/android-fold/fold-en.md) · [Android global guide](docs/android-fold/global-en.md) · [Download both editions](https://duo.zksaga.com/#android-fold)

## Quick start

```sh
git clone https://github.com/win223909/foldlight.git
cd foldlight
npm run dev
# Open http://127.0.0.1:4178
```

The web project has no npm runtime dependencies. Node.js 20+ is sufficient.

```sh
npm test
npm run build         # build/WebRelease/duo-static
npm run check:public  # scan the files selected for Git
```

Native builds and real-device checks are documented in [BUILDING.md](docs/BUILDING.md). Download links in the source website point to hosted, versioned packages; large installers are kept out of Git.

## Android Fold controls

Both editions have six controls: cover opening dark-start / closing light-start angles, inner-left opening light-start / closing dark-start angles, cover right-edge compression and blur. Settings save automatically and can be reset. The inner right half stays clear and stationary. The photo edition opens settings with a two-finger hold; the global edition uses its own settings page and has no floating buttons.

## Repository

- `web/`: dependency-free, bilingual browser demo.
- `DuoLikeAnimation/` and `.xcodeproj`: native iPhone app; the upstream target name is retained.
- `macos/`: AppKit / SwiftUI, ScreenCaptureKit and Metal desktop app.
- `android/`: Java / OpenGL ES app, hinge input and Shizuku service.
- `android-global/`: separate cross-app screenshot experiment, direct EGL rendering and covered task handover.
- `docs/`: architecture, build, compatibility and release guidance.
- `.github/`: CI, issue forms, pull-request template and an opt-in dependency-update template.

## Compatibility and verification

CI checks web tests and static builds, Android unit tests/lint/build, a Mac universal build and an iOS Simulator build. GPU and sensor checks require suitable physical hardware; passing CI does not prove real folding smoothness. See [COMPATIBILITY.md](docs/COMPATIBILITY.md).

The repository uses an original vector demo screen. Personal wallpapers, third-party device screenshots, signing credentials, deployment secrets, visitor records and device logs are excluded. Existing production releases may use different demo artwork.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md) and [CHANGELOG.md](CHANGELOG.md). Report the platform, version and exact reproduction steps; avoid attaching personal screenshots or raw device identifiers.

## License and credits

MIT. Copyright notices for Elijah Semyonov and ZK are retained in [LICENSE](LICENSE). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependencies and artwork. This project is not affiliated with Apple, Samsung or Shizuku.

Made by **ZK**.
