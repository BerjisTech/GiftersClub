package club.gifters.giftersclub.model

import club.gifters.giftersclub.model.Post
import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.model.LiveStream

/**
 * Response model for the search_explore RPC.
 */
data class SearchExploreResult(
    val top: List<Post>,
    val videos: List<Post>,
    val photos: List<Post>,
    val users: List<Profile>,
    val live: List<LiveStream>
)