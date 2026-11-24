# Kế hoạch triển khai US03–US04 và tích hợp ảnh

**Người phụ trách:** Vũ Quốc An · **Nhánh:** `feat/card-lifecycle-auth` · **Trạng thái:** Đã được An duyệt, chuẩn bị triển khai

## 1. Mục tiêu và phạm vi

| Hạng mục | Tiêu chí theo tài liệu phân công | Đầu ra dự kiến |
|---|---|---|
| US03 — Khóa, mở khóa, thu hồi thẻ | Đổi trạng thái thẻ ảo; thẻ bị khóa/thu hồi không sử dụng được | Thao tác quản lý, lưu trạng thái, kiểm thử hành vi liên quan |
| US04 — Đăng nhập và quản lý tài khoản | Đăng nhập/đăng xuất; chỉ tài khoản có quyền thấy chức năng quản trị | Phiên đăng nhập và phân quyền tối thiểu cho ứng dụng host |
| Ảnh trên thẻ | Yêu cầu bổ sung của giảng viên, không được tài liệu phân công ghi là một US riêng | Ghi/đọc và hiển thị ảnh trong giới hạn đã kiểm chứng, tích hợp vào luồng thẻ/hồ sơ phù hợp |

Ưu tiên hoàn thành US03 và US04 trước; không kéo dài tiến độ bằng các thử nghiệm tăng dung lượng ảnh. Tài liệu phân công có thể đã lỗi thời về tiến độ; tiêu chí trên là phạm vi gốc, không khẳng định trạng thái hiện tại của các thành viên khác.

## 2. Điểm xuất phát và giới hạn

- `main` đã có US01–US02 phía host; nhánh làm việc bắt đầu từ commit merge `00592cf`. US01–US02 đã được An thử trên host mô phỏng. Không mặc định quyền đã được thực thi trên thẻ.
- Host đã có các trạng thái `ACTIVE`, `LOCKED`, `REVOKED` và các luồng kiểm tra trạng thái. Applet cũng đã có lệnh đọc/đổi trạng thái và quy tắc không khôi phục thẻ đã thu hồi; gateway và giao diện mô phỏng đã có đường gọi tới `jcsl`. Chưa nghiệm thu toàn bộ luồng đồng bộ: UI hiện đổi trạng thái host trước khi gửi lệnh xuống simulator, nên cần xét trường hợp gửi thất bại và trạng thái hai phía lệch nhau. Cần bổ sung quản lý/lưu trạng thái phù hợp, không xây lại hệ thống thẻ US07.
- Ảnh giả 16.384 byte đã ghi và đọc lại đúng trên simulator. Các thử nghiệm 20, 24, 28 KiB thất bại; kết quả chưa xác định nguyên nhân chính xác hay chứng minh giới hạn phần cứng. Bản nhánh ảnh hiện là thử nghiệm, chưa phải chức năng hoàn chỉnh và chưa mã hóa ảnh.
- An chọn hoàn thành US03 phía host/thẻ ảo trước, rồi tích hợp và thử applet `jcsl` nếu không bị chặn. Trạng thái thực thi trên applet và độ bền dữ liệu card-side phải được xác minh riêng; nếu bị chặn, báo rõ phần chưa đạt, không mô tả kết quả mô phỏng là bảo mật hoặc khả năng trên thẻ thật.
- Lần hồi quy trước trên máy An: 25/28 test cũ đạt; `TC-GW-04`, `TC-GW-05`, `TC-GW-06` thất bại. Chưa xác định nguyên nhân; ghi nhận lại khi hồi quy, không tự mở rộng thành việc sửa US10–US13 nếu không cản trở tích hợp.

## 3. US03 — Quản lý trạng thái thẻ

### Triển khai

1. Dùng mô hình thẻ và trạng thái hiện có; bổ sung chức năng chọn thẻ, xem trạng thái và thao tác khóa, mở khóa, thu hồi.
2. Chuyển `ACTIVE → LOCKED`, `LOCKED → ACTIVE` và `ACTIVE/LOCKED → REVOKED`. **An đã chốt:** thẻ đã thu hồi không thể mở lại. Từ chối thao tác không hợp lệ và báo lý do rõ ràng.
3. Lưu trạng thái theo mã thẻ để giữ được sau khi đóng/mở host; đảm bảo các luồng đọc cùng trạng thái. Không ghi đè dữ liệu thẻ hoặc trạng thái chưa được xác nhận khi tích hợp US07.
4. Bảo vệ thao tác quản trị bằng quyền US04; không để màn hình mô phỏng hoặc đường xử lý khác trở thành cách vượt qua quy tắc quản lý.
5. Kiểm tra luồng chấm công US10 và kiểm soát ra vào US12 từ chối thẻ `LOCKED`/`REVOKED`; thẻ mở khóa hoạt động lại nếu các điều kiện khác hợp lệ.

