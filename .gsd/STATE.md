# MyTube v2.0 — GSD State

## Current Phase
PHASE 3: Tablet/Phone UI, Region & Language Settings, Vertical Queue & Playlists — ✅ DEPLOYED ON DEVICE

## Ralph Pipeline Status
| Gate | Status | Timestamp |
|------|--------|-----------|
| team-plan | ✅ COMPLETE | 2026-09-20T13:48Z |
| team-prd | ✅ APPROVED | 2026-09-20T13:49Z |
| team-exec | ✅ COMPLETE | 2026-09-20T16:28Z |
| team-verify | ✅ PASS | 2026-09-20T16:30Z |
| team-fix | ✅ RESOLVED | 2026-09-20T16:29Z |

## Verified Enhancements (Empirical)
- **APK Deployment**: Installed onto Xiaomi Pad, PID `30357` active with window `Window{38c52bf vn.lobie.mytube/vn.lobie.mytube.MainActivity}` running smoothly.
- **UI Mode Setting (Phone / Tablet Selector)**:
  - Added user setting in *Settings > Appearance*: `Tự động (Theo thiết bị)`, `Điện thoại (Phone layout - 1 cột)`, and `Máy tính bảng (Tablet layout - 2 cột)`.
  - Propagated globally via `LocalIsTablet` CompositionLocal.
- **Tablet Layout Orientation Intelligence**:
  - `FullPlayer`: Dual-pane side-by-side (58% / 42%) is enabled **only when in Landscape** on tablet.
  - In Portrait on tablet: uses single-column layout centered with max-width (800dp) so video and controls are properly proportioned, never squished.
- **Rock-Solid Audio-Only Mode**:
  - Replaced stream switching with native ExoPlayer `setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)`.
  - Video decoding and GPU rendering halt immediately, saving battery/data with 0s latency, no stream reloading, and no 403 HTTP / container errors.
  - Rendered dedicated Audio Mode UI with album art, headphones icon, and audio indicator badge.
- **Tablet & Phone Responsive Layout**:
  - `HomeScreen`: `LazyVerticalGrid` with `GridCells.Adaptive(340.dp)` on tablet (2-3 columns), single column on phone.
  - `SubscriptionsScreen`: Adaptive 2-column video grid on tablet.
  - `MusicScreen`: Adaptive 2-column track grid on tablet.
  - `LibraryScreen`: Adaptive 2-column liked videos on tablet.
- **Settings - Content Region & Language**:
  - 8 Content Regions (`VN`, `US`, `JP`, `KR`, `GB`, `FR`, `DE`, `IN`) affecting trending & recommendations.
  - App Language options (`Tiếng Việt`, `English`).
- **Vertical Playlist Queue & Multi-Query Related Videos**:
  - Vertical collapsible queue docked at bottom of `FullPlayer`.
  - Multi-query enrichment for related videos using channel name + title keywords + trending fallback.
  - Compact 16:9 `CompactVideoCard` with duration pills and metadata.
- **Phase 2 Playlists in Library**:
  - Room Database-backed playlists (`PlaylistEntity`, `PlaylistVideoEntity`).
  - "+ Tạo mới" playlist creation dialog and delete support.

## Next Items
PHASE 4: Offline Video & Audio Downloads Manager (DownloadManager, Storage access, background caching).
