# Thử lưu ảnh trong EmployeeCardApplet

Bản thử trên nhánh phát triển, chưa dùng cho sản phẩm hoặc dữ liệu nhân viên thật. Applet và host của nhóm chưa được kiểm thử hồi quy sau thay đổi. Không đưa vào main cho đến khi đã review và kiểm thử.

## Giới hạn và an toàn

**Kết quả simulator:** An báo thử lại file giả 16384 byte vẫn đọc đúng; các mức 20480, 24576, 28672 và 32768 byte đều thất bại. Ảnh kết quả 32768 byte cho thấy BEGIN trả `6700`. Chưa xác định nguyên nhân hoặc giới hạn tối đa thực sự; 16 KiB là mức đã xác minh, không phải giới hạn phần cứng đã chứng minh. Log cho thấy CAP đã build và verify, nhưng chưa xác nhận CAP hay applet đang chạy là phiên bản 32 KiB. Chưa kiểm thử nhiều thẻ, bảo mật hay hồi quy.

- Bản 32 KiB cấp hai bộ đệm 16384 byte cho mỗi instance. Nếu cài được 5 instance, riêng bộ đệm ảnh chiếm 160 KiB, chưa kể đối tượng khác; chưa biết simulator hoặc thẻ có đủ bộ nhớ. Trước đó bản 8 KiB và 16 KiB đã được người dùng xác nhận ghi/đọc trên simulator một thẻ.
- Thẻ phải ở trạng thái ACTIVE và PIN đã được xác thực cho mọi lệnh ảnh. Deselect sẽ xóa trạng thái xác thực PIN. Bản thử **không mã hóa dữ liệu hoặc đường truyền**, chưa xác thực mật mã và không phù hợp dữ liệu thật.
- Bắt đầu ghi sẽ làm ảnh cũ không còn đọc được; chưa có cơ chế giữ nguyên ảnh cũ khi ghi lỗi hoặc mất điện. Chỉ dữ liệu đã FINISH thành công mới được đọc.
- Host có tab thử file giả trong chế độ một instance. Theo ảnh người dùng, các file PNG giả 3172 và 4096 byte đã được ghi và đọc lại đúng qua simulator với giới hạn cũ; file 4097 byte bị host cũ từ chối. Sau đó file giả 8192 byte cũng đọc lại đúng với bản mới. Không thay đổi AID hay ghi đè, gỡ cài đặt applet đang được sử dụng khi chưa xác định ảnh hưởng và sao lưu simulator.

## APDU thử nghiệm

CLA `80`; P1/P2 cho độ dài hoặc offset big-endian. Tất cả lệnh ảnh yêu cầu PIN đã xác thực và trạng thái thẻ ACTIVE.

| INS | P1/P2 | Dữ liệu | Tác dụng |
|---|---|---|---|
| `10` | Tổng số byte | Không | Bắt đầu ghi, 1–32768 byte; 32768 mã hóa `8000` |
| `11` | Offset ghi tiếp theo | Các byte, Lc là độ dài | Ghi nối tiếp |
| `12` | `0000` | Không | Hoàn tất nếu đã nhận đủ |
| `13` | Offset đọc | Không; Le là độ dài mong muốn | Đọc tối đa 128 byte |

`6982`: PIN chưa xác thực; `6983`: thẻ không ACTIVE; `6985`: chưa sẵn sàng, offset ghi sai hoặc chưa nhận đủ; `6700`: độ dài không hợp lệ; `6A86`: offset đọc hoặc P1/P2 FINISH sai. Lệnh BEGIN làm dữ liệu trước đó không đọc được.

## Kết quả 16 KiB và bước thử 32 KiB

Trên simulator một thẻ, An xác nhận 16384 byte đọc lại đúng; 20480, 24576, 28672 và 32768 byte không thành công. Trường hợp 32768 báo BEGIN `6700`; chưa có chi tiết lỗi các mức còn lại. Bản 32 KiB dùng hai vùng nhớ và chỉ số byte cuối; test host và logic applet qua API giả đạt nhưng không chứng minh kết quả trên simulator. Tạm dừng tăng dung lượng và dùng 16 KiB đã kiểm chứng làm cơ sở thử ảnh giả. Cài lại applet có thể xóa dữ liệu instance cũ; phải bảo vệ EEPROM trước. Đối chiếu byte không đồng nghĩa ảnh được hiển thị hay đã bảo mật.

## Xác minh trước khi dùng tiếp

1. Refresh project, build applet trong Eclipse. Người dùng cho biết tab Problems trống; bản CAP đã được Load, Install và Select trên Java Card Simulator 26.0.
2. Kiểm tra cùng nhóm phương án cài bản thử vào simulator riêng/AID thử để không thay thế instance hay dữ liệu hiện hữu. Không nạp lại trên thẻ thật.
3. Theo log người dùng: lệnh READ trước khi xác thực PIN trả `6982`; sau xác thực PIN thử, BEGIN/WRITE/FINISH/READ 8 byte `ABCDEFGH` đều trả `9000` và đọc lại đúng byte. Cần thử thêm nhiều offset, sai thứ tự, thiếu byte, quá giới hạn, trạng thái khóa/thu hồi và sau deselect. Không ghi PIN thật vào tài liệu hoặc chat.
4. Kiểm tra lại các lệnh chấm công và kiểm soát ra vào, cài nhiều instance và mức dùng bộ nhớ. Thử mất điện và khởi động lại; kiểm tra các tính chất an toàn với dữ liệu giả.
5. Người dùng xác nhận 7 kiểm tra host qua kênh APDU giả đạt trước khi tăng giới hạn; test đã sửa theo giới hạn mới nhưng chưa xác nhận chạy lại. Giao diện báo đọc lại đúng 3172 và 4096 byte cho file PNG giả trên simulator một thẻ với giới hạn cũ. File 4097 byte bị host cũ từ chối; sau khi build và cài lại, file giả 8192 byte đọc lại đúng trên simulator. Cần tiếp tục thử các tình huống lỗi, nhiều kích thước và hồi quy. Trước khi merge hoặc dùng dữ liệu nhạy cảm, thống nhất phân quyền, mã hóa lưu trữ, bảo vệ kênh truyền, xử lý lỗi và yêu cầu dung lượng với nhóm. Không khẳng định PIN một mình đủ bảo mật.
