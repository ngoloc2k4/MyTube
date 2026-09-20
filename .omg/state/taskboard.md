# Taskboard — MyTube

## Active Sprint: Loop Cycle 1 (Phase 4 — NewPipeExtractor Integration)

| Task ID | Description | Status | Owner | Verifier Gate |
| :--- | :--- | :--- | :--- | :--- |
| `TASK-P4-01` | Add JitPack repository and NewPipeExtractor dependency to Gradle | Ready | oma-executor | Gradle sync / CI |
| `TASK-P4-02` | Implement Downloader implementation for NewPipe using OkHttp | Ready | oma-executor | Downloader contract |
| `TASK-P4-03` | Create `NewPipeYouTubeRepository` implementing `YouTubeRepository` | Ready | oma-executor | Domain model mapping |
| `TASK-P4-04` | Implement Cascading Composite Repository: Invidious -> NewPipe -> Fake fallback | Ready | oma-executor | Unit test / CI build |
| `TASK-P4-05` | CI Verification on GitHub Actions (arm64-v8a standalone APK) | Blocked by 01-04 | oma-verifier | GitHub Actions run green |

## Completed Tasks
- [x] `TASK-P0-ALL`: Foundation, Gradle Catalogs, Compose BOM, CI Shared Cache, arm64-v8a APK, Adaptive Icons.
- [x] `TASK-P1-ALL`: Domain models, FakeYouTubeRepository, HomeScreen, VideoCard, HomeViewModel.
- [x] `TASK-P2-ALL`: OkHttp + Kotlinx Serialization, InvidiousApiClient, InvidiousYouTubeRepository with fallback.
- [x] `TASK-P3-ALL`: Media3 ExoPlayer, PlaybackService, MediaSession, PlayerViewModel, MiniPlayer, FullPlayer.
