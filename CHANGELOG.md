# Changelog

Platform versions are independent. Web changes follow source revisions.

## Fold8 0.3.7-preview.2 — unreleased experiment

- Add a persistent reference-glass A/B switch, enabled for the preview.
- Separate the physical glass projection from the picture plane; avoid a second shrinking shutter.
- Move the cover focus boundary from right to left and refocus the inner left pane on opening.
- Broaden the focus transition and blend fine detail continuously into Gaussian blur, removing the abrupt one-sigma cutoff.
- Preserve image colors through dimming, stationary inner-right pixels, user images and settings.
- Add a local four-pane shader comparison and three device GPU regression checks.
- Device installation and physical hinge validation remain pending.

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
