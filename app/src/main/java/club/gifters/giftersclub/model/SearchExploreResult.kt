package club.gifters.giftersclub.model

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