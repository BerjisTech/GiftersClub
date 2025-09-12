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
        versionCode = 36
        versionName = "1.0.36"

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
