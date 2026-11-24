# Thử lưu ảnh trong EmployeeCardApplet

Bản thử trên nhánh phát triển, chưa dùng cho sản phẩm hoặc dữ liệu nhân viên thật. Applet và host của nhóm chưa được kiểm thử hồi quy sau thay đổi. Không đưa vào main cho đến khi đã review và kiểm thử.

## Giới hạn và an toàn

**Bản thử mới đang chuẩn bị:** giới hạn được nâng lên 16384 byte (16 KiB) trên host và applet; chưa xác minh CAP, cài đặt hoặc ghi/đọc trên simulator. Số liệu 8192 byte bên dưới là kết quả đã quan sát của bản trước. Không tự nạp đè trạng thái simulator đang dùng.

- Bản trước đặt bộ đệm 8192 byte mỗi instance và theo log người dùng đã đọc lại đúng file giả 8192 byte trên simulator một thẻ. Bản 16 KiB mới cấp 16384 byte mỗi instance: nếu cài được 5 instance thì riêng bộ đệm ảnh chiếm 80 KiB, chưa kể đối tượng khác. Chưa xác định có đủ bộ nhớ trên simulator hoặc thẻ.
- Thẻ phải ở trạng thái ACTIVE và PIN đã được xác thực cho mọi lệnh ảnh. Deselect sẽ xóa trạng thái xác thực PIN. Bản thử **không mã hóa dữ liệu hoặc đường truyền**, chưa xác thực mật mã và không phù hợp dữ liệu thật.
- Bắt đầu ghi sẽ làm ảnh cũ không còn đọc được; chưa có cơ chế giữ nguyên ảnh cũ khi ghi lỗi hoặc mất điện. Chỉ dữ liệu đã FINISH thành công mới được đọc.
- Host có tab thử file giả trong chế độ một instance. Theo ảnh người dùng, các file PNG giả 3172 và 4096 byte đã được ghi và đọc lại đúng qua simulator với giới hạn cũ; file 4097 byte bị host cũ từ chối. Sau đó file giả 8192 byte cũng đọc lại đúng với bản mới. Không thay đổi AID hay ghi đè, gỡ cài đặt applet đang được sử dụng khi chưa xác định ảnh hưởng và sao lưu simulator.

## APDU thử nghiệm

CLA `80`; P1/P2 cho độ dài hoặc offset big-endian. Tất cả lệnh ảnh yêu cầu PIN đã xác thực và trạng thái thẻ ACTIVE.

| INS | P1/P2 | Dữ liệu | Tác dụng |
|---|---|---|---|
| `10` | Tổng số byte | Không | Bắt đầu ghi, 1–16384 byte trong bản thử mới |
| `11` | Offset ghi tiếp theo | Các byte, Lc là độ dài | Ghi nối tiếp |
| `12` | `0000` | Không | Hoàn tất nếu đã nhận đủ |
| `13` | Offset đọc | Không; Le là độ dài mong muốn | Đọc tối đa 128 byte |

`6982`: PIN chưa xác thực; `6983`: thẻ không ACTIVE; `6985`: chưa sẵn sàng, offset ghi sai hoặc chưa nhận đủ; `6700`: độ dài không hợp lệ; `6A86`: offset đọc hoặc P1/P2 FINISH sai. Lệnh BEGIN làm dữ liệu trước đó không đọc được.

## Thử 16 KiB tiếp theo

Build CAP, dùng simulator và dữ liệu giả riêng sau khi bảo vệ EEPROM hiện có; cài bản mới rồi ghi/đọc đối chiếu file giả 16384 byte. Việc cài lại có thể xóa dữ liệu instance cũ. Đối chiếu byte không đồng nghĩa ảnh được hiển thị hay đã bảo mật. Kiểm tra một file vượt giới hạn bị từ chối; nếu cài hoặc thử thất bại, giữ log và dừng. Sau khi có kết quả mới quyết định thử tiếp 32 KiB; 32768 không biểu diễn được bằng short dương trong giao thức hiện tại và không thể chỉ đổi hằng số.

## Xác minh trước khi dùng tiếp

1. Refresh project, build applet trong Eclipse. Người dùng cho biết tab Problems trống; bản CAP đã được Load, Install và Select trên Java Card Simulator 26.0.
2. Kiểm tra cùng nhóm phương án cài bản thử vào simulator riêng/AID thử để không thay thế instance hay dữ liệu hiện hữu. Không nạp lại trên thẻ thật.
3. Theo log người dùng: lệnh READ trước khi xác thực PIN trả `6982`; sau xác thực PIN thử, BEGIN/WRITE/FINISH/READ 8 byte `ABCDEFGH` đều trả `9000` và đọc lại đúng byte. Cần thử thêm nhiều offset, sai thứ tự, thiếu byte, quá giới hạn, trạng thái khóa/thu hồi và sau deselect. Không ghi PIN thật vào tài liệu hoặc chat.
4. Kiểm tra lại các lệnh chấm công và kiểm soát ra vào, cài nhiều instance và mức dùng bộ nhớ. Thử mất điện và khởi động lại; kiểm tra các tính chất an toàn với dữ liệu giả.
5. Người dùng xác nhận 7 kiểm tra host qua kênh APDU giả đạt trước khi tăng giới hạn; test đã sửa theo giới hạn mới nhưng chưa xác nhận chạy lại. Giao diện báo đọc lại đúng 3172 và 4096 byte cho file PNG giả trên simulator một thẻ với giới hạn cũ. File 4097 byte bị host cũ từ chối; sau khi build và cài lại, file giả 8192 byte đọc lại đúng trên simulator. Cần tiếp tục thử các tình huống lỗi, nhiều kích thước và hồi quy. Trước khi merge hoặc dùng dữ liệu nhạy cảm, thống nhất phân quyền, mã hóa lưu trữ, bảo vệ kênh truyền, xử lý lỗi và yêu cầu dung lượng với nhóm. Không khẳng định PIN một mình đủ bảo mật.
