#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"

echo "Scanning native libs for page size info..."

# Try to locate llvm-readelf from PATH or Android NDK (Windows/macOS/Linux)
exe=""; case "${OS:-}" in MINGW*|CYGWIN*|Windows_NT) exe=".exe";; esac

pick_readelf() {
  if command -v llvm-readelf >/dev/null 2>&1; then echo "$(command -v llvm-readelf)"; return; fi
  if command -v readelf >/dev/null 2>&1; then echo "$(command -v readelf)"; return; fi
  # Prefer NDK env vars
  for var in ANDROID_NDK_HOME ANDROID_NDK_ROOT; do
    ndk="${!var-}"
    if [[ -n "${ndk}" ]]; then
      for host in windows-x86_64 linux-x86_64 darwin-x86_64 darwin-arm64; do
        cand="$ndk/toolchains/llvm/prebuilt/$host/bin/llvm-readelf$exe"
        [[ -x "$cand" ]] && { echo "$cand"; return; }
      done
    fi
  done
  # Fall back to SDK root and declared ndkVersion in build.gradle.kts (26.3.11579264)
  ndk_version="26.3.11579264"
  for var in ANDROID_SDK_ROOT ANDROID_HOME; do
    sdk="${!var-}"
    if [[ -n "${sdk}" ]]; then
      for host in windows-x86_64 linux-x86_64 darwin-x86_64 darwin-arm64; do
        cand="$sdk/ndk/$ndk_version/toolchains/llvm/prebuilt/$host/bin/llvm-readelf$exe"
        [[ -x "$cand" ]] && { echo "$cand"; return; }
      done
      # pick latest NDK if versioned one not found
      latest_ndk_dir=$(ls -1 "$sdk/ndk" 2>/dev/null | sort -V | tail -n1 || true)
      if [[ -n "$latest_ndk_dir" ]]; then
        for host in windows-x86_64 linux-x86_64 darwin-x86_64 darwin-arm64; do
          cand="$sdk/ndk/$latest_ndk_dir/toolchains/llvm/prebuilt/$host/bin/llvm-readelf$exe"
          [[ -x "$cand" ]] && { echo "$cand"; return; }
        done
      fi
    fi
  done
  echo "" # not found
}

READELF_BIN="$(pick_readelf)"
if [[ -z "$READELF_BIN" ]]; then
  echo "Could not find readelf/llvm-readelf."
  echo "Install Android NDK r26+ via Android Studio (SDK Manager > SDK Tools > NDK),"
  echo "or add llvm-readelf to PATH, or set ANDROID_NDK_HOME/ANDROID_SDK_ROOT."
fi

shopt -s nullglob
found=0
for so in \
  "$root_dir"/app/build/intermediates/merged_native_libs/*/merge*NativeLibs/out/lib/*/*.so \
  "$root_dir"/app/build/intermediates/stripped_native_libs/*/strip*DebugSymbols/out/lib/*/*.so; do
  found=1
  echo "==== $so"
  if [[ -n "$READELF_BIN" ]]; then
    "$READELF_BIN" -l "$so" | grep -iE "MaxPageSize|Page size" || echo "  (No page size field found)"
  else
    echo "  (readelf/llvm-readelf not available on this system)"
  fi
done

if [[ "$found" -eq 0 ]]; then
  echo "No .so files found in intermediates. Build a release first: ./gradlew clean :app:assembleRelease"
fi

echo "Done."
