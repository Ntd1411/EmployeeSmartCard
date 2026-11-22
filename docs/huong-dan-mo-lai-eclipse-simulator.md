# Mở lại Eclipse và tiếp tục thử ảnh

Hướng dẫn theo máy đang dùng: Windows, Eclipse, Oracle Java Card Simulator 26.0 và nhánh `feat/photo-card`. Các đường dẫn có thể khác trên máy khác. Đây là bài thử, chỉ dùng ảnh giả; ảnh chưa được mã hóa và chưa dùng được cho dữ liệu thật.

## 1. Mở project và kiểm tra build

1. Mở Eclipse bằng workspace đã dùng. Trong Package Explorer, tìm `EmployeeSmartCard` và `EmployeeSmartCardHost`.
2. Kiểm tra đang ở nhánh `feat/photo-card`. Không tự pull, chuyển nhánh hoặc ghi đè thay đổi chưa commit.
3. Nếu chưa thấy host: **File → Import → Existing Projects into Workspace**, chọn `D:\Projects\EmployeeSmartCard\host`, chọn `EmployeeSmartCardHost`; không chọn **Copy projects into workspace**, rồi Finish. Nếu đã có project thì không import lại.
4. Chọn từng project và nhấn **F5 (Refresh)** sau khi code thay đổi bên ngoài Eclipse. Nếu **Project → Build Automatically** được chọn, Eclipse tự build; **Build Project** có thể bị mờ. Xem tab **Problems** để kiểm tra lỗi đỏ; warning không đồng nghĩa với lỗi build.
5. Khi cần build lại CAP, kiểm tra build/conversion thành công trong Build Console; có thể dùng **Project → Clean…** với `EmployeeSmartCard`. CAP dự kiến tại `D:\Projects\EmployeeSmartCard\deliverables\EmployeeSmartCard\com\nhom11\smartcard\javacard\smartcard.cap`. Không sửa tay file CAP.

## 2. Khởi động simulator

1. Trong **Java Card View → Java Card Simulators**, chọn `Java_Card_Simulator`. Nếu chưa chạy, chuột phải → **Start**; nếu đang chạy, không cần Start lại. Đợi console có `server ready on port #9025`.
2. Nếu không thấy console đúng: ở tab **Console**, dùng menu chọn console (biểu tượng màn hình) → **Java_Card_Simulator**. Menu chọn console khác với menu lệnh Connect / Disconnect / List applets.
3. Với lần cài hoặc kiểm tra applet qua console, nhập `connect;` một lần và đợi `Socket session established`. Nếu đang kết nối sẵn, không bấm Connect lại. `connect` thiếu dấu `;` có thể làm lệnh sau bị ghép sai.

### Trạng thái EEPROM

Cấu hình đã dùng từng có input `D:\Projects\EmployeeSmartCard\spikes\photo-card\simulator-eeprom.bin` và output `D:\Projects\EmployeeSmartCard\spikes\photo-card\simulator-eeprom-next.bin`. Xem lại **Properties** của simulator để biết cấu hình hiện tại; không giả định output đã tự trở thành input. Nếu muốn giữ trạng thái mới nhất sau khi dừng, kiểm tra output đã được tạo rồi chọn đúng file đó làm input cho lần chạy sau và đặt output khác. Nếu không cần giữ dữ liệu giả, có thể chạy trạng thái mới, nhưng sẽ phải cài applet lại. Không xóa `spikes` khi cấu hình còn trỏ tới nó; EEPROM có thể chứa nhiều applet và không đưa file này lên Git.

## 3. Applet: chọn bản đã cài hoặc cài lại khi cần