### Kiểm thử/nghiệm thu

- [ ] Khóa và mở khóa thẻ; trạng thái hiển thị đúng và giữ sau khi mở lại host.
- [ ] Thu hồi thẻ; kiểm tra thao tác chuyển trạng thái không hợp lệ theo quy tắc đã chốt.
- [ ] US10/US12 từ chối thẻ bị khóa hoặc thu hồi; thẻ hoạt động lại sau mở khóa hợp lệ.
- [ ] Trạng thái của một thẻ không làm thay đổi thẻ khác; không làm mất dữ liệu quyền US01–US02.
- [ ] Nếu thử với `jcsl`, ghi rõ thao tác nào được thực thi ở host và thao tác nào thực sự được xác minh trên applet.

## 4. US04 — Đăng nhập và phân quyền host

### Triển khai

1. Tạo luồng đăng nhập/đăng xuất, quản lý phiên hiện tại và tối thiểu hai vai trò: quản trị và người dùng thông thường.
2. Chỉ hiển thị chức năng quản trị US01–US03 cho người có quyền; đồng thời kiểm tra quyền tại điểm xử lý nghiệp vụ, không chỉ ẩn nút hoặc tab. Đăng xuất phải xóa phiên và vô hiệu hóa thao tác cần quyền.
3. An chọn làm đầy đủ tạo, sửa, xóa và quản lý tài khoản nếu không xung đột với phần việc của thành viên khác. Trước khi triển khai, đối chiếu module/nhánh liên quan; nếu có giao thoa, giữ nguyên phần của bạn và báo rõ phương án phối hợp, không tự ý ghi đè.
4. Phân biệt tài khoản đăng nhập với hồ sơ nhân viên và ảnh thẻ; xác định cách khởi tạo tài khoản demo an toàn, không commit mật khẩu thật hoặc bí mật. Lưu mật khẩu dưới dạng băm có salt bằng thuật toán dẫn xuất phù hợp, không lưu bản rõ.

### Kiểm thử/nghiệm thu

- [ ] Đăng nhập đúng; từ chối sai tài khoản/mật khẩu, không tạo phiên hợp lệ.
- [ ] Quản trị viên sử dụng được các chức năng được cấp quyền.
- [ ] Tài khoản thường và người chưa đăng nhập không thấy hoặc không dùng được chức năng quản trị; gọi trực tiếp xử lý nghiệp vụ cũng bị từ chối.
- [ ] Đăng xuất rồi không tiếp tục được thao tác quản trị; thử đăng nhập lại bằng vai trò khác.
- [ ] Kiểm tra dữ liệu tài khoản và log không chứa mật khẩu bản rõ hoặc bí mật thật.

## 5. Ảnh trên thẻ — tích hợp sau US03–US04

1. Giữ `feat/photo-card` riêng để đối chiếu; lựa chọn phần phù hợp của thử nghiệm 16 KiB và tích hợp có chọn lọc, không merge nguyên mẫu thử 32 KiB. Giới hạn 16.384 byte ở cả host và applet khi triển khai.
2. Trên host: chọn ảnh, thu nhỏ/nén vừa giới hạn, báo lỗi nếu không xử lý được; ghi, đọc lại và hiển thị ảnh. Xác định định dạng và mức chất lượng vừa đủ cho demo.
3. An chọn luồng cấp/quản lý thẻ (US07), kết nối hồ sơ nhân viên sau. Thống nhất giao diện tích hợp với Hoàng và Nhi; không giả định đó là ảnh tài khoản đăng nhập US04, không làm lại chức năng của các bạn.
4. Thử dữ liệu giả hoặc ảnh không chứa thông tin cá nhân. Ảnh và đường truyền của nguyên mẫu chưa mã hóa; phối hợp phương án bảo vệ với phần US09 trước khi dùng dữ liệu thật. Không khẳng định bảo mật hay khả năng trên thẻ vật lý.
5. Kiểm tra ảnh vừa giới hạn, ảnh không hợp lệ/quá lớn, ghi lỗi/đọc lại và không ảnh hưởng dữ liệu khác; nếu cần tính bền vững thì kiểm tra sau khi khởi động lại simulator. Ghi lại kết quả thực tế, không yêu cầu thử thêm các mức lớn hơn 16 KiB.

