# ROADMAP.md — MyTube

## Phase 0: Android & Kotlin Foundation
- [x] Thiết lập Gradle build (Kotlin, Compose BOM, Material 3, AndroidX lifecycle)
- [x] Kiến trúc cơ bản: Activity -> NavHost -> Compose Screen
- [x] Quản lý State: ViewModel + StateFlow cơ bản
- [x] CI/CD GitHub Actions: Build file arm64-v8a APK độc lập với Unified Shared Cache

## Phase 1: Fake YouTube (Architecture Validation)
- [x] Domain Models: `Video`, `SearchResult`, `Channel`, `StreamInfo`
- [x] `YouTubeRepository` interface & `FakeYouTubeRepository`
- [x] UI Screens: `HomeScreen` với SearchBar, Video Feed (LazyColumn), `VideoCard` component với duration badge & channel avatar
- [x] Kiểm chứng luồng MVI / Clean Architecture hoạt động hoàn chỉnh

## Phase 2: Invidious Engine (First Real Source)
- [x] Network layer: OkHttp + Kotlinx Serialization
- [x] Invidious API Client (Search, Video metadata, Stream URLs, instance rotator)
- [x] Source Selector & Repository integration (Invidious with Fake fallback)

## Phase 3: Media3 Playback Engine
- [x] Media3 ExoPlayer integration (Core, UI, HLS, DASH, Session)
- [x] MediaSessionService (`PlaybackService`) cho Background Playback & Notification
- [x] Mini Player & Full Player controls với Material 3 & gesture expand/collapse

## Phase 4: NewPipeExtractor Integration
- [x] Tích hợp NewPipeExtractor library & desugar_jdk_libs_nio
- [x] OkHttp Downloader implementation cho NewPipeExtractor
- [x] `NewPipeYouTubeRepository` domain adapter
- [x] `CascadingYouTubeRepository` (Invidious -> NewPipe -> Fake fallback)

## Phase 5: Native InnerTube Engine
- [x] Client context & Innertube endpoint requests (WEB, ANDROID_TESTSUITE)
- [x] Stream extraction & metadata parser
- [x] 4-Tier Cascading Repository pipeline (Invidious -> InnerTube -> NewPipe -> Fake)
