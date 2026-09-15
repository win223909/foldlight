# Changelog

## 2026-09-15 — Android Fold downloads

- Publish separate photo (0.3.8-fold7.1) and global (0.2.4 Beta) downloads with Chinese and English guides.
- Photo edition: Samsung Fold7 and Fold8 profiles; other models untested. Global edition: Fold8 only, snapshot transitions, independent panel rendering and six saved effect controls.
- Rename the website section to Android Fold and distinguish each edition’s tested scope.
- Sync the deployed website loading improvements and Mac fixed-view / signing-identity fixes. Add global Android builds to CI.


Platform versions are independent. Web changes follow source revisions.

## Fold8 0.3.7 / Web — 2026-09-12

- Recommend the measured 80° / 90° / 55° / 70° brightness starts, 60% cover compression and 100% blur.
- Restore only the six effect settings with one button, preserving images.
- Share the reference-glass shader with the web demo; add web compression and restore controls.

- Use a continuous depth-based focus gradient, with a smooth fine-detail/Gaussian blend.
- Fix ineffective dimming by removing the reference renderer's 45% brightness floor and duplicate easing; initialize cover brightness before its first frame.
- Reduce effect settings to four brightness angles, cover right-edge compression and shared blur.
- Keep the cover left edge anchored during compression; preserve both inner halves from this adjustment.
- Preserve user images and brightness angles; migrate cover blur to shared blur and retire hidden advanced overrides.
- Keep the original renderer in the local developer comparison; use reference glass in the app.
- Add GPU regression checks for fixed panes, 90-degree continuity, focus progression and cover-only compression, plus six-control/layout checks.

## Fold8 0.3.6 — 2026-09-11

- Group settings by cover / inner-left and opening / closing.
- Add sixteen independent deformation controls and four brightness start angles.
- Add separate 0–100% frost and gradient controls per screen.
- Preserve the stationary inner right half and existing user settings.

## Mac 0.2.4 — 2026-09-11

- Cached Gaussian glass blur, feathered edges and smooth haze-to-black.
- Transparent transitions at effect entry and return to rest.

## iPhone 1.0.3 — 2026-09-11

- Cached Gaussian glass textures, photo persistence and native Metal rendering.
- Manual and sensor-driven rotation up to ±90°.

## Web / repository — 2026-09-11

- Bilingual phone demo, fullscreen guidance, image import and platform downloads.
- Fully English installation guidance and language-aware external links.
- Independent source repository, original vector artwork, CI, contribution templates and portable build instructions.
