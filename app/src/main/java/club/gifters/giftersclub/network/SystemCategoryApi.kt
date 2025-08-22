package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.SystemCategory
import retrofit2.http.GET
import retrofit2.http.Query

interface SystemCategoryApi {
    @GET("system_categories")
    suspend fun getCategories(
        @Query("select", encoded = true) select: String = "id,name,description",
        @Query("order", encoded = true) order: String = "name.asc"
    ): List<SystemCategory>
}

