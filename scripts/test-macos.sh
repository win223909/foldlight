#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/MacApp
xcrun swiftc -swift-version 5 macos/Sources/FoldMath.swift macos/Sources/LidReport.swift macos/Sources/GlassBlur.swift macos/Tests/main.swift -o build/MacApp/FoldlightTests
build/MacApp/FoldlightTests macos/Resources/Fold.metal
