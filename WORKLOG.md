# Worklog — Team

> Ghi lại tất cả công việc đã làm theo ngày. Ai làm gì, kết quả gì.

---

## [2026-11-24]

| Member | Task | Status | Output | Time |
|--------|------|--------|--------|------|
| Notion AI | Chuẩn bị thử dữ liệu ảnh 16 KiB | 🔄 WIP | Nâng giới hạn host và applet lên 16384 byte; 9 kiểm tra host qua kênh APDU giả đạt trên Java 25, biên dịch đích Java 17. Mức 32 KiB cần xử lý giới hạn short trước khi thử. Chỉ dùng dữ liệu giả; xem `docs/photo-card-experiment.md`. | — |
| Vũ Quốc An & Notion AI | Thử truyền ảnh giả 16 KiB trên simulator | 🔄 WIP | An gửi ảnh host ở chế độ jcsl một thẻ báo đọc lại đúng 16384 byte sau khi ghi. Chưa thử 32 KiB, nhiều thẻ, ảnh chân dung, bảo mật hay hồi quy. | — |
| Notion AI | Chuẩn bị thử ảnh 32 KiB | 🔄 WIP | Chia bộ đệm thành hai vùng 16 KiB và xử lý độ dài 32768; 12 kiểm tra host qua kênh APDU giả đạt, logic applet qua API giả đạt biên 16383/16384/16385/32767/32768. | — |
| Vũ Quốc An & Notion AI | Thử các mức ảnh trên simulator | 🔄 WIP | An báo thử lại 16384 byte vẫn đọc đúng; các file giả 20480, 24576, 28672 và 32768 byte đều lỗi. Ảnh chụp lần 32768 cho thấy BEGIN trả 6700. Chưa xác định nguyên nhân hoặc giới hạn tối đa thực sự; tạm chọn 16 KiB đã kiểm chứng cho thử nghiệm. | — |

---

## [2026-11-20]

| Member | Task | Status | Output | Time |
|--------|------|--------|--------|------|
| Notion AI | Ghi nhận thử nghiệm lưu dữ liệu ảnh và bảo vệ file EEPROM mô phỏng | 🔄 WIP | Cập nhật README thử nghiệm theo log người dùng: ghi/đọc 8 byte, đọc theo offset và nạp lại EEPROM sau khi khởi động simulator; bỏ qua file EEPROM thử nghiệm trong Git. Chưa thử file ảnh hoặc thẻ thật. | — |
| Notion AI | Thử tích hợp giao thức byte ảnh vào applet hiện có trên nhánh riêng | 🔄 WIP | Bổ sung BEGIN/WRITE/FINISH/READ có kiểm tra PIN và trạng thái. Theo log người dùng, simulator từ chối READ trước PIN và ghi/đọc 8 byte sau PIN thành công. Chưa kiểm thử hồi quy, file ảnh hay host; xem `docs/photo-card-experiment.md`. | — |
| Notion AI | Viết tài liệu lý thuyết lưu ảnh thử nghiệm | ✅ Done | Giải thích APDU, cách chia đoạn, kết quả simulator, EEPROM, giới hạn bảo mật và các bước còn lại tại `docs/ly-thuyet-luu-anh-tren-the.md`. | — |
| Notion AI | Thử nối file giả vào host hiện có | 🔄 WIP | Viết tab chọn file giả và đối chiếu byte qua simulator một thẻ. Người dùng xác nhận 7 kiểm tra kênh APDU giả đạt; giao diện báo đọc lại đúng 3172 và 4096 byte, từ chối file 4097 byte ở giới hạn cũ. Chưa kiểm thử hồi quy hoặc thẻ thật. | — |
| Notion AI | Nâng giới hạn thử ảnh và hướng dẫn mở lại Eclipse | 🔄 WIP | Đổi giới hạn thử thành 8192 byte và cập nhật tài liệu. Theo log người dùng, CAP build, cài và giao diện đọc lại đúng file giả 8192 byte trên simulator một thẻ. Chưa kiểm thử hồi quy, nhiều thẻ hoặc phần cứng thật. | — |

---

## [2026-11-13]

| Member | Task | Status | Output | Time |
|--------|------|--------|--------|------|
| Codex | Add mandatory work-log rule and correct the filename casing | ✅ Done | Updated `AGENTS.md` to require `WORKLOG.md`; removed the mistakenly created lowercase `worklog.md`; verified both files | < 10m |
| Codex | Prepare the existing project for GitHub | ✅ Done | Attached the remote `main` history; added `.gitignore`; removed a machine-specific Javadoc path from `.classpath`; checked for secrets | < 10m |
| Codex | Clean source formatting before publication | ✅ Done | Removed trailing whitespace from `EmployeeCardApplet.java`; `git diff --check` passes | < 5m |
| Codex | Publish the project to GitHub | ✅ Done | Authenticated as `Ntd1411` over SSH and pushed the prepared project to `main` while preserving remote history | < 15m |

