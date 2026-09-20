package vn.lobie.mytube.domain.model

data class Channel(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val subscriberCountText: String = ""
)
