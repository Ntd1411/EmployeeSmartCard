# Phân công công việc — Nhóm 11

**Đề tài:** Xây dựng hệ thống thẻ nhân viên và chấm công thông minh

---

## 1. Tổng quan dự án, nguyên tắc và phạm vi

### Yêu cầu chính của hệ thống
- Xác thực nhân viên bằng mã PIN.
- Lưu trữ thông tin nhân viên gồm mã nhân viên, họ tên, phòng ban, chức vụ.
- Ghi nhận thời gian vào ca, ra ca và thời gian làm việc.
- Quản lý quyền ra vào các khu vực theo chức vụ hoặc phòng ban.
- Hỗ trợ cấp quyền truy cập tạm thời.
- Cho phép khóa hoặc thu hồi quyền sử dụng thẻ khi nhân viên nghỉ việc.
- Sử dụng AES và cơ chế xác thực mật mã để chống giả mạo thẻ.
- Xây dựng phần mềm chấm công, quản lý nhân viên và xuất báo cáo.

### Nguyên tắc làm việc
- **Mỗi US một người sở hữu:** Mỗi User Story do một thành viên chịu trách nhiệm chính từ đầu đến cuối.
- **Tự làm chức năng đầu-cuối:** Tự thiết kế dữ liệu, xử lý nghiệp vụ, giao diện và kiểm thử phần mình phụ trách.
- **Không chia frontend/backend:** Không tách biệt nhân sự chuyên frontend/backend; mỗi người tự hoàn thiện trọn vẹn US được giao.
- **Thống nhất cấu trúc dữ liệu chung:** Cả nhóm thống nhất mô hình dữ liệu nhân viên, thẻ ảo, quyền truy cập và log chấm công ngay từ giai đoạn chuẩn bị.
- **Tích hợp chung cuối mỗi tuần:** Ghép nối, kiểm thử liên thông các module vào cuối Tuần 1 và cuối Tuần 2.

### Phạm vi mô phỏng
- Thẻ ảo trên máy tính.
- Không có thẻ thật/NFC.
- Không yêu cầu đầu đọc.
- AES/RSA chỉ trong mô phỏng.
- Không khẳng định mức bảo mật phần cứng.

### Quy ước Báo cáo Word (`.docx`)
- **Phạm Hoa Nhi** chủ trì báo cáo Word (`.docx`), tổng hợp và định dạng toàn bộ tài liệu.
- Nhi đảm nhận **8 SP** (thay vì ~13–16 SP) để cân đối khối lượng làm báo cáo cho cả nhóm.
- **An, Hoàng, Dũng** chịu trách nhiệm cung cấp đầy đủ nội dung kỹ thuật, mô tả triển khai, ảnh chụp màn hình và kết quả kiểm thử phần US của mình.
- **Nhi** tổng hợp ảnh, sơ đồ, kết quả từ các thành viên và hoàn thiện báo cáo **trước ngày 14**.

---

## 2. Tổng hợp khối lượng theo thành viên

| MSSV | Thành viên | Số US | SP Tuần 1 | SP Tuần 2 | Tổng SP | Công việc ngoài US | Vai trò trong dự án |
|---|---|---:|---:|---:|---:|---|---|
| `CT070201` | Vũ Quốc An | 4 | 8 | 8 | 16 | Viết nội dung kỹ thuật phần mình; kiểm thử tích hợp | Chịu trách nhiệm trọn vẹn các US được giao |
| `CT070236` | Phạm Hoa Nhi | 2 | 5 | 3 | 8 | Báo cáo Word (`.docx`), tổng hợp và định dạng | Chịu trách nhiệm trọn vẹn các US được giao |
| `CT070221` | Nguyễn Huy Hoàng | 3 | 8 | 5 | 13 | Viết nội dung kỹ thuật phần mình; kiểm thử tích hợp | Chịu trách nhiệm trọn vẹn các US được giao |
| `CT070212` | Nguyễn Tuấn Dũng | 4 | 5 | 11 | 16 | Viết nội dung kỹ thuật phần mình; kiểm thử tích hợp | Chịu trách nhiệm trọn vẹn các US được giao |
| **Tổng cộng** | — | **13** | **26** | **27** | **53** | — | — |

---

## 3. Danh sách đầy đủ 13 User Story (Master Backlog)

