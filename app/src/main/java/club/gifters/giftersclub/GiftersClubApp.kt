package club.gifters.giftersclub

import android.app.Application
import com.rollbar.android.Rollbar

class GiftersClubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Manifest-based initialization as per Rollbar docs
        // Requires <meta-data android:name="com.rollbar.android.ACCESS_TOKEN" android:value="${ROLLBAR_ACCESS_TOKEN}" /> in AndroidManifest
        try {
            Rollbar.init(this)
        } catch (_: Throwable) {
            // No-op: if manifest is missing or malformed, Rollbar will not initialize
        }
    }
}
