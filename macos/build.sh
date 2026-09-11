#!/bin/bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR="$ROOT_DIR/build/MacApp/Foldlight.app"
SDK_PATH="$(xcrun --sdk macosx --show-sdk-path)"
mkdir -p "$APP_DIR/Contents/MacOS" "$APP_DIR/Contents/Resources"
for arch in arm64 x86_64; do
  xcrun swiftc -swift-version 5 -O -sdk "$SDK_PATH" -target "$arch-apple-macos13.0" \
    "$ROOT_DIR"/macos/Sources/*.swift -o "$ROOT_DIR/build/MacApp/Foldlight-$arch"
done
lipo -create "$ROOT_DIR/build/MacApp/Foldlight-arm64" "$ROOT_DIR/build/MacApp/Foldlight-x86_64" -output "$APP_DIR/Contents/MacOS/Foldlight"
cp "$ROOT_DIR/macos/Resources/Info.plist" "$APP_DIR/Contents/Info.plist"
cp "$ROOT_DIR/macos/Resources/Fold.metal" "$ROOT_DIR/LICENSE" "$APP_DIR/Contents/Resources/"
ICONSET_DIR="$ROOT_DIR/build/MacApp/AppIcon.iconset"
mkdir -p "$ICONSET_DIR"
for size in 16 32 128 256 512; do
  sips -z "$size" "$size" "$ROOT_DIR/DuoLikeAnimation/Assets.xcassets/AppIcon.appiconset/AppIcon.png" --out "$ICONSET_DIR/icon_${size}x${size}.png" >/dev/null
  twice=$((size * 2))
  sips -z "$twice" "$twice" "$ROOT_DIR/DuoLikeAnimation/Assets.xcassets/AppIcon.appiconset/AppIcon.png" --out "$ICONSET_DIR/icon_${size}x${size}@2x.png" >/dev/null
done
iconutil -c icns "$ICONSET_DIR" -o "$APP_DIR/Contents/Resources/AppIcon.icns"
codesign --force --options runtime --sign "${FOLDLIGHT_SIGN_IDENTITY:--}" "$APP_DIR"
codesign --verify --strict --verbose=2 "$APP_DIR"
echo "$APP_DIR"
