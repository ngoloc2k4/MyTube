# STATE.md — MyTube

## Completed Phases
- [x] **Phase 0: Android & Kotlin Foundation** (Verified, arm64-v8a APK, Shared Cache ~700MB, App Icons)
- [x] **Phase 1: Fake YouTube (Architecture & Domain Layer)** (Verified, Run ID: 35507507511 in 1m23s)
- [x] **Phase 2: Invidious Engine (First Real Source)** (Verified, Run ID: 35507916247 in 2m48s)
- [x] **Phase 3: Media3 Playback Engine** (Verified, Run ID: 35508358662 in 2m29s)
- [x] **Phase 4: NewPipeExtractor Integration** (Verified, Run ID: 35509204891 in 3m27s)
- [x] **Phase 5: Native InnerTube Engine & 4-Tier Pipeline** (Verified, Run ID: 35509537526 in 1m36s)
- [x] **Phase 6: UI/UX Modernization & Stream Stabilization** (Verified, Run ID: 35511913654 in 1m49s)
  - Full i18n localization (English & Tiếng Việt)
  - Modern 28dp pill search bar with IME Search & auto-dismiss
  - Category FilterChips row (All, Music, Gaming, News, Tech, Podcasts)
  - FullPlayer Action Bar (Like, Share Android Intent, Save, Download) & Expandable Description
  - Floating MiniPlayer pill (16dp rounded, 10dp elevation, 44-48dp touch targets)
  - VideoCard 12dp rounded thumbnail & 6dp high-contrast duration badge
  - Fixed ExoPlayer 403 Forbidden with custom User-Agent & Headers
  - Fixed MediaSession command 5 seek permissions
  - Automatic stream fallback mechanism

## Workflow & User Constraints (STRICT)
- **APK Download:** Luôn dùng lệnh `curl` để download file APK về máy thay vì `gh run download` để tốc độ tải nhanh hơn nhiều.
- **CI Monitoring:** Dùng `sleep 60` trước mỗi lần kiểm tra trạng thái CI GitHub Actions để tránh spam API.
- **Build Policy:** Không build APK local trên Termux; toàn bộ artifact được kiểm chứng và đóng gói qua GitHub Actions CI.
