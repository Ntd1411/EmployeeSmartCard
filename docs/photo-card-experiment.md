# Thử lưu ảnh trong EmployeeCardApplet

Bản thử trên nhánh phát triển, chưa dùng cho sản phẩm hoặc dữ liệu nhân viên thật. Applet và host của nhóm chưa được kiểm thử hồi quy sau thay đổi. Không đưa vào main cho đến khi đã review và kiểm thử.

## Giới hạn và an toàn

- Code mới đặt bộ đệm 8192 byte cho mỗi instance khi cài applet; chưa build, cài hoặc thử trên simulator. Nếu dùng 5 instance thì riêng bộ đệm ảnh chiếm 40 KiB, chưa kể các đối tượng khác. Chưa xác định đủ bộ nhớ trên thẻ hoặc simulator.
- Thẻ phải ở trạng thái ACTIVE và PIN đã được xác thực cho mọi lệnh ảnh. Deselect sẽ xóa trạng thái xác thực PIN. Bản thử **không mã hóa dữ liệu hoặc đường truyền**, chưa xác thực mật mã và không phù hợp dữ liệu thật.
- Bắt đầu ghi sẽ làm ảnh cũ không còn đọc được; chưa có cơ chế giữ nguyên ảnh cũ khi ghi lỗi hoặc mất điện. Chỉ dữ liệu đã FINISH thành công mới được đọc.
- Host có tab thử file giả trong chế độ một instance. Theo ảnh người dùng, các file PNG giả 3172 và 4096 byte đã được ghi và đọc lại đúng qua simulator với giới hạn cũ; file 4097 byte bị host cũ từ chối. Không thay đổi AID hay ghi đè, gỡ cài đặt applet đang được sử dụng khi chưa xác định ảnh hưởng và sao lưu simulator.

## APDU thử nghiệm

CLA `80`; P1/P2 cho độ dài hoặc offset big-endian. Tất cả lệnh ảnh yêu cầu PIN đã xác thực và trạng thái thẻ ACTIVE.

| INS | P1/P2 | Dữ liệu | Tác dụng |
|---|---|---|---|
| `10` | Tổng số byte | Không | Bắt đầu ghi, 1–8192 byte trong code mới chưa kiểm thử |
| `11` | Offset ghi tiếp theo | Các byte, Lc là độ dài | Ghi nối tiếp |
| `12` | `0000` | Không | Hoàn tất nếu đã nhận đủ |
| `13` | Offset đọc | Không; Le là độ dài mong muốn | Đọc tối đa 128 byte |

`6982`: PIN chưa xác thực; `6983`: thẻ không ACTIVE; `6985`: chưa sẵn sàng, offset ghi sai hoặc chưa nhận đủ; `6700`: độ dài không hợp lệ; `6A86`: offset đọc hoặc P1/P2 FINISH sai. Lệnh BEGIN làm dữ liệu trước đó không đọc được.

## Xác minh trước khi dùng tiếp

1. Refresh project, build applet trong Eclipse. Người dùng cho biết tab Problems trống; bản CAP đã được Load, Install và Select trên Java Card Simulator 26.0.
2. Kiểm tra cùng nhóm phương án cài bản thử vào simulator riêng/AID thử để không thay thế instance hay dữ liệu hiện hữu. Không nạp lại trên thẻ thật.
3. Theo log người dùng: lệnh READ trước khi xác thực PIN trả `6982`; sau xác thực PIN thử, BEGIN/WRITE/FINISH/READ 8 byte `ABCDEFGH` đều trả `9000` và đọc lại đúng byte. Cần thử thêm nhiều offset, sai thứ tự, thiếu byte, quá giới hạn, trạng thái khóa/thu hồi và sau deselect. Không ghi PIN thật vào tài liệu hoặc chat.
4. Kiểm tra lại các lệnh chấm công và kiểm soát ra vào, cài nhiều instance và mức dùng bộ nhớ. Thử mất điện và khởi động lại; kiểm tra các tính chất an toàn với dữ liệu giả.
5. Người dùng xác nhận 7 kiểm tra host qua kênh APDU giả đạt và giao diện báo đọc lại đúng 3172 và 4096 byte cho file PNG giả trên simulator một thẻ với giới hạn cũ. File 4097 byte bị host cũ từ chối; mức 8192 byte mới chưa thử. Cần tiếp tục thử các tình huống lỗi, nhiều kích thước và hồi quy. Trước khi merge hoặc dùng dữ liệu nhạy cảm, thống nhất phân quyền, mã hóa lưu trữ, bảo vệ kênh truyền, xử lý lỗi và yêu cầu dung lượng với nhóm. Không khẳng định PIN một mình đủ bảo mật.
