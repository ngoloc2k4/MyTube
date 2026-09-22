package vn.lobie.mytube.domain.model

data class Comment(
    val id: String,
    val author: String,
    val authorAvatarUrl: String,
    val content: String,
    val publishedTimeText: String,
    val likeCount: Long = 0,
    val replyCount: Int = 0,
    val isPinned: Boolean = false,
    val isChannelOwner: Boolean = false
)
