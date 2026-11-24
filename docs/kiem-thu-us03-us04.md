# Kiểm thử US03–US04 (bản host đang phát triển)

Nhánh `feat/card-lifecycle-auth`. Chưa nghiệm thu, chưa merge. Chỉ dùng dữ liệu demo và mật khẩu thử riêng, không dùng mật khẩu thật. Đây là phân quyền ứng dụng host local, không chứng minh bảo mật thẻ thật. Chưa đồng bộ trạng thái host–jcsl; không dùng chế độ jcsl để kết luận US03 trên thẻ.

## Chuẩn bị

- Mở project host từ thư mục gốc `D:\Projects\EmployeeSmartCard`, không tạo workspace mới. Chạy cấu hình host hiện có; kiểm tra chế độ **Thẻ ảo nội bộ**. Nếu Eclipse chưa cập nhật, refresh/build project host.
- Dữ liệu nằm tại đường dẫn hiển thị trên ứng dụng (thường `host/data`). Không xóa hoặc sửa file thật để thử; `accounts.tsv` và `card_statuses.tsv` mới sẽ được lưu tại đó. Đừng đưa dữ liệu hoặc mật khẩu vào Git hay ảnh chụp.
- Thu hồi không thể hoàn tác: chỉ dùng một thẻ demo không còn cần. Không test thu hồi bằng thẻ cần tiếp tục sử dụng. Khóa có thể mở lại.

## Chạy kiểm thử tự động trong Eclipse

Trong project host, chạy từng class dưới `host/test/com/nhom11/smartcard/host/` bằng **Run As → Java Application**: `AccountServiceTest`, `CardLifecycleServiceTest`, `PolicyAuthorizationTest`, `AccessPolicyManagementTest`, `PhotoPreparationTest`, `PhotoTransferTest`, và `AttendanceAndAccessTest`. Gửi lại toàn bộ dòng tổng kết cùng lỗi nếu có. Ba test gateway GW-04/05/06 đã từng fail; không mặc định chúng đã được sửa. Test ảnh dùng dữ liệu sinh ra và APDU giả, không chứng minh đọc/ghi thẻ thật.

## Kiểm tra tài khoản và phân quyền

1. Tab **US04 · Tài khoản**: nếu là lần đầu, tạo quản trị viên đầu tiên với tên 3–40 ký tự chữ/số/dấu chấm/gạch và mật khẩu thử 8–128 ký tự; sau đó đăng nhập. Nếu tài khoản đã tồn tại, dùng tài khoản đã tạo. Không gửi mật khẩu.
2. Thử mật khẩu sai: không có phiên. Đăng nhập đúng bằng admin: tab US01–02 và các nút quản trị hiển thị. Tạo một tài khoản `USER`; sửa và xóa một tài khoản thử, xác nhận không xóa/hạ quyền quản trị viên cuối cùng được.
3. Đăng xuất rồi đăng nhập `USER`: tab US01–02 và thao tác quản trị trạng thái thẻ không được hiển thị. Đăng xuất; kiểm tra lại. Trên host, test trực tiếp lớp nghiệp vụ có trong `PolicyAuthorizationTest` và `CardLifecycleServiceTest`.

## Kiểm tra thẻ ảo

1. Đăng nhập admin, trong **US12 · Kiểm soát ra vào**, chọn một thẻ demo và đặt `LOCKED`, bấm **Áp dụng trạng thái thẻ**. Kiểm tra US10 và US12 từ chối thẻ này. Chọn thẻ khác, xác nhận trạng thái không thay đổi.
2. Đóng và mở lại host: thẻ vẫn `LOCKED`. Đăng nhập admin, chọn `ACTIVE` để mở; bật lại xác thực mô phỏng nếu cần và kiểm tra thẻ được phép dùng khi các điều kiện khác hợp lệ.
3. Với thẻ demo riêng không cần dùng lại, chọn `REVOKED`, xác nhận US10/US12 từ chối và không chuyển lại `ACTIVE`/`LOCKED` được, kể cả sau mở lại host. Quy tắc và quyền tạm US01–02 phải còn.
4. Khi jcsl đang kết nối, thay đổi trạng thái từ host hiện bị chặn để không giả vờ đồng bộ. Phần giao tiếp, xử lý lỗi và xác thực thay đổi trạng thái trên applet còn chờ; không thử trên thẻ thật.

## Ảnh

Chưa có tab ảnh hay giao diện cấp thẻ trong nhánh này. Đã chuẩn bị độc lập bộ giảm kích thước/nén JPEG tối đa 16.384 byte và giao thức thử round-trip; chưa nối giao diện, applet hoặc US07. Chưa có mã hóa; chưa dùng ảnh cá nhân. Nhánh `feat/photo-card` được giữ nguyên. Chờ thống nhất US07/US05 và kiểm thử simulator trước khi đóng việc ảnh.