| ID | User Story | Người phụ trách | MSSV | SP | Sprint | Tiêu chí nghiệm thu | Trạng thái |
|---|---|---|---:|---:|---|---|---|
| `US01` | Quản lý quyền truy cập theo phòng ban, chức vụ | Vũ Quốc An | CT070201 | 5 | Tuần 1 | Tạo/sửa quy tắc quyền theo khu vực, phòng ban, chức vụ; lưu và áp dụng được cho nhân viên | Chưa bắt đầu |
| `US02` | Cấp và quản lý quyền truy cập tạm thời | Vũ Quốc An | CT070201 | 5 | Tuần 2 | Cấp quyền có ngày bắt đầu/kết thúc; quyền hết hạn không còn hiệu lực | Chưa bắt đầu |
| `US03` | Khóa, mở khóa và thu hồi thẻ | Vũ Quốc An | CT070201 | 3 | Tuần 2 | Đổi trạng thái thẻ ảo; thẻ bị khóa/thu hồi không sử dụng được | Chưa bắt đầu |
| `US04` | Đăng nhập và quản lý tài khoản | Vũ Quốc An | CT070201 | 3 | Tuần 1 | Đăng nhập/đăng xuất; chức năng quản trị chỉ hiện cho tài khoản có quyền | Chưa bắt đầu |
| `US05` | Quản lý hồ sơ nhân viên (thêm, sửa, xóa, tìm kiếm) | Phạm Hoa Nhi | CT070236 | 5 | Tuần 1 | CRUD nhân viên và tìm kiếm; thông tin hiển thị thống nhất | Chưa bắt đầu |
| `US06` | Xem lịch sử chấm công và xuất báo cáo | Phạm Hoa Nhi | CT070236 | 3 | Tuần 2 | Xem/lọc lịch sử theo nhân viên/ngày; xuất bảng dữ liệu CSV | Chưa bắt đầu |
| `US07` | Tạo và quản lý thẻ nhân viên mô phỏng | Nguyễn Huy Hoàng | CT070221 | 5 | Tuần 1 | Tạo thẻ ảo gắn nhân viên, hiển thị mã thẻ và trạng thái; không dùng phần cứng | Chưa bắt đầu |
| `US08` | Xác thực thẻ bằng mã PIN | Nguyễn Huy Hoàng | CT070221 | 3 | Tuần 1 | Nhập PIN để xác thực thẻ ảo; từ chối khi PIN sai | Chưa bắt đầu |
| `US09` | Mô phỏng bảo mật thẻ bằng AES và RSA | Nguyễn Huy Hoàng | CT070221 | 5 | Tuần 2 | Mã hóa/xác minh bằng AES và RSA trong mô phỏng; phát hiện dữ liệu sai hoặc giả mạo theo kịch bản thử nghiệm | Chưa bắt đầu |
| `US10` | Chấm công vào ca, ra ca | Nguyễn Tuấn Dũng | CT070212 | 5 | Tuần 1 | Ghi check-in/check-out hợp lệ và lưu thời điểm vào hệ thống | Chưa bắt đầu |
| `US11` | Tự động tính thời gian làm việc | Nguyễn Tuấn Dũng | CT070212 | 3 | Tuần 2 | Tính số giờ từ cặp vào-ra và xử lý ca thiếu dữ liệu | Chưa bắt đầu |
| `US12` | Mô phỏng kiểm soát ra vào khu vực | Nguyễn Tuấn Dũng | CT070212 | 5 | Tuần 2 | Nhập/chọn thẻ ảo, khu vực, thời điểm; trả kết quả cho phép/từ chối theo quyền và trạng thái thẻ | Chưa bắt đầu |
| `US13` | Thống kê dữ liệu chấm công | Nguyễn Tuấn Dũng | CT070212 | 3 | Tuần 2 | Tổng hợp số lần chấm công, ngày công/giờ làm theo nhân viên, khoảng ngày | Chưa bắt đầu |
| **Tổng** | — | — | — | **53** | — | — | — |

---

## 4. Phân công chi tiết theo từng thành viên

### 4.1. Vũ Quốc An (`CT070201`) — 4 US | 16 SP (Tuần 1: 8 SP · Tuần 2: 8 SP)

**Trọng tâm:** Phân quyền hệ thống, quy tắc truy cập khu vực (cố định & tạm thời), quản lý trạng thái khóa/thu hồi thẻ và kiểm thử tích hợp.

#### Danh sách User Story được giao

**Tuần 1 (Sprint 1 — 8 SP):**
- [ ] **`US01` — Quản lý quyền truy cập theo phòng ban, chức vụ (`5 SP`)**
  - *Tiêu chí nghiệm thu:* Tạo/sửa quy tắc quyền theo khu vực, phòng ban, chức vụ; lưu và áp dụng được cho nhân viên.
