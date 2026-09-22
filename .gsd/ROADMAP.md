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

## Giai đoạn Cải tiến 1: Core Hardening & Resilience
- [x] Chuẩn hóa Chế độ Ngoại tuyến (Offline Error State) thay thế FakeRepo
- [x] Tách tầng Domain UseCase (`GetStreamWithFallbackUseCase`, `SearchVideosUseCase`, `GetTrendingVideosUseCase`)
- [x] Tích hợp bộ lọc bảo vệ quyền riêng tư (Privacy Toggles: SponsorBlock & Return YouTube Dislike)
- [x] Bộ nhớ đệm Invidious Mirror Cache & kiểm tra độ trễ ping trực quan

## Giai đoạn Cải tiến 2: Playback Polish & Nâng tầm tính năng Player
- [x] Chuẩn hóa âm lượng (Audio Normalization / Loudness Enhancer)
- [x] Hẹn giờ ngủ (Sleep Timer) kèm hiệu ứng hạ dần âm lượng (Fade-out)
- [x] Hiệu ứng chuyển bài mượt (Crossfade)
- [x] Hiệu ứng gợn sóng hình cung chạm 2 lần tua nhanh (Double-Tap Seek Ripple Arc & Dynamic Seek Duration)
- [x] Lặp đoạn A-B (A-B Loop) & Tùy biến cỡ chữ / màu nền phụ đề (Subtitles Font & Background)
- [x] Lịch sử tìm kiếm thông minh & Quản lý lịch sử xem (Pause / Clear Watch History)

## Giai đoạn Cải tiến 3: Data Portability & Tương tác cộng đồng
- [x] Nhập danh sách đăng ký từ Google Takeout CSV (`subscriptions.csv`) và NewPipe JSON
- [x] Sao lưu & Phục hồi toàn vẹn 5 bảng Room DB ra file JSON qua Android SAF
- [x] Trình xem bình luận video dạng ModalBottomSheet kéo vuốt kết nối API Invidious

## Giai đoạn Cải tiến 4: Làm giàu dữ liệu âm nhạc (Metadata Enrichment) & Gợi ý Cục bộ
- [x] Bóc tách & Chuẩn hóa tên bài hát / nghệ sĩ tự động từ video YouTube
- [x] Tích hợp MusicBrainz & Cover Art Archive nạp ảnh bìa album gốc chất lượng cao cho chế độ Audio-only
- [x] Thẻ thông tin bài hát (EnrichedMusicBanner) và hộp thoại tiểu sử nghệ sĩ từ Wikipedia / MusicBrainz
- [x] Đồng bộ nghe nhạc ListenBrainz (Scrobbling) với User Token cá nhân
- [x] Thuật toán gợi ý cá nhân hóa 100% cục bộ (Local Recommendation Engine) dựa trên lịch sử xem on-device

## Giai đoạn Cải tiến 5: Offline Media Engine & Download Service Hoàn thiện
- [x] Tích hợp bộ ghép luồng gốc Android `MediaMuxer` + `MediaExtractor` tự động gộp video và audio riêng biệt (DASH streams) thành tệp MP4 chuẩn không cần phụ thuộc thư viện ngoài
- [x] Hỗ trợ tải chỉ âm thanh (Audio-only M4A/MP3) cho nhu cầu nghe nhạc ngoại tuyến và chạy ngầm
- [x] Theo dõi tiến trình tải trực tiếp (Live Progress Tracking) qua StateFlow và Room DAO
- [x] Hộp thoại lựa chọn chất lượng tải xuống (1080p, 720p, 480p, 360p, Audio M4A) ngay trên FullPlayer
- [x] Nâng cấp mục Tải xuống ngoại tuyến trong `LibraryScreen` với badge chất lượng/định dạng, dung lượng file, thanh tiến trình live, phát ngoại tuyến tức thì không cần mạng, và hộp thoại xác nhận xóa

