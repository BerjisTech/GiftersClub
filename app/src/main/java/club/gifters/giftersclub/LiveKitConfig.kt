package club.gifters.giftersclub

/**
 * Configuration for LiveKit WebRTC SFU integration.
 */
object LiveKitConfig {
    /** WebSocket URL for your LiveKit server */
    const val WS_URL = "<YOUR_LIVEKIT_WS_URL>"

    /** Supabase Edge Function endpoint name for fetching access tokens */
    const val TOKEN_ENDPOINT = "live-session"
}