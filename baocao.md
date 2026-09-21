# BÁO CÁO TOÀN DIỆN DỰ ÁN MYTUBE

---

## MỤC LỤC
1. [Tổng quan mục tiêu](#1-tổng-quan-mục-tiêu)
2. [Cấu trúc project thực tế](#2-cấu-trúc-project-thực-tế)
3. [Chi tiết từng chức năng đã làm xong](#3-chi-tiết-từng-chức-năng-đã-làm-xong)
4. [Vấn đề đã gặp và cách fix](#4-vấn-đề-đã-gặp-và-cách-fix)
5. [Cơ chế Fallback & Resilience](#5-cơ-chế-fallback--resilience)
6. [Trạng thái hiện tại](#6-trạng-thái-hiện-tại)
7. [Quyết định không làm](#7-quyết-định-không-làm)
8. [TODO tiếp theo](#8-todo-tiếp-theo)

---

### 1. Tổng quan mục tiêu

MyTube là ứng dụng phát và nghe nhạc YouTube client mã nguồn mở, hoạt động độc lập trên hệ điều hành Android. Dự án được xây dựng dựa trên các tiêu chuẩn kỹ thuật hiện đại:
- **Ngôn ngữ & Nền tảng**: 100% Kotlin hiện đại, tuân thủ nguyên lý Clean Architecture và mô hình MVI (Model-View-Intent) kết hợp luồng dữ liệu một chiều (Unidirectional Data Flow).
- **Giao diện người dùng**: Xây dựng hoàn toàn bằng Jetpack Compose và Material Design 3, hỗ trợ chế độ màu tối/sáng, tự động co giãn bố cục linh hoạt giữa điện thoại và máy tính bảng.
- **Trình phát đa phương tiện**: Ứng dụng AndroidX Media3 (ExoPlayer) native, hỗ trợ phát video mượt mà, phát nền khi tắt màn hình, Picture-in-Picture và điều khiển ngoại vi.
- **Triết lý Local-First & Multi-Source**: Ứng dụng hoạt động thuần client-side, tuyệt đối không phụ thuộc vào máy chủ trung gian (backend riêng) hay Google Play Services. Hệ thống tích hợp kiến trúc đa nguồn bóc tách tự phục hồi linh hoạt, kết hợp đồng thời giữa InnerTube API, NewPipeExtractor và hệ thống mirror Invidious công cộng, đảm bảo phát video ổn định, không quảng cáo và tôn trọng quyền riêng tư của người dùng.

---

### 2. Cấu trúc project thực tế

Cấu trúc mã nguồn thực tế tại thư mục `app/src/main/java/vn/lobie/mytube/` được phân chia thành các gói chức năng chặt chẽ:

1. **`core/common/`**:
   - `AppLogger.kt`: Hệ thống ghi log nội bộ đa cấp độ (Debug, Info, Warning, Error), quản lý bộ đệm xoay vòng trong bộ nhớ RAM (tối đa 350 bản ghi), bộ ghi tệp xoay vòng (tối đa 2MB trong bộ nhớ cache) và tiện ích làm sạch URL nhạy cảm (`maskUrl`).

2. **`data/local/`**:
   - `db/`: Cơ sở dữ liệu SQLite cục bộ thông qua Room Database (`MyTubeDatabase.kt`).
     - `entity/`: Định nghĩa các bảng dữ liệu gồm `WatchHistoryEntity` (lịch sử xem video và tiến trình phát), `LikedVideoEntity` (danh sách video đã thích), `SubscriptionEntity` (các kênh đã đăng ký theo dõi), `PlaylistEntity` và `PlaylistVideoEntity` (danh sách phát tùy chọn và video thuộc danh sách), `DownloadEntity` (trạng thái tải tệp), `HiddenVideoEntity` (danh sách video bị ẩn khỏi đề xuất).
     - `dao/`: Các giao diện truy vấn bất đồng bộ qua Kotlin Flow gồm `WatchHistoryDao`, `LikedVideoDao`, `SubscriptionDao`, `PlaylistDao`, `PlaylistVideoDao`, `DownloadDao`, `HiddenVideoDao`.
   - `prefs/`: `SettingsDataStore.kt` quản lý lưu trữ cài đặt ứng dụng qua AndroidX Preferences DataStore (chủ đề giao diện, chất lượng video mặc định, tốc độ mặc định, phát âm thanh nền, tự động chuyển bài, giới hạn dung lượng cache, vùng nội dung, ngôn ngữ ứng dụng, bố cục thiết bị).

3. **`data/remote/`**:
   - `innertube/`: `InnerTubeClient.kt` và `InnerTubeParser.kt` đảm nhận gửi truy vấn trực tiếp đến các endpoint chính thức của YouTube InnerTube API v1 (`browse`, `search`, `player`) với client context Android tối ưu và phân giải cấu trúc JSON phản hồi.
   - `newpipe/`: `NewPipeDownloader.kt` đóng vai trò tầng trung gian HTTP cho thư viện NewPipeExtractor, hỗ trợ trích xuất dữ liệu trực tiếp từ mã HTML/JS của YouTube mà không qua proxy.
   - `InvidiousApiClient.kt` & `dto/InvidiousDto.kt`: Quản lý danh sách các instance Invidious công cộng, thực hiện xoay vòng instance tự động khi có lỗi kết nối và đo độ trễ mạng thời gian thực.
   - `ryd/ReturnYouTubeDislikeClient.kt`: Gọi REST API từ Return YouTube Dislike để bóc tách chính xác số lượt không thích của video.
   - `sponsorblock/`: `SponsorBlockClient.kt` và `SponsorSegment.kt` tải danh sách các mốc thời gian quảng cáo, tự giới thiệu hoặc tài trợ trong video để tự động bỏ qua khi phát.

4. **`data/repository/`**:
   - `CascadingYouTubeRepository.kt`: Bộ điều phối trung tâm triển khai `YouTubeRepository`, áp dụng thứ tự ưu tiên động (`enginePriority`) để lần lượt thử nghiệm qua các tầng bóc tách dữ liệu cho video thịnh hành, tìm kiếm, chi tiết video và luồng phát.
   - `InnerTubeYouTubeRepository.kt`: Triển khai kho dữ liệu thông qua InnerTube.
   - `NewPipeYouTubeRepository.kt`: Triển khai kho dữ liệu thông qua NewPipeExtractor.
   - `InvidiousYouTubeRepository.kt`: Triển khai kho dữ liệu thông qua Invidious.
   - `FakeYouTubeRepository.kt`: Kho dữ liệu dự phòng ngoại tuyến cung cấp video mẫu chuẩn và luồng phát kiểm thử.

5. **`domain/`**:
   - `model/`: Các thực thể dữ liệu thuần túy gồm `Video`, `Channel`, `StreamInfo`, `SearchResult`, `VideoStream`, `AudioStream`, `SubtitleTrack`.
   - `repository/`: Giao diện trừu tượng `YouTubeRepository`.

6. **`playback/`**:
   - `PlaybackService.kt`: Service nền kế thừa `MediaSessionService` của AndroidX Media3, quản lý vòng đời `MediaSession`, xử lý phát âm thanh khi ứng dụng ẩn hoặc tắt màn hình, hiển thị bảng điều khiển media trên thanh thông báo hệ thống và bắt sự kiện ngắt tai nghe `BecomingNoisy`.

7. **`ui/`**:
   - `components/`: `VideoCard.kt` (thẻ video chuẩn 16:9), `CompactVideoCard.kt` (thẻ video thu nhỏ), `ShimmerEffect.kt` (hiệu ứng khung xương phát sáng khi tải dữ liệu).
   - `home/`: `HomeScreen.kt`, `HomeViewModel.kt`, `HomeUiState.kt` quản lý màn hình chính, thanh tìm kiếm nhanh, thanh cuộn chip chủ đề phân loại và lưới video.
   - `player/`: `FullPlayer.kt`, `MiniPlayer.kt`, `PlayerViewModel.kt`, `PlayerUiState.kt` phụ trách toàn bộ trải nghiệm xem video, thanh trượt thời gian, nút bấm điều khiển, chọn nguồn phát, chỉnh phụ đề, hàng đợi và cử chỉ vuốt.
   - `library/`: `LibraryScreen.kt`, `LibraryViewModel.kt` quản lý thư viện cá nhân với các tab Lịch sử xem, Video đã thích và Danh sách phát tùy chỉnh.
   - `music/`: `MusicScreen.kt`, `MusicViewModel.kt` cung cấp giao diện nghe nhạc chuyên biệt với các bảng xếp hạng và bài hát nổi bật.
   - `subscriptions/`: `SubscriptionsScreen.kt`, `SubscriptionsViewModel.kt` quản lý danh sách kênh theo dõi và cập nhật video mới từ các kênh này.
   - `settings/`: `SettingsScreen.kt`, `SettingsViewModel.kt` cung cấp giao diện quản lý nguồn phát, đo ping instance Invidious, dọn cache, chuyển đổi giao diện và bảng đọc nhật ký gỡ lỗi thời gian thực.
   - `navigation/`: `AppNavigation.kt` định nghĩa cấu trúc thanh điều hướng đáy và các tab chính.
   - `util/`: `ChapterParser.kt` (bóc tách các mốc chương từ mô tả video), `UiFormatUtils.kt` (tiện ích định dạng số lượt xem và thời lượng).

8. **`.github/workflows/`**:
   - `build-apk.yml`: Quy trình tự động hóa CI trên GitHub Actions biên dịch bản phát hành APK cho kiến trúc `arm64-v8a`, tối ưu hóa bộ nhớ đệm Gradle và tự động tải file APK lên bản phát hành Release Beta trên GitHub khi có commit mới.

---

### 3. Chi tiết từng chức năng đã làm xong

#### 3.1. Chức năng Tìm kiếm (Search & Quick Category Filter)
- **Luồng hoạt động**: Người dùng nhập từ khóa vào thanh tìm kiếm hoặc nhấn vào một chip chủ đề -> `HomeScreen` chuyển giao sự kiện đến `HomeViewModel` thông qua hàm `search` hoặc `selectCategory` -> ViewModel kích hoạt trạng thái tải với hiệu ứng khung xương phát sáng -> gọi hàm `search` của `CascadingYouTubeRepository` -> repository duyệt qua chuỗi engine ưu tiên (`InnerTube` -> `NewPipe` -> `Invidious`) -> dữ liệu kết quả được bóc tách và phân loại thành các mục video trong `SearchResult` -> cập nhật `HomeUiState` để hiển thị danh sách kết quả lên màn hình.
- **File chịu trách nhiệm**: `HomeScreen.kt`, `HomeViewModel.kt`, `CascadingYouTubeRepository.kt`, `InnerTubeClient.kt`, `InnerTubeParser.kt`.
- **Quyết định kỹ thuật quan trọng**: InnerTube được cấu hình sử dụng client context dạng ứng dụng di động Android chính thức. Quyết định này giúp truy vấn kết quả tìm kiếm cực nhanh, cấu trúc dữ liệu phong phú, đồng thời tránh việc YouTube phát hiện yêu cầu cào dữ liệu tự động từ trình duyệt web.

#### 3.2. Chức năng Xem chi tiết video (Watch Detail & Metadata)
- **Luồng hoạt động**: Người dùng bấm vào một thẻ video trên bất kỳ màn hình nào -> `PlayerViewModel.playVideo` tiếp nhận đối tượng video và kiểm tra bản ghi lịch sử xem trong Room DB để chuẩn bị mốc phát tiếp -> ViewModel song song gọi `getVideoDetails` để lấy mô tả đầy đủ, thông tin kênh, số lượt like và gọi `ReturnYouTubeDislikeClient.getDislikeCount` để lấy số lượt dislike thực tế -> đồng thời gọi `SponsorBlockClient.fetchSegments` để chuẩn bị danh sách các phân đoạn cần tự động bỏ qua -> nạp thông tin vào `PlayerUiState` hiển thị lên `FullPlayer`.
- **File chịu trách nhiệm**: `PlayerViewModel.kt`, `FullPlayer.kt`, `ReturnYouTubeDislikeClient.kt`, `SponsorBlockClient.kt`, `WatchHistoryDao.kt`.
- **Quyết định kỹ thuật quan trọng**: Tải bất đồng bộ song song nhiều tác vụ thông qua Coroutines giúp giao diện trình phát mở lên ngay lập tức mà không phải chờ đợi các API phụ trợ như lượt dislike hay mốc tài trợ.

#### 3.3. Trình phát Video & Trình phát Thu nhỏ (FullPlayer & MiniPlayer)
- **Luồng hoạt động**: Khi có yêu cầu phát -> `PlayerViewModel` gọi hàm trích xuất luồng `getStreamInfo` từ `CascadingYouTubeRepository` -> repository bóc tách luồng video hoặc HLS manifest từ engine đang ưu tiên -> ViewModel gọi hàm `setPlayerMedia` để nạp `MediaItem` vào `ExoPlayer` -> kích hoạt `prepare()` và `play()` -> `PlayerView` trong `FullPlayer` thực hiện giải mã hình ảnh -> khi người dùng nhấn nút thu nhỏ hoặc nhấn nút Back của Android, `FullPlayer` co gọn thành thanh `MiniPlayer` nổi ở cạnh đáy màn hình -> `PlaybackService` tiếp tục duy trì phiên phát ngay cả khi thoát màn hình ứng dụng.
- **File chịu trách nhiệm**: `FullPlayer.kt`, `MiniPlayer.kt`, `PlayerViewModel.kt`, `PlaybackService.kt`.
- **Quyết định kỹ thuật quan trọng**: Sử dụng cơ chế hàng đợi lệnh phát `pendingMediaItem` để xử lý triệt để xung đột thời gian (race condition) khi người dùng mở ứng dụng và ấn phát video trước khi phiên kết nối với `PlaybackService` hoặc `ExoPlayer` hoàn tất khởi tạo. Giao diện điều khiển được vẽ hoàn toàn bằng Composable đè lên `PlayerView` để đạt độ mượt và khả năng tùy biến tuyệt đối.

#### 3.4. Tùy chỉnh Độ phân giải, Tốc độ, Phụ đề, Bỏ qua tài trợ & Cử chỉ
- **Độ phân giải**: Người dùng mở bảng chọn độ phân giải trong `FullPlayer` -> `PlayerViewModel.selectQuality` lọc danh sách luồng phát của video theo nhãn độ phân giải đã chọn -> trích xuất URL tương ứng -> cập nhật lại vào `ExoPlayer` tại vị trí phát hiện tại mà không làm ngắt quãng trải nghiệm.
- **Tốc độ phát & Vòng lặp A-B**: `PlayerViewModel.setPlaybackSpeed` gán thông số tốc độ vào bộ điều khiển của ExoPlayer. Tính năng A-B Loop lưu mốc thời gian bắt đầu và kết thúc, tiến trình theo dõi định kỳ kiểm tra và tự động tua về mốc bắt đầu khi chạm mốc kết thúc.
- **Tùy biến phụ đề**: Hỗ trợ chọn kênh phụ đề theo ngôn ngữ, tùy chỉnh hệ số tỷ lệ cỡ chữ (từ 0.75x đến 1.5x) và áp dụng các kiểu màu nền (Đen mờ, Đen đặc, Trong suốt, Chữ vàng nền đen) trực tiếp lên `CaptionStyleCompat` của `PlayerView`.
- **Bỏ qua tài trợ (SponsorBlock)**: Tiến trình cập nhật vị trí phát trong `PlayerViewModel` định kỳ đối chiếu giây phát hiện tại với danh sách phân đoạn tài trợ, tự động nhảy qua (`seekTo`) phân đoạn đó khi tiến trình đi vào vùng tài trợ.
- **Cử chỉ vuốt**: `FullPlayer` hỗ trợ cử chỉ vuốt dọc nửa trái màn hình để điều chỉnh độ sáng hệ thống, vuốt dọc nửa phải để tăng giảm âm lượng media, và chạm đúp hai bên để tua nhanh/lùi theo bước 5 đến 30 giây tùy chọn.

#### 3.5. Nhật ký gỡ lỗi trực tiếp trong ứng dụng (In-App Online Debug Logger)
- **Luồng hoạt động**: Tất cả các điểm nhạy cảm trong hệ thống (chọn engine, lỗi mạng, xoay vòng instance, ngoại lệ ExoPlayer) đều gọi các hàm ghi nhận của `AppLogger` (`d`, `i`, `w`, `e`) -> `AppLogger` thêm bản ghi vào bộ nhớ RAM xoay vòng và tệp rolling cache, tự động làm sạch URL qua hàm `maskUrl` -> tại màn hình Cài đặt, người dùng bấm vào mục "Nhật ký gỡ lỗi" -> `SettingsScreen` mở hộp thoại `DebugLogsDialog` -> người dùng có thể lọc danh sách log theo tag (`Player`, `Source`, `Invidious`, `NewPipe`, `InnerTube`, `Fallback`), tìm kiếm từ khóa hoặc video ID, mở rộng xem chi tiết vết ngăn xếp lỗi (stack trace), sao chép toàn bộ hoặc chia sẻ trực tiếp qua các ứng dụng nhắn tin.
- **File chịu trách nhiệm**: `AppLogger.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `PlayerViewModel.kt`, `CascadingYouTubeRepository.kt`.
- **Quyết định kỹ thuật quan trọng**: Không sử dụng quyền đọc logcat toàn hệ thống của Android (`READ_LOGS`) vì vi phạm quyền riêng tư và yêu cầu quyền root/ADB. Thay vào đó, xây dựng một bộ logger tự trị nội bộ chỉ ghi nhận thông tin trong phạm vi ứng dụng, đồng thời loại bỏ toàn bộ token bảo mật và địa chỉ IP nhạy cảm trong URL stream trước khi lưu trữ.

---

### 4. Vấn đề đã gặp và cách fix

#### 4.1. Lỗi HTTP 403 Forbidden (`InvalidResponseCodeException`) khi phát luồng video
- **Hiện tượng**: Khi ấn phát video, trình phát dừng ở trạng thái xoay vòng hoặc báo lỗi mạng với mã lỗi 403 từ máy chủ `googlevideo.com`.
- **Nguyên nhân cốt lõi**:
  1. Link stream do các instance Invidious công cộng trả về bị cơ chế IP-lock của YouTube chặn lại: máy chủ Invidious giải mã link tại địa chỉ IP của server đó, nhưng thiết bị người dùng lại gửi yêu cầu phát từ địa chỉ IP mạng di động/Wi-Fi khác nhau.
  2. Thiếu cấu hình tiêu đề `User-Agent` chuẩn của nền tảng di động hoặc YouTube kiểm tra chữ ký hàm biến đổi tham số `n` (`n-parameter`) để hạn chế băng thông của công cụ cào dữ liệu.
- **Cách khắc phục**:
  1. Tái cấu trúc pipeline ưu tiên trong `CascadingYouTubeRepository`: Đưa `InnerTube` (sử dụng client context Android chính thức từ chính thiết bị người dùng) và `NewPipeExtractor` (giải mã trực tiếp trên máy người dùng, đồng bộ địa chỉ IP) lên vị trí ưu tiên hàng đầu trước Invidious.
  2. Bổ sung cơ chế phát hiện lỗi HTTP trong `InvidiousApiClient`: Khi instance trả về mã lỗi 403 hoặc 429, lập tức kích hoạt xoay vòng sang instance tiếp theo và ghi nhận cảnh báo vào `AppLogger`.
  3. Bổ sung cơ chế tự phục hồi trong `PlayerViewModel`: Khi `ExoPlayer` kích hoạt sự kiện `onPlayerError` do lỗi mã nguồn phát, hệ thống tự động kiểm tra và chuyển đổi sang luồng thay thế hoặc luồng dự phòng kiểm nghiệm chuẩn để tránh ứng dụng bị đơ cứng.

#### 4.2. Xung đột chữ ký hàm sinh mã trên JVM (`Platform declaration clash`)
- **Hiện tượng**: Quá trình biên dịch mã nguồn báo lỗi xung đột phương thức `setEnginePriority` trong `CascadingYouTubeRepository.kt`.
- **Nguyên nhân**: Trong Kotlin, khi khai báo biến public `var enginePriority: List<String>`, trình biên dịch tự động sinh ra một hàm setter ngầm định có chữ ký `setEnginePriority(List<String>)`. Việc lập trình viên định nghĩa thêm một hàm nghiệp vụ cùng tên gây trùng lặp chữ ký phương thức trên bytecode của máy ảo Java.
- **Cách khắc phục**: Đổi tên hàm nghiệp vụ cập nhật thứ tự nguồn thành `updateEnginePriority(order: List<String>)`, phân biệt rành mạch với setter tự động của biến.

#### 4.3. Lỗi cú pháp đồng bộ hóa trong Kotlin (`@Synchronized`)
- **Hiện tượng**: Quy trình biên dịch trên GitHub Actions thất bại tại tệp `AppLogger.kt` với thông báo lỗi cú pháp không hợp lệ.
- **Nguyên nhân**: Viết hàm theo cú pháp của ngôn ngữ Java `private synchronized fun writeToFile(...)`. Trong Kotlin, `synchronized` là một annotation chứ không phải là một từ khóa khai báo hàm.
- **Cách khắc phục**: Đổi cú pháp thành `@Synchronized private fun writeToFile(...)` và bổ sung đầy đủ tham số nội dung chi tiết `raw` cho tất cả các mức độ ghi nhận nhật ký của `AppLogger`.

#### 4.4. Sự cố phình to và phân mảnh bộ nhớ đệm CI trên GitHub Actions
- **Hiện tượng**: Các lần chạy CI trước đây mất nhiều thời gian tải lại các phần cache riêng rẽ cho Gradle, đôi khi gặp lỗi thiếu tệp phụ thuộc giữa các công đoạn.
- **Nguyên nhân**: Phân mảnh bộ đệm thành nhiều key nhỏ riêng biệt cho từng thư mục `~/.gradle/caches`, `wrapper` và dependencies.
- **Cách khắc phục**: Hợp nhất thành một bước duy nhất `Single Unified Gradle Cache` trong tệp workflow `.github/workflows/build-apk.yml`, tính toán khóa lưu trữ dựa trên mã băm của `gradle/libs.versions.toml` và tất cả các file cấu hình `build.gradle.kts`. Thời gian hoàn thành đóng gói APK giảm xuống chỉ còn khoảng 2 phút 26 giây.

#### 4.5. Bố cục hiển thị bị chật chội trên máy tính bảng xoay dọc (Portrait Tablet)
- **Hiện tượng**: Trên màn hình máy tính bảng kích thước lớn, khi người dùng đặt máy ở hướng xoay dọc, giao diện tự động chia 2 cột gây hiện tượng các thành phần bị ép dẹp, chữ bị tràn hàng.
- **Nguyên nhân**: Logic ban đầu chỉ kiểm tra kích thước chiều ngang đơn thuần (`smallestScreenWidthDp` hoặc `screenWidthDp >= 600.dp`) mà không tính đến tỷ lệ khung hình và hướng xoay thực tế của thiết bị.
- **Cách khắc phục**: Tinh chỉnh lại điều kiện xác định giao diện trong `MainActivity.kt`, kết hợp kiểm tra hướng dọc/ngang từ `LocalConfiguration.current.orientation` và bổ sung thiết lập ép kiểu chế độ giao diện trong Cài đặt (cho phép người dùng chủ động chọn kiểu Điện thoại hoặc Máy tính bảng theo sở thích cá nhân).

---

### 5. Cơ chế Fallback & Resilience

Kiến trúc tự phục hồi của MyTube hoạt động theo mô hình tầng bậc đa lớp, đảm bảo trải nghiệm xem video không bao giờ bị gián đoạn:

1. **Tầng 1 - InnerTube Engine (Ưu tiên hàng đầu)**:
   - Gửi yêu cầu trực tiếp đến endpoint nội bộ của YouTube với bối cảnh client ứng dụng Android chính thức.
   - Ưu điểm: Tốc độ phản hồi cực nhanh, không qua máy chủ trung gian, ít khi bị chặn bởi các đợt cập nhật web của YouTube.

2. **Tầng 2 - NewPipeExtractor (Dự phòng trực tiếp)**:
   - Nếu InnerTube gặp sự cố phân giải dữ liệu hoặc cấu trúc phản hồi thay đổi, hệ thống tự động kích hoạt bóc tách trực tiếp bằng bộ phân tích NewPipeExtractor chạy cục bộ trên máy.
   - Ưu điểm: Phân tích sâu các luồng phân đoạn DASH, hỗ trợ tách riêng luồng hình ảnh chất lượng cao và luồng âm thanh nguyên bản.

3. **Tầng 3 - Invidious API Mirror Pool (Dự phòng phân tán)**:
   - Khi cả hai engine trực tiếp đều gặp trở ngại từ hệ thống mạng cục bộ, yêu cầu được chuyển tiếp đến mạng lưới các instance Invidious công cộng.
   - Đi kèm cơ chế xoay vòng thông minh: nếu một mirror phản hồi chậm hoặc trả về mã lỗi, hệ thống tự động đổi sang mirror khác trong danh sách và ghi nhận cảnh báo.
   - Người dùng có thể tự thêm server Invidious cá nhân và kiểm tra độ trễ ping trực tiếp trong phần Cài đặt.

4. **Tầng 4 - Fake Repository (Lưới bảo vệ cuối cùng)**:
   - Khi toàn bộ các nguồn trực tuyến bị ngắt kết nối hoàn toàn (thiết bị mất mạng Internet hoặc lỗi mạng diện rộng), kho dữ liệu mẫu cục bộ sẽ tự động nạp các video thử nghiệm kèm luồng phát video tiêu chuẩn.
   - Tác dụng: Ngăn chặn triệt để hiện tượng ứng dụng bị crash đột ngột hoặc hiển thị màn hình trắng xóa vô nghĩa, thông báo cho người dùng biết trạng thái mạng một cách thân thiện.

5. **Quyền điều khiển linh hoạt của người dùng**:
   - Trong màn hình Cài đặt, mục "Bộ quản lý Nguồn phát" cho phép người dùng thay đổi thứ tự ưu tiên giữa các engine (ví dụ: chuyển NewPipe hoặc Invidious lên vị trí số 1 theo nhu cầu).
   - Trên trình phát video có gắn huy hiệu nhỏ hiển thị chính xác engine và độ phân giải đang tải luồng (ví dụ: `[InnerTube · 1080p]` hoặc `[NewPipe · 720p]`), giúp người dùng luôn biết rõ nguồn dữ liệu đang phát.

---

### 6. Trạng thái hiện tại

#### 6.1. Những phần đã hoàn thiện và hoạt động ổn định
- **Giao diện & Điều hướng**:
  - Hệ thống 5 tab hoàn chỉnh: Trang chủ, Thịnh hành / Âm nhạc, Đăng ký kênh, Thư viện, Cài đặt.
  - Tối ưu hóa bố cục linh hoạt cho cả điện thoại di động và máy tính bảng (hướng ngang / hướng dọc).
  - Tích hợp hiệu ứng khung xương phát sáng (Shimmer placeholder) khi nạp dữ liệu.
- **Trình phát đa phương tiện (Playback)**:
  - Chuyển đổi mượt mà giữa trình phát đầy đủ (`FullPlayer`) và trình phát thu nhỏ (`MiniPlayer`).
  - Hỗ trợ đầy đủ chế độ phát trong nền khi tắt màn hình thông qua `PlaybackService` và thanh điều khiển thông báo hệ thống.
  - Hỗ trợ chế độ thu nhỏ hình trong hình (Picture-in-Picture - PiP).
  - Điều chỉnh linh hoạt độ phân giải (Auto, 1080p, 720p, 480p, 360p), tốc độ phát (0.75x đến 2.0x), tỷ lệ co giãn khung hình, cử chỉ vuốt âm lượng/độ sáng.
  - Bỏ qua quảng cáo tự động thông qua SponsorBlock.
  - Hiển thị số lượt dislike thực tế qua Return YouTube Dislike API.
  - Bộ điều khiển phụ đề chi tiết: chỉnh cỡ chữ và đổi màu nền phụ đề.
  - Chế độ lặp đoạn A-B Loop và quản lý sắp xếp lại hàng đợi phát.
- **Cơ sở dữ liệu & Cá nhân hóa cục bộ**:
  - Lưu trữ lịch sử xem tự động với mốc thời gian xem dở, cho phép tiếp tục phát lại chính xác vị trí đã dừng.
  - Quản lý danh sách video đã thích và đăng ký theo dõi kênh hoàn toàn cục bộ trên Room Database, không cần đăng nhập tài khoản.
  - Quản lý danh sách phát (Playlist) cá nhân hóa: tạo, sửa, xóa, thêm/bớt video.
- **Quản lý hệ thống & Gỡ lỗi**:
  - Quản lý thứ tự ưu tiên engine bóc tách dữ liệu và quản lý mirror Invidious có đo độ trễ mạng.
  - Quản lý kích thước bộ nhớ đệm (cache), hỗ trợ dọn dẹp cache một chạm.
  - Bảng xem nhật ký gỡ lỗi (In-App Debug Logs) với bộ lọc, tìm kiếm, xem stack trace chi tiết, sao chép và chia sẻ log.
  - Chạm nhanh vào thông tin phiên bản để sao chép thông số kỹ thuật phần cứng và hệ thống.
- **Đóng gói & Phân phối (CI/CD)**:
  - Tự động hóa quy trình dựng APK kiến trúc `arm64-v8a` bằng GitHub Actions.
  - Tự động xuất bản và ghi đè cập nhật tệp cài đặt mới nhất lên bản phát hành Release Beta trên kho mã nguồn GitHub.

#### 6.2. Những phần còn hạn chế hoặc đang cải thiện
- **Quản lý tải tệp ngoại tuyến (Download Manager)**: Các bảng thực thể và DAO trong Room Database đã được khởi tạo đầy đủ (`DownloadEntity`, `DownloadDao`), tuy nhiên logic dịch vụ tải tệp đa luồng chạy nền và module ghép luồng âm thanh/hình ảnh chưa được kích hoạt thành luồng hoàn chỉnh trên giao diện.
- **Tương tác bình luận (Comments)**: Hiện tại ứng dụng tập trung tối ưu vào trải nghiệm nghe nhìn và độ mượt của luồng phát, tính năng đọc và phân trang bình luận video chưa được tích hợp lên giao diện `FullPlayer`.

---

### 7. Quyết định không làm

Dự án kiên quyết tuân thủ các nguyên tắc thiết kế vì quyền tự do và quyền riêng tư của người dùng:

1. **Không tích hợp dịch vụ Google Play Services hoặc đăng nhập Google Account**:
   - Quyết định: Ứng dụng không sử dụng bất kỳ thư viện dịch vụ độc quyền nào của Google và không cung cấp nút đăng nhập tài khoản Google.
   - Lý do: Loại trừ hoàn toàn nguy cơ tài khoản cá nhân của người dùng bị khóa hoặc gắn cờ từ Google, đồng thời ngăn chặn mọi hành vi theo dõi thói quen xem video của người dùng từ các máy chủ bên ngoài.
2. **Không xây dựng máy chủ trung gian riêng (No Custom Backend)**:
   - Quyết định: Toàn bộ quá trình phân giải yêu cầu, bóc tách dữ liệu và lưu trữ lịch sử đều được xử lý 100% tại phía client trên thiết bị của người dùng.
   - Lý do: Giúp dự án tồn tại vĩnh viễn và độc lập, không phải chịu chi phí vận hành máy chủ đắt đỏ, và không có nguy cơ ứng dụng bị ngừng hoạt động khi máy chủ của nhà phát triển bị sập.
3. **Không xin quyền đọc Logcat hệ thống (`READ_LOGS`)**:
   - Quyết định: Thay vì yêu cầu quyền truy cập vào nhật ký toàn hệ điều hành Android, ứng dụng tự xây dựng bộ `AppLogger` nội bộ khép kín.
   - Lý do: Quyền đọc nhật ký hệ thống là quyền nhạy cảm cấp cao, có thể gây lo ngại về bảo mật hoặc bị Google Play Protect cảnh báo. Logger nội bộ chỉ ghi nhận thông tin trong phạm vi ứng dụng và đảm bảo loại bỏ hoàn toàn các thông tin nhạy cảm.
4. **Không tích hợp bất kỳ SDK theo dõi hành vi, thống kê hay quảng cáo (No Analytics / Telemetry)**:
   - Quyết định: Dự án sạch 100%, không chứa Firebase Analytics, Crashlytics của bên thứ ba hay bất kỳ dịch vụ quảng cáo nào.
   - Lý do: Tối ưu dung lượng ứng dụng ở mức nhẹ nhất có thể (~19.6 MB), tiết kiệm tối đa pin và dữ liệu di động cho người dùng.

---

### 8. TODO tiếp theo

1. **Hoàn thiện module Tải video ngoại tuyến (Offline Download Service)**:
   - Kết hợp `DownloadManager` hoặc `DownloadService` chính thức của AndroidX Media3 để tải song song các luồng video và audio riêng biệt.
   - Tích hợp module đóng gói container đa phương tiện (Muxer) để ghép tệp video và audio thành tệp MP4 hoàn chỉnh lưu vào bộ nhớ máy.
2. **Bổ sung tính năng Nhập / Xuất dữ liệu (Backup & Restore)**:
   - Hỗ trợ xuất toàn bộ lịch sử xem, video đã thích, danh sách phát và danh sách kênh đăng ký thành tệp tin định dạng JSON an toàn.
   - Cho phép nhập lại dữ liệu khi người dùng đổi thiết bị hoặc cài lại ứng dụng.
3. **Bổ sung giao diện xem bình luận (Comments Viewer)**:
   - Xây dựng tab hoặc thanh cuộn mở rộng dưới phần mô tả video trong `FullPlayer` để bóc tách và phân trang danh sách bình luận bằng InnerTube API.
4. **Đồng bộ đăng ký kênh từ tệp xuất của YouTube / NewPipe**:
   - Cho phép người dùng nhập trực tiếp tệp `subscriptions.csv` xuất từ Google Takeout hoặc tệp sao lưu của NewPipe để chuyển đổi danh sách theo dõi kênh chỉ trong một chạm.
5. **Thuật toán gợi ý thông minh cục bộ (Local Recommendation Engine)**:
   - Dựa trên tần suất thể loại và kênh video trong bảng `WatchHistoryDao` để sắp xếp thứ tự ưu tiên cho video đề xuất tại Trang chủ mà không cần gửi bất kỳ dữ liệu nào ra bên ngoài.
