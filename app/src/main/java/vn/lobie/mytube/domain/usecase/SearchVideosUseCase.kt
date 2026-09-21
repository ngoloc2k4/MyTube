package vn.lobie.mytube.domain.usecase

import vn.lobie.mytube.domain.model.SearchResult
import vn.lobie.mytube.domain.repository.YouTubeRepository

/**
 * UseCase to execute video search queries.
 */
class SearchVideosUseCase(
    private val repository: YouTubeRepository
) {
    suspend operator fun invoke(query: String): Result<List<SearchResult>> {
        if (query.isBlank()) return Result.success(emptyList())
        return repository.search(query.trim())
    }
}
