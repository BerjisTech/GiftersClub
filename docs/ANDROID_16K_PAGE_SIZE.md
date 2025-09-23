Android 16 KB Page Size Support

Summary
- Google Play requires all updates targeting Android 15+ (API 35) to support 16 KB memory page sizes by Nov 1, 2025.
- If your app includes any native libraries (.so), every packaged ABI must be built in a way that works on 16 KB page size devices.

What we changed
- Limited packaged ABIs to 64-bit only in `app/build.gradle.kts`:
  - `arm64-v8a`, `x86_64` via `ndk { abiFilters }`.
  - This drops legacy 32-bit ABIs (armeabi, armeabi-v7a, x86) that are more likely to be non-compliant.
- Enabled modern JNI packaging (`packaging.jniLibs.useLegacyPackaging = false`).
- Added excludes for 32-bit ABI directories in packaging to prevent accidental inclusion.
- Added Gradle helper tasks already present in the project to inspect/verify native page sizes:
  - `:app:printNativePageSizes` (runs `scripts/scan_so_pagesize.sh`).
  - `:app:verify16kPageSupport` (best-effort failure if 4 KB-only libs are detected).
- Wired verification to run automatically after `assembleRelease`/`bundleRelease`.

How to verify locally
1) Build a release so merged native libs are produced:
   - `./gradlew :app:assembleRelease`
2) Print page size info for merged .so files:
   - `./gradlew :app:printNativePageSizes`
   - Requires `llvm-readelf` from the Android NDK (set `ANDROID_NDK_HOME` or install NDK in Android Studio > SDK Manager > SDK Tools).
3) Optional: run the heuristic verifier (fails on likely 4 KB outputs):
   - `./gradlew :app:verify16kPageSupport`
4) To identify which dependency contributes a particular .so, run:
   - `bash scripts/resolve_so_origins.sh`

Likely offenders to check first
- Image cropping and media libraries frequently bundle native code. In the current app build, these .so files were present in a prior universal APK:
  - `lib*/libucrop.so` (from uCrop)
  - `lib*/libimage_processing_util_jni.so`
  - `lib*/libyuv-decoder.so`
- Action items:
  - Toggle candidate versions that are expected to be 16 KB–compliant by building with `-Puse16kCandidates=true`. This switches `uCrop` and `livekit-android` to newer versions for testing.
  - If Play still flags non-compliance, update to the newest available versions or replace with non-native alternatives where feasible, then re-check with the scripts above.
  - Keep only 64-bit ABIs unless you have a hard requirement for 32-bit devices.

General guidance for 16 KB support
- Update to the latest stable versions of:
  - Android Gradle Plugin (AGP) and Gradle (already modern in this project).
  - Third‑party SDKs with native code (WebRTC/LiveKit, media stacks, image/codec libs, etc.).
- If you build your own native code, ensure your NDK/toolchain is current and linkers are configured appropriately (recent NDKs/toolchains handle 16 KB automatically for Android).

Release checklist
- [ ] Assemble a release and run `printNativePageSizes`.
- [ ] If any 4 KB page size hints appear, run `resolve_so_origins.sh` and bump/replace the offending dependency.
- [ ] Confirm only `arm64-v8a` and `x86_64` are packaged (unless you intentionally support 32-bit).
- [ ] Upload the new build to Play Console and confirm the 16 KB check passes for the new artifact.
