#!/usr/bin/env bash
set -euo pipefail

# Resolves which AAR(s) contributed specific .so files into the app merge output.
# Usage:
#   1) Build a release first: ./gradlew :app:assembleRelease
#   2) Run: bash scripts/resolve_so_origins.sh

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT_DIR/app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib"

if [[ ! -d "$OUT_DIR" ]]; then
  echo "Merged native libs not found at $OUT_DIR"
  echo "Build a release first: ./gradlew :app:assembleRelease"
  exit 1
fi

declare -a SO_NAMES

echo "Scanning merged native libs (arm64-v8a):"
for so in "$OUT_DIR"/arm64-v8a/*.so; do
  [ -e "$so" ] || continue
  so_name=$(basename "$so")
  echo "  - $so_name"
  SO_NAMES+=("$so_name")
done

GRADLE_CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"
if [[ ! -d "$GRADLE_CACHE" ]]; then
  echo "Gradle cache not found at $GRADLE_CACHE; set GRADLE_USER_HOME if needed."
  exit 0
fi

echo
echo "Searching Gradle cache for origins (this may take a moment)…"
shopt -s globstar nullglob

for name in "${SO_NAMES[@]}"; do
  echo "\n=== Origins for $name ==="
  # Find any AAR that contains the .so in arm64-v8a
  count=0
  while IFS= read -r -d '' aar; do
    if unzip -l "$aar" | grep -q "lib/arm64-v8a/$name"; then
      echo "  -> $aar"
      count=$((count+1))
    fi
  done < <(find "$GRADLE_CACHE" -type f -name "*.aar" -print0)
  if [[ $count -eq 0 ]]; then
    echo "  (no AAR in Gradle cache contains $name — it might be produced by a local module or repackaged)"
  fi
done

echo "\nDone."

