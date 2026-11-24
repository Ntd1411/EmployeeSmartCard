# US01–US02 — Quản lý quyền truy cập mô phỏng

## Phạm vi và giới hạn

- Quy tắc và quyền tạm được quản lý **trên host**, áp dụng cho màn hình US12; nhân viên, thẻ và khu vực hiện vẫn là dữ liệu mẫu từ nhóm. Không dùng thông tin nhân viên thật.
- Đây không phải cơ chế an toàn thực tế: chưa có đăng nhập/phân quyền quản trị US04, file quyền không mã hóa (Base64 chỉ là mã hóa biểu diễn), giờ do máy host cung cấp, và chưa đồng bộ/kiểm chứng các chính sách xuống applet.
- Ở chế độ jcsl hiện tại, gateway kiểm tra trạng thái/PIN của thẻ nhưng chỉ ghi bitmask ALLOW/DENY vào thông báo rồi trả eligible nếu APDU thành công. Quyết định chính sách của demo là ở host; không tuyên bố thẻ thực thi quyền. Chưa sửa hành vi gateway hoặc APDU trên nhánh này.
- Thẻ khóa/thu hồi và các kiểm tra bảo mật đang bật được xét trước quyền. Quy tắc cố định được xét trước quyền tạm: quyền tạm hết hạn không vô hiệu hóa quyền cố định khác.

## Sử dụng và nghiệm thu thủ công

1. Mở project `EmployeeSmartCardHost` của nhánh này trong Eclipse (dùng workspace riêng nếu project tên trùng với bản ảnh). Chạy cấu hình host `Run_Host_App_US10_US13`; chưa cần simulator, để chế độ thẻ ảo nội bộ.
2. Tab **US01–02 · Quản lý quyền** → **US01 · Quy tắc cố định** → tạo quy tắc, chọn khu vực, nhập phòng ban hoặc chức vụ, chọn AND/OR khi có cả hai, chọn trạng thái bật. Chọn dòng rồi sửa/tắt quy tắc.
3. Sang **US12 · Kiểm soát ra vào** chọn thẻ, khu vực và thời điểm để thử cho phép/từ chối; chọn nhân viên có thẻ ACTIVE và thỏa các kiểm tra bảo mật nếu đang bật.
4. Trong **US02 · Quyền tạm thời**, cấp quyền cho nhân viên và khu vực, nhập thời gian theo `yyyy-MM-dd HH:mm` hoặc `yyyy-MM-dd HH:mm:ss`, ví dụ `2026-11-24 09:00`. Thời gian dùng giờ địa phương của host và **bao gồm cả hai đầu mốc**. Chọn dòng để sửa hoặc thu hồi; quyền đã thu hồi không mở lại được, hãy cấp quyền mới.
5. Trong US12, thử trước, đúng đầu, đúng cuối, sau cuối thời hạn. Thử khi có/không có quyền cố định khác và với thẻ khóa/thu hồi. Thay đổi được lưu ngay; đóng và mở lại host để xác minh. Nút **Nạp lại dữ liệu mẫu** chỉ khôi phục dữ liệu chấm công/nhật ký và mẫu nhân viên/thẻ; không xóa các quyền đã lưu.
6. Chạy `Run_Tests_US01_US02.launch` và `Run_Tests_US10_US13.launch` trong Eclipse. Kiểm tra tiếp jcsl, 5 thẻ, UI thực tế và hồi quy trước khi merge. Chưa có kiểm thử GUI tự động hay kiểm thử simulator cho nhánh này.

## Dữ liệu và xử lý lỗi

File là `host/data/access_policies.tsv` khi chạy từ gốc repo, hoặc `data/access_policies.tsv` khi chạy với working directory là `host/`. Đường dẫn được hiển thị ở đầu ứng dụng. Dữ liệu `host/data/` đã được `.gitignore` bỏ qua. Lần đầu chưa có file, ứng dụng tạo một file từ quyền mẫu hiện có; các lần sau nạp file đã lưu, không tự thay bằng mẫu. Nếu file hỏng hoặc không đọc được, ứng dụng báo lỗi và dừng thay vì ghi đè; sao lưu file và kiểm tra thủ công. Chỉ chạy một host cho cùng thư mục dữ liệu: chưa có khóa liên tiến trình.
