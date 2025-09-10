package club.gifters.giftersclub.model

data class AiMemeResponse(
    val top_text: String?,
    val bottom_text: String?,
    val stickers: List<String>? = emptyList()
)

