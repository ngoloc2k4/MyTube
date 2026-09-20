# STATE.md — MyTube

## Completed Phases
- [x] **Phase 0: Android & Kotlin Foundation** (Verified, arm64-v8a APK, Shared Cache ~700MB, App Icons)
- [x] **Phase 1: Fake YouTube (Architecture & Domain Layer)** (Verified, Run ID: 35507507511 in 1m23s)
  - Domain models: `Video`, `Channel`, `SearchResult`, `StreamInfo`
  - Repository contract: `YouTubeRepository` + `FakeYouTubeRepository`
  - Presentation: `HomeUiState` (Loading, Success, Error) + `HomeViewModel` (StateFlow, Coroutines)
  - Compose UI: `VideoCard` (16:9 thumbnail, duration badge, channel avatar) + `HomeScreen` (SearchBar, LazyColumn feed)
  - Image Loading: Coil3 Compose integration
- [x] **Phase 2: Invidious Engine (First Real Source)** (Verified, Run ID: 35507916247 in 2m48s)
  - OkHttp + Kotlinx Serialization
  - Multi-instance rotation for Invidious API
  - Seamless fallback to `FakeYouTubeRepository`
- [x] **Phase 3: Media3 Playback Engine** (Verified, Run ID: 35508358662 in 2m29s)
  - `PlaybackService` (`MediaSessionService`) background playback & media notification
  - `PlayerViewModel` + `PlayerUiState` with `MediaController`
  - `MiniPlayer` (bottom bar, progress line, play/pause, dismiss)
  - `FullPlayer` (`PlayerView` surface, seekbar, gestures, 10s skip, back-collapse)

- [ ] **Phase 4: NewPipeExtractor Integration** (In verification)
  - Thư viện NewPipeExtractor + JDK NIO Desugaring
  - `NewPipeDownloader` (OkHttp implementation)
  - `NewPipeYouTubeRepository`
  - `CascadingYouTubeRepository` (Invidious -> NewPipe -> Fake fallback)

## Next Phase: Phase 5 (Native InnerTube Engine)
