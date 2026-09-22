package vn.lobie.mytube.domain.usecase

import vn.lobie.mytube.domain.model.Comment
import vn.lobie.mytube.domain.repository.YouTubeRepository

/**
 * UseCase to fetch comments for a specific video.
 */
class GetCommentsUseCase(
    private val repository: YouTubeRepository
) {
    suspend operator fun invoke(videoId: String): Result<List<Comment>> {
        if (videoId.isBlank()) return Result.success(emptyList())
        return repository.getComments(videoId)
    }
}
