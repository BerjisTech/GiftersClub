package club.gifters.giftersclub

/**
 * Configuration for LiveKit WebRTC SFU integration.
 */
object LiveKitConfig {
    /** WebSocket URL for your LiveKit server */
    const val WS_URL = "wss://giftersclub-1ej914uy.livekit.cloud"

    /** Supabase Edge Function endpoint name for fetching access tokens */
    const val TOKEN_ENDPOINT = "live-session"
}