- [ ] **`US04` — Đăng nhập và quản lý tài khoản (`3 SP`)**
  - *Tiêu chí nghiệm thu:* Đăng nhập/đăng xuất; chức năng quản trị chỉ hiện cho tài khoản có quyền.

**Tuần 2 (Sprint 2 — 8 SP):**
- [ ] **`US02` — Cấp và quản lý quyền truy cập tạm thời (`5 SP`)**
  - *Tiêu chí nghiệm thu:* Cấp quyền có ngày bắt đầu/kết thúc; quyền hết hạn không còn hiệu lực.
- [ ] **`US03` — Khóa, mở khóa và thu hồi thẻ (`3 SP`)**
  - *Tiêu chí nghiệm thu:* Đổi trạng thái thẻ ảo; thẻ bị khóa/thu hồi không sử dụng được.

#### Lịch trình 14 ngày
| Giai đoạn | Ngày | Công việc cụ thể |
|---|---|---|
| Chuẩn bị | 1–2 | Thống nhất dữ liệu; khởi tạo `US01`, `US04` |
| Sprint 1 | 3–7 | Hoàn thành `US01`, `US04` |
| Sprint 2A | 8–10 | Hoàn thành `US02`, `US03` |
| Sprint 2B | 11–12 | Test quyền và trạng thái thẻ |
| Hoàn tất | 13–14 | Tích hợp, kiểm thử, trình diễn |

#### Công việc ngoài US, phối hợp & Báo cáo Word
- **Điểm giao tiếp dữ liệu:**
  - Sử dụng thông tin phòng ban, chức vụ từ hồ sơ nhân viên (`US05` — Nhi) để thiết lập và áp dụng quy tắc quyền ở `US01`, `US02`.
  - Đồng bộ trạng thái thẻ ảo (`US03`) với module quản lý thẻ của Hoàng (`US07`) để đảm bảo thẻ bị khóa/thu hồi không vượt qua bước xác thực.
  - Cung cấp quy tắc quyền (`US01`, `US02`) và trạng thái thẻ (`US03`) cho module kiểm soát ra vào khu vực của Dũng (`US12`).
- **Đóng góp Báo cáo Word & kiểm thử:**
  - Hỗ trợ Nhi phần *Giới thiệu đề tài, yêu cầu và phạm vi mô phỏng* (hạn **Ngày 5**).
  - Đóng góp phần *Kiến trúc & thiết kế dữ liệu* (hạn **Ngày 8**), nội dung kỹ thuật và ảnh chụp màn hình cho `US01`–`US04` (hạn **Ngày 11**), kết quả kiểm thử quyền & trạng thái thẻ (hạn **Ngày 12**), và rà soát báo cáo hoàn chỉnh (hạn **Ngày 14**).

---

### 4.2. Phạm Hoa Nhi (`CT070236`) — 2 US | 8 SP (Tuần 1: 5 SP · Tuần 2: 3 SP)

**Trọng tâm:** Quản lý hồ sơ nhân viên, xem/xuất báo cáo chấm công CSV và chủ trì biên soạn, tổng hợp, định dạng Báo cáo Word (`.docx`) cho cả nhóm.

#### Danh sách User Story được giao

**Tuần 1 (Sprint 1 — 5 SP):**
- [ ] **`US05` — Quản lý hồ sơ nhân viên (thêm, sửa, xóa, tìm kiếm) (`5 SP`)**
  - *Tiêu chí nghiệm thu:* CRUD nhân viên và tìm kiếm; thông tin hiển thị thống nhất.

**Tuần 2 (Sprint 2 — 3 SP):**
- [ ] **`US06` — Xem lịch sử chấm công và xuất báo cáo (`3 SP`)**
  - *Tiêu chí nghiệm thu:* Xem/lọc lịch sử theo nhân viên/ngày; xuất bảng dữ liệu CSV.

#### Lịch trình 14 ngày
| Giai đoạn | Ngày | Công việc cụ thể |
|---|---|---|
| Chuẩn bị | 1–2 | Thiết kế dữ liệu nhân viên, `US05`; đề cương Word |
| Sprint 1 | 3–7 | Hoàn thành `US05`; viết báo cáo phần tổng quan |
| Sprint 2A | 8–10 | Hoàn thành `US06`; cập nhật Word |
| Sprint 2B | 11–12 | Kiểm thử dữ liệu, xuất CSV; chèn ảnh Word |
| Hoàn tất | 13–14 | Hoàn thành báo cáo Word và rà soát trình bày |