## 6. Phân chia công việc để làm liền mạch

### Trước khi triển khai — An chốt một lượt

- [x] An đã trả lời khảo sát các điểm ở mục 7. Không yêu cầu An build, mở Eclipse hay chạy thử trước nếu không phát sinh trở ngại môi trường thực sự.
- [ ] Nếu việc kết nối US05/US07 cần quyết định của Nhi hoặc Hoàng, ghi rõ phần chờ; không bắt An liên hệ ngay khi vẫn có thể làm và kiểm thử phần độc lập.

### Triển khai liên tục — Notion AI thực hiện

1. [ ] Triển khai US03 phía host trước, tiếp đến US04 và các kiểm tra quyền; thử tích hợp applet US03 nếu không bị chặn.
2. [ ] Tích hợp có chọn lọc phần ảnh 16 KiB theo phương án đã chốt. Nếu module của nhóm chưa sẵn sàng, làm phần có thể kiểm thử độc lập và báo rõ phần tích hợp còn chờ.
3. [ ] Tự chạy các kiểm thử và hồi quy có thể thực hiện bằng công cụ được cấp; ghi kết quả, phần bị chặn và những gì chưa kiểm thử. Không nói đã chạy nếu phải nhờ An.
4. [ ] Chuẩn bị một bộ hướng dẫn chạy/test theo thứ tự, dữ liệu giả, kết quả mong đợi và cách gửi lại lỗi; cập nhật hướng dẫn, `WORKLOG.md`, nội dung bàn giao và chia commit hợp lý. Push nhánh khi có thể, không đưa cấu hình cá nhân hoặc dữ liệu thật vào commit.
5. [ ] Chỉ nhờ An giữa chừng nếu thực sự bị chặn bởi quyền truy cập, thao tác trên máy hoặc một quyết định không thể suy đoán; gom các việc cần hỗ trợ trong cùng một lượt nếu có thể.

### Sau khi có bản để review — An làm một lượt

- [ ] Theo hướng dẫn đã chuẩn bị, mở đúng host tại project gốc và chạy các test tự động còn cần máy An; ghi lại pass/fail, bao gồm ba test gateway nếu có chạy.
- [ ] Kiểm tra giao diện US03–US04 và hồi quy liên quan theo checklist ngắn. Nếu phần ảnh/simulator đã sẵn sàng và cần thao tác thủ công, build/cài applet và thử ảnh giả theo cùng checklist; không yêu cầu thử các mức lớn hơn 16 KiB.
- [ ] Gửi kết quả hoặc ảnh chụp lỗi một lần để xử lý; review thay đổi và tạo PR khi đạt. Nếu có lỗi cần sửa, có thể phải xác nhận lại phần bị ảnh hưởng; không coi việc review ban đầu là đã nghiệm thu.
- [ ] Chỉ đóng nhánh ảnh khi phần cần thiết đã được tích hợp, kiểm thử và có quyết định của nhóm.

## 7. Các điểm cần review/chốt

- **Đã chốt:** `REVOKED` là trạng thái cuối; không mở lại thẻ đã thu hồi.
- **Đã chọn có điều kiện:** US04 có đầy đủ quản lý tài khoản nếu không xung đột với phần việc của thành viên khác; cần đối chiếu module/nhánh liên quan.
- **Đã chọn:** gắn ảnh vào luồng cấp/quản lý thẻ US07, kết nối hồ sơ US05 sau; còn cần thống nhất giao diện với các thành viên liên quan.
- **Đã chốt phương án C:** hoàn thành US03 phía host trước; tích hợp và thử applet `jcsl` nếu không bị chặn. Code có đường đọc/ghi trạng thái card-side nhưng chưa nghiệm thu đồng bộ đầu-cuối; phải xử lý lệch trạng thái, xem xét xác thực lệnh và phối hợp giao diện chung. Nếu bị chặn, báo rõ phần applet còn chờ thay vì coi là đã nghiệm thu.
