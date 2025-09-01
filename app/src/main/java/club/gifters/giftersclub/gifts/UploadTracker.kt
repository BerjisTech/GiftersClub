package club.gifters.giftersclub.gifts

import android.content.Context

object UploadTracker {
    private const val PREF = "uploads"

    fun addPending(ctx: Context, userId: String?, postId: String) {
        if (userId.isNullOrBlank()) return
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val key = "pending_$userId"
        val set = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(postId)
        prefs.edit().putStringSet(key, set).apply()
    }

    fun removePending(ctx: Context, userId: String?, postId: String) {
        if (userId.isNullOrBlank()) return
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val key = "pending_$userId"
        val set = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (set.remove(postId)) prefs.edit().putStringSet(key, set).apply()
    }

    fun hasPending(ctx: Context, userId: String?): Boolean {
        if (userId.isNullOrBlank()) return false
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val key = "pending_$userId"
        return !prefs.getStringSet(key, emptySet()).isNullOrEmpty()
    }

    fun setPendingVideo(ctx: Context, userId: String?, isVideo: Boolean) {
        if (userId.isNullOrBlank()) return
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pending_video_$userId", isVideo).apply()
    }

    fun isPendingVideo(ctx: Context, userId: String?): Boolean {
        if (userId.isNullOrBlank()) return false
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return prefs.getBoolean("pending_video_$userId", false)
    }

    fun clearAll(ctx: Context, userId: String?, postId: String) {
        removePending(ctx, userId, postId)
        if (!hasPending(ctx, userId)) {
            // clear video flag when no more pending
            if (!userId.isNullOrBlank()) {
                ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit().remove("pending_video_$userId").apply()
            }
        }
    }
}