#### Công việc ngoài US, phối hợp & Báo cáo Word
- **Điểm giao tiếp dữ liệu:**
  - Định nghĩa và cung cấp cấu trúc dữ liệu nhân viên chuẩn (mã nhân viên, họ tên, phòng ban, chức vụ từ `US05`) làm gốc cho `US01` (An), `US07` (Hoàng) và `US10` (Dũng).
  - Đọc dữ liệu chấm công và thời gian làm việc từ `US10`, `US11` (Dũng) để phục vụ lọc lịch sử và xuất CSV ở `US06`.
- **Chủ trì Báo cáo Word (`.docx`):**
  - Tạo đề cương, mục lục, định dạng mẫu `.docx` (hạn **Ngày 2**).
  - Viết phần giới thiệu đề tài, yêu cầu và phạm vi mô phỏng cùng An (hạn **Ngày 5**).
  - Tổng hợp sơ đồ kiến trúc, thiết kế dữ liệu (hạn **Ngày 8**), ghép nội dung triển khai 13 US (hạn **Ngày 11**), tổng hợp kết quả kiểm thử/ảnh minh họa (hạn **Ngày 12**), và hoàn thiện toàn bộ file `.docx` (hạn **Ngày 14**).

---

### 4.3. Nguyễn Huy Hoàng (`CT070221`) — 3 US | 13 SP (Tuần 1: 8 SP · Tuần 2: 5 SP)

**Trọng tâm:** Mô phỏng thẻ nhân viên ảo, xác thực mã PIN, cơ chế bảo mật AES/RSA chống giả mạo và kiểm thử bảo mật tích hợp.

#### Danh sách User Story được giao

**Tuần 1 (Sprint 1 — 8 SP):**
- [ ] **`US07` — Tạo và quản lý thẻ nhân viên mô phỏng (`5 SP`)**
  - *Tiêu chí nghiệm thu:* Tạo thẻ ảo gắn nhân viên, hiển thị mã thẻ và trạng thái; không dùng phần cứng.
- [ ] **`US08` — Xác thực thẻ bằng mã PIN (`3 SP`)**
  - *Tiêu chí nghiệm thu:* Nhập PIN để xác thực thẻ ảo; từ chối khi PIN sai.

**Tuần 2 (Sprint 2 — 5 SP):**
- [ ] **`US09` — Mô phỏng bảo mật thẻ bằng AES và RSA (`5 SP`)**
  - *Tiêu chí nghiệm thu:* Mã hóa/xác minh bằng AES và RSA trong mô phỏng; phát hiện dữ liệu sai hoặc giả mạo theo kịch bản thử nghiệm.

#### Lịch trình 14 ngày
| Giai đoạn | Ngày | Công việc cụ thể |
|---|---|---|
| Chuẩn bị | 1–2 | Đặc tả thẻ ảo, `US07`, `US08` |
| Sprint 1 | 3–7 | Hoàn thành `US07`, `US08` |
| Sprint 2A | 8–10 | Hoàn thành `US09` |
| Sprint 2B | 11–12 | Test PIN, mã hóa, chống giả mạo |
| Hoàn tất | 13–14 | Hỗ trợ kiểm thử tích hợp |

#### Công việc ngoài US, phối hợp & Báo cáo Word
- **Điểm giao tiếp dữ liệu:**
  - Gắn thẻ ảo (`US07`) với dữ liệu nhân viên từ `US05` (Nhi) và thống nhất trường trạng thái thẻ với `US03` (An).
  - Cung cấp thẻ ảo đã xác thực PIN (`US08`) và xác minh chữ ký/mã hóa AES–RSA (`US09`) làm đầu vào tin cậy cho các luồng chấm công (`US10` — Dũng) và kiểm soát ra vào khu vực (`US12` — Dũng).
- **Đóng góp Báo cáo Word & kiểm thử:**
  - Đóng góp đặc tả thẻ ảo và kiến trúc bảo mật mô phỏng (hạn **Ngày 8**).
  - Cung cấp nội dung kỹ thuật, quy trình mã hóa/xác thực và ảnh chụp màn hình cho `US07`–`US09` (hạn **Ngày 11**).
  - Bàn giao test case, kịch bản giả mạo và kết quả kiểm thử PIN/AES/RSA (hạn **Ngày 12**); hỗ trợ kiểm thử tích hợp và rà soát báo cáo (Ngày 13–14).

