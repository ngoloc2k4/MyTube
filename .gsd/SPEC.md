# SPEC.md — MyTube

**Status: FINALIZED**
**Version: 2.0**

## 1. Objective
MyTube là Android YouTube client mã nguồn mở, không backend riêng, phục vụ việc tự học Android/Kotlin thực tế qua thực hành kiến trúc Clean Architecture, Jetpack Compose, Media3/ExoPlayer và đa nguồn dữ liệu (multi-engine extraction).

## 2. Core Constraints & Principles
- **No Custom Backend**: Chỉ chạy client-side extraction.
- **Multi-source Resilience**: Invidious API (MVP) -> NewPipeExtractor (Backup) -> Native InnerTube.
- **Normalized Domain Models**: UI chỉ nhận domain models (Video, Channel, StreamInfo,...), hoàn toàn tách biệt với format JSON của các source.
- **Learning-first Protocol**: Không generate code hàng loạt. Học concept -> Hiểu kiến trúc -> Viết code -> Debug -> Verify.

## 3. Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVI / MVVM (ViewModel + StateFlow + Coroutines)
- **DI**: Hilt
- **Navigation**: Navigation Compose
- **Playback**: AndroidX Media3 / ExoPlayer + MediaSessionService (Background playback)
- **Local Storage**: Room + DataStore
