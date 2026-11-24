# Worklog — Team

> Ghi lại tất cả công việc đã làm theo ngày. Ai làm gì, kết quả gì.

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

## [2026-11-24]

| Member | Task | Status | Output | Time |
|--------|------|--------|--------|------|
| Notion AI | Triển khai quản lý quyền US01–US02 phía host | 🔄 WIP | Thêm tạo/sửa/bật tắt quy tắc, cấp/sửa/thu hồi quyền tạm; lưu file và dùng trong US12, không thay applet. Đã biên dịch bản sao host với đích Java 17, 27 kiểm tra quản lý quyền đạt trên môi trường độc lập. Hồi quy cũ 25/28 đạt; 3 test gateway phụ thuộc bộ công cụ Windows/simulator không chạy được trong môi trường này. Chưa kiểm thử UI thủ công, jcsl, 5 thẻ hoặc bảo mật thật; xem `docs/huong-dan-us01-us02.md`. | — |
| Vũ Quốc An & Notion AI | Kiểm thử US01–US02 trên host mô phỏng | 🔄 WIP | An xác nhận bộ test US01–US02 pass; thử UI tạo và tắt quy tắc, quyền tạm trước/đúng/sau biên, mở lại host và thu hồi đều cho kết quả mong đợi. Hồi quy US10–US13 trên máy An: 25/28 pass, TC-GW-04/05/06 fail; cần kiểm tra cấu hình và simulator khi tích hợp. Sau thu hồi, thông báo có thể nhắc quyền mẫu cũ hết hạn gây nhầm lẫn. Chưa xác minh thực thi chính sách trên thẻ; chờ review và merge. | — |

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
