# ROADMAP.md — MyTube

## Phase 0: Android & Kotlin Foundation
- [ ] Thiết lập Gradle build (Kotlin, Compose BOM, Material 3, AndroidX lifecycle)
- [ ] Kiến trúc cơ bản: Activity -> NavHost -> Compose Screen
- [ ] Quản lý State: ViewModel + StateFlow cơ bản

## Phase 1: Fake YouTube (Architecture Validation)
- [ ] Domain Models: `Video`, `SearchResult`, `Channel`
- [ ] `YouTubeRepository` interface & Fake Repository
- [ ] UI Screens: Home, Search, Video Detail với dữ liệu mẫu

## Phase 2: Invidious Engine (First Real Source)
- [ ] Network layer: Retrofit / Ktor + OkHttp
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