---

### 4.4. Nguyễn Tuấn Dũng (`CT070212`) — 4 US | 16 SP (Tuần 1: 5 SP · Tuần 2: 11 SP)

**Trọng tâm:** Nghiệp vụ chấm công vào/ra ca, tự động tính giờ làm việc, mô phỏng kiểm soát ra vào khu vực và thống kê dữ liệu chấm công.

#### Danh sách User Story được giao

**Tuần 1 (Sprint 1 — 5 SP):**
- [ ] **`US10` — Chấm công vào ca, ra ca (`5 SP`)**
  - *Tiêu chí nghiệm thu:* Ghi check-in/check-out hợp lệ và lưu thời điểm vào hệ thống.

**Tuần 2 (Sprint 2 — 11 SP):**
- [ ] **`US11` — Tự động tính thời gian làm việc (`3 SP`)**
  - *Tiêu chí nghiệm thu:* Tính số giờ từ cặp vào-ra và xử lý ca thiếu dữ liệu.
- [ ] **`US12` — Mô phỏng kiểm soát ra vào khu vực (`5 SP`)**
  - *Tiêu chí nghiệm thu:* Nhập/chọn thẻ ảo, khu vực, thời điểm; trả kết quả cho phép/từ chối theo quyền và trạng thái thẻ.
- [ ] **`US13` — Thống kê dữ liệu chấm công (`3 SP`)**
  - *Tiêu chí nghiệm thu:* Tổng hợp số lần chấm công, ngày công/giờ làm theo nhân viên, khoảng ngày.

#### Lịch trình 14 ngày
| Giai đoạn | Ngày | Công việc cụ thể |
|---|---|---|
| Chuẩn bị | 1–2 | Đặc tả dữ liệu chấm công, `US10` |
| Sprint 1 | 3–7 | Hoàn thành `US10`; demo chấm công |
| Sprint 2A | 8–10 | Hoàn thành `US11`, `US13` |
| Sprint 2B | 11–12 | Hoàn thành và kiểm thử `US12` |
| Hoàn tất | 13–14 | Hỗ trợ kiểm thử và xử lý lỗi tích hợp |

#### Công việc ngoài US, phối hợp & Báo cáo Word
- **Điểm giao tiếp dữ liệu:**
  - Sử dụng thông tin nhân viên từ `US05` (Nhi) và thẻ ảo từ `US07`–`US08` (Hoàng) khi ghi nhận check-in/check-out ở `US10`.
  - Cung cấp cấu trúc dữ liệu chấm công và kết quả tính giờ (`US10`, `US11`) cho chức năng xem lịch sử và xuất CSV của Nhi (`US06`).
  - Tích hợp kiểm tra quyền truy cập cố định (`US01`), quyền tạm thời (`US02`), trạng thái khóa/thu hồi (`US03` — An) và thẻ ảo (`US07` — Hoàng) trong mô phỏng kiểm soát ra vào (`US12`).
- **Đóng góp Báo cáo Word & kiểm thử:**
  - Đóng góp đặc tả dữ liệu chấm công và thiết kế nghiệp vụ vào phần *Kiến trúc & thiết kế dữ liệu* (hạn **Ngày 8**).
  - Cung cấp mô tả kỹ thuật, ảnh minh họa cho `US10`, `US11`, `US12`, `US13` (hạn **Ngày 11**) và kết quả kiểm thử luồng chấm công/ra vào khu vực (hạn **Ngày 12**).
  - Hỗ trợ kiểm thử và xử lý lỗi tích hợp toàn hệ thống trong ngày 13–14.

---

## 5. Kế hoạch triển khai theo Sprint & Lộ trình 14 ngày

### Phân bổ theo Sprint
- **Sprint 1 (Tuần 1 — 6 US, `26 SP`):** `US01` (5), `US04` (3), `US05` (5), `US07` (5), `US08` (3), `US10` (5). Tập trung hoàn thiện nền tảng dữ liệu nhân viên, tài khoản, quy tắc quyền cơ bản, thẻ ảo, xác thực PIN và chấm công vào/ra ca.
- **Sprint 2 (Tuần 2 — 7 US, `27 SP`):** `US02` (5), `US03` (3), `US06` (3), `US09` (5), `US11` (3), `US12` (5), `US13` (3). Tập trung hoàn thiện quyền tạm thời, khóa/thu hồi thẻ, bảo mật mô phỏng AES/RSA, tính giờ/thống kê chấm công, kiểm soát khu vực và xuất CSV.

