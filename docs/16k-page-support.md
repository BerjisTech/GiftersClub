16 KB memory page-size support (Android 15+)

Summary
- Android 15+ devices can use 16 KB OS page sizes. Any native code (.so) in your app must be linked to support 16 KB pages. If any bundled .so assumes 4 KB-only, the app can crash or be blocked in Play updates.

What needs attention in this app
- The app itself has no local NDK sources, but it bundles native libraries from dependencies. A previous universal APK contained:
  - lib/arm64-v8a/libucrop.so (from uCrop)
  - lib/arm64-v8a/libyuv-decoder.so (likely from Coil Video)
  - lib/arm64-v8a/libimage_processing_util_jni.so (likely from Media3 Transformer/Effect)
- These must be 16 KB–compatible. That typically means upgrading to dependency versions released in 2024/2025 where vendors rebuilt with `-Wl,-z,max-page-size=16384` on arm64.

High‑level fix
- Upgrade any dependency that contributes .so files to a version that declares 16 KB support. Commonly affected here:
  - uCrop: update to a recent Maven Central release that’s 16 KB–ready (or switch to a non‑native cropper if you prefer to avoid .so entirely).
  - Coil and coil‑video: upgrade to the latest 2.x.
  - AndroidX Media3: stay current (1.8.0+ is already very recent, keep it updated as new patches land).
  - Any other library contributing .so files (check with the scripts below), including LiveKit/WebRTC if you enable features that pull in its native libs.

How to identify which AAR adds a given .so
1) Build a release: `./gradlew :app:assembleRelease`
2) Run: `bash scripts/resolve_so_origins.sh`
   - This prints AAR(s) in your Gradle cache that contain each merged .so (arm64-v8a).

How to check page-size compatibility
Option A — via Gradle tasks (added in this repo):
- `./gradlew :app:printNativePageSizes` prints each merged .so and its page-size metadata (requires llvm-readelf/readelf; install NDK via Android Studio > SDK Tools > NDK).
- `./gradlew :app:verify16kPageSupport` fails the build if it detects signs of 4 KB‑only binaries.

Option B — via script:
- `bash scripts/scan_so_pagesize.sh` (same output as the Gradle task).

What “good” looks like
- For arm64 .so files, readelf output should show a MaxPageSize or Page size of 16384 (or higher) for PT_LOAD segments. If you see 4096, that .so needs an update.

If you build native code locally (future‑proofing)
- Use NDK r26b+ and set link flags for 16 KB page size when producing .so:
  - CMake: `target_link_options(<target> PRIVATE "-Wl,-z,max-page-size=16384")`
  - ndk-build: `LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384`
  - Rust: `-C link-arg=-z -C link-arg=max-page-size=16384`

Testing on 16 KB devices
- Use an Android 15 emulator/system image that enables 16 KB pages (see Android Studio’s 16 KB page‑size guidance), or test on a physical device known to use 16 KB pages.
- Sanity checks: install the release APKs/App Bundle, run hot paths that touch native libs (video transforms, image cropping, decoding), and watch for loader errors.

Play Console validation
- After uploading a new App Bundle, the Play Console’s App Bundle Explorer indicates whether the bundle supports 16 KB page sizes. Ensure your production track uses a compliant build.

Typical upgrade steps (safe defaults)
1) Bump uCrop to a recent version from Maven Central that advertises 16 KB compatibility (or replace with a pure‑Kotlin cropper to remove .so entirely).
2) Update Coil and coil‑video to the latest 2.x.
3) Keep AndroidX Media3 on the latest stable.
4) Rebuild release, run `:app:verify16kPageSupport`, and re‑test.

Notes
- No config change is required for pure‑Java/Kotlin modules; only native code is sensitive to page sizes.
- Avoid repackaging or manually stripping third‑party .so files; prefer vendor updates so symbols and relro are correct.