- Nếu chỉ mở lại và dùng trạng thái đã lưu, trong Simulator Console chạy `List;` để xem các applet. `EmployeeCardApplet` có applet AID `1122334455` (package AID `112233445511`). Nếu đã cài đúng phiên bản, không cần Load/Install lại; có thể `Select 1122334455;` để kiểm tra.
- Nếu applet chưa có (`List;` không liệt kê AID hoặc SELECT trả `6A82`), sau khi kết nối và build CAP, trong menu lệnh ▼ chọn **EmployeeSmartCard → CAP: EmployeeSmartCard → Load, Install, Select**. Kiểm tra cả ba bước đều thành công, không chỉ nhìn thấy menu. Khi lỗi, dừng và ghi lại log.
- **Sau khi thay code applet**, CAP mới không tự cập nhật instance đã cài. Cần thay phiên bản đã cài trên simulator: thao tác uninstall applet và unload package cũ rồi Load, Install, Select CAP mới. Việc này xóa trạng thái của instance bị gỡ, có thể ảnh hưởng các instance cùng package; chỉ thực hiện nếu chấp nhận mất dữ liệu thử và đã xác định ảnh hưởng. Nếu lỗi hoặc không rõ các instance đang có, dừng để kiểm tra, không nhấn lại tùy tiện. Không gỡ `PhotoCardSpike` nếu không cần.
- Bản thử **8192 byte** đã được build, cài và đọc lại đúng file giả 8192 byte trên simulator một thẻ theo log và ảnh người dùng. Nếu khởi động lại bằng EEPROM cũ, instance trong đó có thể vẫn là bản 4096 byte hoặc không có applet: kiểm tra trạng thái thực tế, không giả định là bản mới.

## 4. Chạy host và kết nối

1. Trước khi host kết nối, trong Simulator Console nhập `disconnect;` hoặc chọn **2 Disconnect** nếu console đang kết nối. Chỉ ngắt client console, **không Stop simulator**.
2. Chạy cấu hình Java Application `Run_Host_App_US10_US13` của project host. Nếu có hai cấu hình cùng tên, kiểm tra project trong **Run → Run Configurations…** thay vì đoán. Trên máy này, cấu hình cần VM argument:

   ```text
   -Djc.simulator.home=D:\Eclipse\java_card\simulator
   ```

   Đường dẫn chứa `client\COMService\socketprovider.jar`; nếu máy khác cài khác, điều chỉnh argument theo máy. Sau thay đổi host, đóng app đang chạy và chạy lại để dùng code mới.
3. Trong app, giữ host `127.0.0.1`, port `9025`, dùng PIN thử của môi trường phát triển và bấm **Kết nối jcsl**. Kiểm tra hiển thị **Simulator jcsl — 1 thẻ** và xác thực PIN thành công (`SW=9000`). Không đăng PIN vào log/chat.
4. Không bấm **Cài 5 thẻ lên jcsl** cho bài thử này. Vào tab **Thử ảnh (dữ liệu giả)**, chọn ảnh giả và xác nhận. Mỗi lần bắt đầu ghi sẽ làm ảnh cũ không đọc được, kể cả khi lỗi. Tab chỉ so sánh byte, không hiển thị ảnh.

## 5. Lỗi hay gặp

- `Session is disconnected`: console chưa kết nối; nhập `connect;` trước lệnh quản lý.
- `6A82` khi SELECT: applet AID không có trong trạng thái simulator đang nạp; kiểm tra `List;` và trạng thái EEPROM, rồi cài nếu cần.
- `6D00` khi gửi lệnh ảnh: applet nhận lệnh có thể không đúng hoặc chưa được chọn; kiểm tra AID và phiên bản.
- `6982`: cần xác thực PIN thử sau khi chọn applet; không thử đoán PIN.
- `Không tìm thấy socketprovider.jar`: kiểm tra VM argument và file `D:\Eclipse\java_card\simulator\client\COMService\socketprovider.jar`.
- Host không kết nối: kiểm tra simulator đang chạy, cổng 9025 và console đã disconnect; không đổi hay xóa EEPROM chỉ để xử lý lỗi kết nối.

## 6. Kết thúc buổi làm việc

Ngắt kết nối trong host, đóng host. Nếu cần giữ trạng thái simulator thì Stop simulator và kiểm tra file output EEPROM đã được tạo trước khi tắt Eclipse; input và output là hai mục riêng. Nếu dữ liệu đều là giả và không cần giữ thì không cần sao lưu. Nhớ ghi lại kết quả thử và các thay đổi chưa commit.