### Lộ trình chi tiết 14 ngày

| Giai đoạn | Ngày | Vũ Quốc An | Phạm Hoa Nhi | Nguyễn Huy Hoàng | Nguyễn Tuấn Dũng |
|---|---|---|---|---|---|
| Chuẩn bị | 1–2 | Thống nhất dữ liệu; khởi tạo `US01`, `US04` | Thiết kế dữ liệu nhân viên, `US05`; đề cương Word | Đặc tả thẻ ảo, `US07`, `US08` | Đặc tả dữ liệu chấm công, `US10` |
| Sprint 1 | 3–7 | Hoàn thành `US01`, `US04` | Hoàn thành `US05`; viết báo cáo phần tổng quan | Hoàn thành `US07`, `US08` | Hoàn thành `US10`; demo chấm công |
| Sprint 2A | 8–10 | Hoàn thành `US02`, `US03` | Hoàn thành `US06`; cập nhật Word | Hoàn thành `US09` | Hoàn thành `US11`, `US13` |
| Sprint 2B | 11–12 | Test quyền và trạng thái thẻ | Kiểm thử dữ liệu, xuất CSV; chèn ảnh Word | Test PIN, mã hóa, chống giả mạo | Hoàn thành và kiểm thử `US12` |
| Hoàn tất | 13–14 | Tích hợp, kiểm thử, trình diễn | Hoàn thành báo cáo Word và rà soát trình bày | Hỗ trợ kiểm thử tích hợp | Hỗ trợ kiểm thử và xử lý lỗi tích hợp |

---

## 6. Kế hoạch hoàn thiện Báo cáo Word (`.docx`)

| Hạng mục | Người phụ trách | Người hỗ trợ | Hạn hoàn thành | Đầu ra mong đợi | Trạng thái |
|---|---|---|---|---|---|
| Tạo đề cương, mục lục, định dạng báo cáo | Phạm Hoa Nhi | Cả nhóm | Ngày 2 | File `.docx` đúng mẫu, đầy đủ mục chính | Chưa bắt đầu |
| Giới thiệu đề tài, yêu cầu và phạm vi mô phỏng | Phạm Hoa Nhi | Vũ Quốc An | Ngày 5 | Mục tiêu, yêu cầu, phạm vi không có phần cứng | Chưa bắt đầu |
| Kiến trúc & thiết kế dữ liệu | Phạm Hoa Nhi | Cả nhóm | Ngày 8 | Sơ đồ tổng quan, thiết kế dữ liệu theo chức năng | Chưa bắt đầu |
| Mô tả triển khai 13 User Story | Phạm Hoa Nhi | Mỗi thành viên viết phần US của mình | Ngày 11 | Ảnh chụp màn hình, quy trình, mô tả triển khai | Chưa bắt đầu |
| Kiểm thử & kết quả minh họa | Phạm Hoa Nhi | Cả nhóm | Ngày 12 | Test case, kết quả, ảnh minh họa | Chưa bắt đầu |
| Kết luận, tài liệu tham khảo, rà soát Word | Phạm Hoa Nhi | Cả nhóm | Ngày 14 | Báo cáo `.docx` hoàn chỉnh | Chưa bắt đầu |

### Checklist bàn giao tài liệu cho Nhi
- [ ] **Ngày 2:** Cả nhóm thống nhất cấu trúc dữ liệu chung và phản hồi đề cương/mục lục file `.docx`.
- [ ] **Ngày 5:** Vũ Quốc An phối hợp cùng Nhi hoàn thiện nội dung *Giới thiệu đề tài, yêu cầu và phạm vi mô phỏng*.
- [ ] **Ngày 8:** An, Hoàng, Dũng gửi phần thiết kế dữ liệu/kiến trúc thuộc phạm vi chức năng của mình để Nhi tổng hợp sơ đồ.
- [ ] **Ngày 11:** Mỗi thành viên bàn giao đầy đủ mô tả triển khai, quy trình và ảnh chụp màn hình rõ nét cho các US phụ trách.
- [ ] **Ngày 12:** An, Hoàng, Dũng bàn giao test case, kịch bản thử nghiệm và kết quả kiểm thử minh họa (quyền, trạng thái thẻ, PIN, AES/RSA, chấm công, ra vào khu vực).
- [ ] **Trước ngày 14:** Cả nhóm rà soát lần cuối cùng Nhi để chốt báo cáo `.docx` hoàn chỉnh đúng hạn.
