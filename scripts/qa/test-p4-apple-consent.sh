#!/usr/bin/env bash
# CI-only XCTest of the exact Apple P4 capture consent source.
# Never copy production code into the repository or claim full app/device QA.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT

source_dir="$scratch/Sources/KelpieBrowser"
test_dir="$scratch/Tests/KelpieBrowserTests"
mkdir -p "$source_dir" "$test_dir"

# Keep package construction explicit: fail if upstream moves either actual file.
cp "$repo_root/core/apple/shared/Browser/ConsentedCaptureLease.swift" "$source_dir/"
cp "$repo_root/core/apple/shared/Browser/ConsentedCaptureRequestRegistry.swift" "$source_dir/"
cp "$repo_root/apps/macos/Tests/ConsentedCaptureLeaseTests.swift" "$test_dir/"
cp "$repo_root/apps/macos/Tests/ConsentedCaptureRequestRegistryTests.swift" "$test_dir/"

cat > "$scratch/Package.swift" <<'SWIFT'
// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "KelpieP4ConsentVerification",
    platforms: [.macOS(.v13)],
    products: [
        .library(name: "KelpieBrowser", targets: ["KelpieBrowser"]),
    ],
    targets: [
        .target(name: "KelpieBrowser"),
        .testTarget(name: "KelpieBrowserTests", dependencies: ["KelpieBrowser"]),
    ]
)
SWIFT

echo "P4 Apple consent regression: compiling the repository's unmodified Swift files"
echo "No CEF, WebView, Apple user UI, device, signing or public release involved."
swift --version

# pipefail preserves the test runner failure rather than tee's exit code.
swift test --package-path "$scratch" 2>&1 | tee "$scratch/swift-test.log"

# SwiftPM can exit successfully when no XCTest tests are discovered.
# Require all nine currently specified XCTest cases to have executed.
if ! grep -Eq 'Executed 9 tests?, with 0 failures' "$scratch/swift-test.log"; then
    echo "ERROR: did not verify the complete 9-case XCTest suite." >&2
    exit 1
fi
echo "PASS: 9 Apple P4 consent XCTest cases executed with zero failures."
