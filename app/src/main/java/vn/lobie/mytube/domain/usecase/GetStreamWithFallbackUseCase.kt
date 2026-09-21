package vn.lobie.mytube.domain.usecase

import vn.lobie.mytube.domain.model.StreamInfo
import vn.lobie.mytube.domain.repository.YouTubeRepository

/**
 * UseCase to extract stream metadata with automatic multi-engine cascading fallback.
 */
class GetStreamWithFallbackUseCase(
    private val repository: YouTubeRepository
) {
    suspend operator fun invoke(videoId: String): Result<StreamInfo> {
        if (videoId.isBlank()) return Result.failure(IllegalArgumentException("Video ID cannot be blank"))
        return repository.getStreamInfo(videoId)
    }
}
