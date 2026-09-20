# Workflow State — MyTube

- **Active Stage:** All Delivery Cycles Complete (Phases 0 - 5 Verified)
- **Architecture Highlights:**
  - Clean Architecture & MVI (StateFlow + Jetpack Compose Material 3)
  - 4-Tier Cascading Repository:
    Invidious API ➔ Native InnerTube ➔ NewPipeExtractor ➔ Fake Deterministic Fallback
  - Media3 Playback Engine (Background MediaSessionService + MiniPlayer / FullPlayer)
  - Continuous Integration: GitHub Actions Unified Shared Cache (~1.1GB), standalone `arm64-v8a` APK generation.
