package club.gifters.giftersclub.gifts

import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper object to handle follow/unfollow and follow-status actions.
 */
object FollowApiHolder {
    private val api = RetrofitClient.followsApi

    /**
     * Follows the given user.
     */
    suspend fun followUser(followedId: String): Boolean = withContext(Dispatchers.IO) {
        val current = AuthUtils.getCurrentUserId(RetrofitClient.context) ?: return@withContext false
        val resp = api.followUser(mapOf(
            "followed_id" to followedId,
            "follower_id" to current
        ))
        resp.isSuccessful
    }

    /**
     * Unfollows the given user.
     */
    suspend fun unfollowUser(followedId: String): Boolean = withContext(Dispatchers.IO) {
        val current = AuthUtils.getCurrentUserId(RetrofitClient.context) ?: return@withContext false
        val resp = api.unfollowUser(
            followedIdFilter = "eq.$followedId",
            followerIdFilter = "eq.$current"
        )
        resp.isSuccessful
    }

    /**
     * Returns true if the current user follows the given user.
     */
    suspend fun isFollowingUser(profileId: String): Boolean = withContext(Dispatchers.IO) {
        val current = AuthUtils.getCurrentUserId(RetrofitClient.context) ?: return@withContext false
        val resp = api.isFollowingUser(
            followedIdFilter = "eq.$profileId",
            followerIdFilter = "eq.$current"
        )
        val header = resp.headers()["Content-Range"] ?: return@withContext false
        (header.substringAfterLast('/').toIntOrNull() ?: 0) > 0
    }

    /**
     * Returns true if the given user follows the current user.
     */
    suspend fun isFollowedByUser(profileId: String): Boolean = withContext(Dispatchers.IO) {
        val current = AuthUtils.getCurrentUserId(RetrofitClient.context) ?: return@withContext false
        val resp = api.isFollowedByUser(
            followedIdFilter = "eq.$current",
            followerIdFilter = "eq.$profileId"
        )
        val header = resp.headers()["Content-Range"] ?: return@withContext false
        (header.substringAfterLast('/').toIntOrNull() ?: 0) > 0
    }
}