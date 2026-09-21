package vn.lobie.mytube.domain.usecase

import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.domain.repository.YouTubeRepository

/**
 * UseCase to retrieve trending videos from the repository.
 * Decouples ViewModel from specific repository implementations.
 */
class GetTrendingVideosUseCase(
    private val repository: YouTubeRepository
) {
    suspend operator fun invoke(): Result<List<Video>> {
        return repository.getTrendingVideos()
    }
}
