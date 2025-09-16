import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}
android.buildFeatures.buildConfig = true
android {
    namespace = "club.gifters.giftersclub"
    compileSdk = 35
    // Ensure a 16K-page-aware toolchain if/when compiling native code
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "club.gifters.giftersclub"
        minSdk = 24
        targetSdk = 35
        versionCode = 37
        versionName = "1.0.37"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Rollbar configuration exposed to BuildConfig
        val props = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        val rollbarClientToken: String = (
                props.getProperty("ROLLBAR_CLIENT_TOKEN")
                    ?: (project.findProperty("ROLLBAR_CLIENT_TOKEN") as String?)
                    ?: System.getenv("ROLLBAR_CLIENT_TOKEN")
                    ?: ""
                )
        val rollbarEnv: String = (
                props.getProperty("ROLLBAR_ENV")
                    ?: (project.findProperty("ROLLBAR_ENV") as String?)
                    ?: System.getenv("ROLLBAR_ENV")
                    ?: "production"
                )
        buildConfigField("String", "ROLLBAR_CLIENT_TOKEN", "\"$rollbarClientToken\"")
        buildConfigField("String", "ROLLBAR_ENV", "\"$rollbarEnv\"")
        // Expose token to manifest as a placeholder so you can use docs' manifest-based init
        manifestPlaceholders["ROLLBAR_ACCESS_TOKEN"] = rollbarClientToken

        // ABI selection moved to productFlavors below to allow x86_64 for dev/debug
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        // AGP 8.x requires JDK 17; align toolchain + bytecode level
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        // Keep Kotlin JVM target aligned with Java toolchain
        jvmTarget = "17"
    }

    // Optional: helper tasks to inspect native .so page sizes after a build
    // Usage:
    //   ./gradlew :app:assembleRelease
    //   ./gradlew :app:printNativePageSizes
    //   ./gradlew :app:verify16kPageSupport
    // The scripts rely on readelf/llvm-readelf (available from the Android NDK or PATH)
    tasks.register("printNativePageSizes") {
        group = "verification"
        description =
            "Prints page size info for merged native libs using scripts/scan_so_pagesize.sh"
        doLast {
            val script = rootProject.file("scripts/scan_so_pagesize.sh")
            if (!script.exists()) {
                println("scripts/scan_so_pagesize.sh not found")
            } else {
                val pb = ProcessBuilder("bash", script.absolutePath)
                    .directory(rootProject.projectDir)
                    .inheritIO()
                val proc = pb.start()
                val exit = proc.waitFor()
                if (exit != 0) error("scan_so_pagesize.sh exited with $exit")
            }
        }
        // Ensure we have something to scan
        dependsOn("assembleRelease")
    }

    tasks.register("verify16kPageSupport") {
        group = "verification"
        description = "Fails if any merged .so lacks 16 KB page-size support (best-effort check)."
        doLast {
            val out =
                file("$projectDir/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib")
            if (!out.exists()) error("Merged native libs not found. Run :app:assembleRelease first.")
            // Try to run the scan script and then grep for suspicious page size values
            val script = rootProject.file("scripts/scan_so_pagesize.sh")
            if (script.exists()) {
                val scan = ProcessBuilder("bash", script.absolutePath)
                    .directory(rootProject.projectDir)
                    .redirectErrorStream(true)
                    .start()
                val output = scan.inputStream.bufferedReader().use { it.readText() }
                val exit = scan.waitFor()
                println(output)
                if (exit != 0) error("scan_so_pagesize.sh exited with $exit")
                // Heuristic: flag if any line mentions 4096 as a MaxPageSize/Page size value
                val has4k = Regex("(?i)(MaxPageSize|Page size).*(4096|4k)").containsMatchIn(output)
                if (has4k) error("Detected native libraries built for 4 KB pages. Update dependencies to 16 KB-compatible builds.")
            } else {
                println("scan_so_pagesize.sh not found; performing naive check for .so presence under $out")
                val anySo = out.walk().any { it.isFile && it.extension == "so" }
                if (anySo) {
                    println("Found native libraries but could not verify page size. Install NDK (for llvm-readelf) and rerun.")
                } else {
                    println("No native libraries found in merged output.")
                }
            }
        }
        dependsOn("assembleRelease")
    }
}

// Flavors removed: use a single applicationId for all builds

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.coil)
    implementation(libs.coil.video)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.viewpager2)
    // ConcatAdapter for merging header and conversation adapters (requires RecyclerView 1.2+)
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    // Pull-to-refresh support for post feed
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    // GPUImage for real-time image filters
    // GPUImage for real-time image filters in the post editor
    implementation("jp.co.cyberagent.android:gpuimage:2.1.0")
    // Color picker dialog for custom color selection
    // Full‑range color‑picker for text/background: AmbilWarna via JitPack
    implementation("com.github.yukuku:ambilwarna:2.0.1")
    // Image cropping UI via uCrop (JitPack)
    // Image cropping UI via uCrop (Maven Central, non-native artifact)
    implementation("com.yalantis:ucrop:2.2.0")
    // Circular zoom control (rotary seekbar) via Maven Central
    implementation("com.akaita.android:circular-seek-bar:1.0")
    // CameraX for live camera preview and capture
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // LiveKit Android SDK for WebRTC SFU streaming (pinned)
    implementation("io.livekit:livekit-android:2.20.1")
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    // Google Play Billing library (adds BILLING permission via manifest)
    implementation(libs.billing.ktx)
    // Rollbar Android SDK for crash/error reporting
    implementation("com.rollbar:rollbar-android:1.10.3")
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Firebase Cloud Messaging for push notifications
    // Import the Firebase BoM for consistent Firebase library versions
    implementation(platform(libs.firebase.bom))


    // TODO: Add the dependencies for Firebase products you want to use
    // When using the BoM, don't specify versions in Firebase dependencies
    implementation(libs.firebase.analytics)


    // Add the dependencies for any other desired Firebase products
    // https://firebase.google.com/docs/android/setup#available-libraries
    implementation(libs.firebase.messaging.ktx)
    // Lottie for like/unlike animations overlay
    implementation("com.airbnb.android:lottie:5.2.0")

    // Android FlexboxLayout for responsive wrapping rows (used in fragment_purchase_tokens_bottom_sheet.xml)
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    // WorkManager for resilient background uploads
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // Media3 stack via version catalog (all on the same version)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    // E2EE: BouncyCastle for X25519/HKDF/ChaCha20-Poly1305
    implementation("org.bouncycastle:bcprov-jdk15to18:1.76")
    // EncryptedSharedPreferences for secure key storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
