# Releasing

Versions are per platform (`android/app/build.gradle`, `macos/Resources/Info.plist`, iOS Xcode build settings). Keep the changelog and website version labels consistent with the artifact being distributed.

1. Run public-content scanning and platform checks from a clean checkout.
2. Build artifacts using a signing identity/keystore you control. Never commit signing files or passwords.
3. Verify package identity, version, signature and SHA-256. Preserve signing continuity for in-place Android upgrades.
4. Create a tagged GitHub Release and attach binaries, checksums and platform instructions. Store binaries as release assets, not source blobs.
5. For Mac distribution, complete Developer ID signing and notarization if claiming notarized status; the current hosted beta is not notarized. iOS distribution requires your own Apple signing and provisioning.
6. Update hosted download URLs only after uploads succeed. Verify public hashes and retain the previous version for rollback.
7. Record separately: source checks, CI, installation verification and actual sensor-driven device acceptance.

The repository CI never deploys the live website or server configuration. Existing hosted downloads are linked from `web/index.html`; replace these URLs when publishing your own builds.
