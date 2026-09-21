package vn.lobie.mytube.domain.usecase

import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.domain.repository.YouTubeRepository

/**
 * UseCase to fetch full metadata for a specific video.
 */
class GetVideoDetailsUseCase(
    private val repository: YouTubeRepository
) {
    suspend operator fun invoke(videoId: String): Result<Video> {
        if (videoId.isBlank()) return Result.failure(IllegalArgumentException("Video ID cannot be blank"))
        return repository.getVideoDetails(videoId)
    }
}
