package club.gifters.giftersclub

object SupabaseConfig {
    const val SUPABASE_URL = "https://xffhtertooztyyotwhrv.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhmZmh0ZXJ0b296dHl5b3R3aHJ2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDc5MDI0ODAsImV4cCI6MjA2MzQ3ODQ4MH0.uaLukRBX5IVWbFM8aD6-025am4WpsT94crliJPHl1pk"
    const val REDIRECT_URI = "gifterclub://login-callback"
    const val POSTS_BUCKET = "posts"
    const val AVATARS_BUCKET = "avatars"
    /** Bucket for chat media attachments */
    const val CHAT_MEDIA_BUCKET = "chat-media"

    /** Approximate conversion rate from KES to USD */
    const val KES_USD_RATE = 0.0078

    /** Flutterwave public key for inline payments */
    const val FLUTTERWAVE_PUBLIC_KEY = "FLWPUBK-23f4ab7e7dfd648de9c957acd063b30d-X"
}