**Tổng kết ngày:** Đã bổ sung quy định ghi nhật ký, chuẩn hóa `WORKLOG.md`, thiết lập Git và xuất dự án lên nhánh `main` trên GitHub.

---

## [2026-11-22]

| Member | Task | Status | Output | Time |
|--------|------|--------|--------|------|
| Antigravity | Tạo tài liệu phân công công việc Nhóm 11 trong `docs/` | ✅ Done | Đã tạo `docs/phan-cong-cong-viec.md` (13 US, 53 SP, lịch trình 14 ngày và kế hoạch báo cáo Word) từ file phân công | < 10m |
| Antigravity | Tạo nhánh phát triển, commit tài liệu phân công và lập kế hoạch triển khai `US10`–`US13` | ✅ Done | Tạo nhánh `feat/attendance-access-control`, commit `docs/phan-cong-cong-viec.md` và `WORKLOG.md`; tạo kế hoạch chi tiết tại `docs/dung-plan.md` | < 15m |
| Antigravity | Triển khai Applet APDU chấm công/kiểm soát ra vào và dự án Host Eclipse cho `US10`–`US13` | ✅ Done | Cập nhật `EmployeeCardApplet.java` (lệnh `0x20`/`0x30`/`0x32`/`0x34`/`0x40`/`0x42`/`0x50`) và xây dựng project `host/` (`EmployeeSmartCardHost`) với model, provider, repository, service, Swing UI và `AttendanceAndAccessTest` | 1h |
| Antigravity | Chuẩn hóa cấu hình biên dịch Java 17 và file `.launch` cho Eclipse trên `host/` | ✅ Done | Thêm `host/.settings/org.eclipse.jdt.core.prefs` (`--release 17`), `Run_Host_App_US10_US13.launch`, `Run_Tests_US10_US13.launch` và kiểm tra chạy 21/21 test trên JRE 25 của Eclipse | 15m |
| Antigravity | Nối App Host trực tiếp tới Simulator `jcsl` qua APDU và mở rộng kiểm thử | ✅ Done | Thêm `ApduSimulatorCardGateway`, `SwitchableCardGateway`, thanh kết nối `jcsl` trên `MainApp`/`AccessSimulationPanel`; chạy 25/25 test đạt trên JRE 25 | 30m |
| Antigravity | Xử lý lỗi đơ giao diện khi `jcsl` đang bận giữ kết nối từ APDU Console | ✅ Done | Thêm timeout có thể ngắt trong `ApduSimulatorCardGateway.connect`, chạy kết nối qua `SwingWorker` ở `MainApp`, thêm test `TC-GW-05` (26/26 pass) | 15m |
| Antigravity | Sửa lỗi lệch trạng thái chấm công khi cùng giây, đồng bộ ca khi khởi động lại và tự lấy giờ hiện tại trên UI | ✅ Done | Giữ đúng thứ tự chèn theo thời gian ở `AttendanceService`/`ShiftPairingService`, bổ sung `syncFromRecords` trong `VirtualStoreCardGateway`/`MainApp`, bật tự cập nhật giờ ở `AttendancePanel`, thêm test `TC-10-05` (27/27 pass) | 15m |
| Antigravity | Hỗ trợ mô phỏng 5 thẻ độc lập theo AID riêng và cài đặt 1-click lên Simulator `jcsl` | ✅ Done | Nâng cấp `ApduSimulatorCardGateway` với ánh xạ AID `112233445501`..`112233445505`, `deployMultiCards` qua `AMService`, nút `Cài 5 thẻ lên jcsl` ở `MainApp`, kiểm thử `TC-GW-06` (28/28 pass) và kiểm tra thực tế trên `jcsl` | 30m |

**Tổng kết ngày:** Đã hoàn thiện tài liệu phân công Nhóm 11, kế hoạch triển khai, Applet + ứng dụng Host kết nối và mô phỏng 5 thẻ độc lập trên Java Card Simulator (`jcsl`) cho `US10`–`US13`, đạt 28/28 test tự động trên JRE 25 của Eclipse.

---

## [YYYY-MM-DD]

| Member | Task | Status | Output | Time |
|--------|------|--------|--------|------|
| [Tên] | [mô tả task] | ✅ Done | [link/kết quả] | 2h |
| [Tên] | [mô tả task] | 🔄 WIP | [mô tả tiến độ] | 1.5h |
| [Tên] | [mô tả task] | ❌ Blocked | [lý do block] | - |

**Tổng kết ngày:** [1-2 câu về tiến độ chung]

---

## [YYYY-MM-DD]

| Member | Task | Status | Output | Time |
|--------|------|--------|--------|------|
| | | | | |

**Tổng kết ngày:**

---

<!-- Format: copy block trên cho mỗi ngày làm việc -->
