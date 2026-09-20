# Taskboard — MyTube

## Active Sprint: Loop Cycle 1 (Phase 4 — NewPipeExtractor Integration) — COMPLETE

| Task ID | Description | Status | Owner | Verifier Gate |
| :--- | :--- | :--- | :--- | :--- |
| `TASK-P4-01` | Add JitPack repository and NewPipeExtractor dependency to Gradle | Done | oma-executor | Gradle sync / CI ✓ |
| `TASK-P4-02` | Implement Downloader implementation for NewPipe using OkHttp | Done | oma-executor | Downloader contract ✓ |
| `TASK-P4-03` | Create `NewPipeYouTubeRepository` implementing `YouTubeRepository` | Done | oma-executor | Domain model mapping ✓ |
| `TASK-P4-04` | Implement Cascading Composite Repository: Invidious -> NewPipe -> Fake fallback | Done | oma-executor | Unit test / CI build ✓ |
| `TASK-P4-05` | CI Verification on GitHub Actions (arm64-v8a standalone APK) | Done | oma-verifier | Run #35509204891 (3m27s) ✓ |

## Next Sprint: Loop Cycle 2 (Phase 5 — Native InnerTube Engine)
- [ ] `TASK-P5-01`: InnerTube client context payload generator (ANDROID / WEB / TV clients)
- [ ] `TASK-P5-02`: Direct InnerTube browse & search endpoint caller
- [ ] `TASK-P5-03`: InnerTube player endpoint & streaming data parser (cipher/sig deciphers)
- [ ] `TASK-P5-04`: Integrate InnerTube into `CascadingYouTubeRepository` (Invidious -> InnerTube -> NewPipe -> Fake)
