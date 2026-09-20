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
- [ ] Network layer: Retrofit / Ktor / OkHttp
- [ ] Invidious API Client (Search, Video metadata, Stream URLs)
- [ ] Source Selector & Repository integration

## Phase 3: Media3 Playback Engine
- [ ] Media3 ExoPlayer integration
- [ ] MediaSessionService cho Background Playback
- [ ] Mini Player & Full Player controls

## Phase 4: NewPipeExtractor Integration
- [ ] Tích hợp NewPipeExtractor library
- [ ] Data Source fallback pipeline: Invidious -> NewPipeExtractor

## Phase 5: Native InnerTube Engine
- [ ] Client context & Innertube endpoint requests
- [ ] Stream extraction, PoToken & Cipher handling
