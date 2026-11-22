# Thử lưu ảnh trong EmployeeCardApplet

Bản thử trên nhánh phát triển, chưa dùng cho sản phẩm hoặc dữ liệu nhân viên thật. Applet và host của nhóm chưa được kiểm thử hồi quy sau thay đổi. Không đưa vào main cho đến khi đã review và kiểm thử.

## Giới hạn và an toàn

- Bộ đệm cố định 4096 byte cho mỗi instance, cấp phát khi cài applet; giới hạn này chỉ dành cho thử nghiệm. Nếu dùng 5 instance thì riêng bộ đệm ảnh chiếm 20 KiB, chưa kể các đối tượng khác. Chưa xác định đủ bộ nhớ trên thẻ hoặc simulator.
- Thẻ phải ở trạng thái ACTIVE và PIN đã được xác thực cho mọi lệnh ảnh. Deselect sẽ xóa trạng thái xác thực PIN. Bản thử **không mã hóa dữ liệu hoặc đường truyền**, chưa xác thực mật mã và không phù hợp dữ liệu thật.
- Bắt đầu ghi sẽ làm ảnh cũ không còn đọc được; chưa có cơ chế giữ nguyên ảnh cũ khi ghi lỗi hoặc mất điện. Chỉ dữ liệu đã FINISH thành công mới được đọc.
- Không có sửa đổi nào ở host; chưa có chức năng gửi file ảnh. Không thay đổi AID hay ghi đè, gỡ cài đặt applet đang được sử dụng để thử khi chưa xác định ảnh hưởng và sao lưu simulator.

## APDU thử nghiệm

CLA `80`; P1/P2 cho độ dài hoặc offset big-endian. Tất cả lệnh ảnh yêu cầu PIN đã xác thực và trạng thái thẻ ACTIVE.

| INS | P1/P2 | Dữ liệu | Tác dụng |
|---|---|---|---|
| `10` | Tổng số byte | Không | Bắt đầu ghi, 1–4096 byte |
| `11` | Offset ghi tiếp theo | Các byte, Lc là độ dài | Ghi nối tiếp |
| `12` | `0000` | Không | Hoàn tất nếu đã nhận đủ |
| `13` | Offset đọc | Không; Le là độ dài mong muốn | Đọc tối đa 128 byte |

`6982`: PIN chưa xác thực; `6983`: thẻ không ACTIVE; `6985`: chưa sẵn sàng, offset ghi sai hoặc chưa nhận đủ; `6700`: độ dài không hợp lệ; `6A86`: offset đọc hoặc P1/P2 FINISH sai. Lệnh BEGIN làm dữ liệu trước đó không đọc được.

## Xác minh trước khi dùng tiếp

1. Refresh project, build applet trong Eclipse. Người dùng cho biết tab Problems trống; bản CAP đã được Load, Install và Select trên Java Card Simulator 26.0.
2. Kiểm tra cùng nhóm phương án cài bản thử vào simulator riêng/AID thử để không thay thế instance hay dữ liệu hiện hữu. Không nạp lại trên thẻ thật.
3. Theo log người dùng: lệnh READ trước khi xác thực PIN trả `6982`; sau xác thực PIN thử, BEGIN/WRITE/FINISH/READ 8 byte `ABCDEFGH` đều trả `9000` và đọc lại đúng byte. Cần thử thêm nhiều offset, sai thứ tự, thiếu byte, quá giới hạn, trạng thái khóa/thu hồi và sau deselect. Không ghi PIN thật vào tài liệu hoặc chat.
4. Kiểm tra lại các lệnh chấm công và kiểm soát ra vào, cài nhiều instance và mức dùng bộ nhớ. Thử mất điện và khởi động lại; kiểm tra các tính chất an toàn với dữ liệu giả.
5. Trước khi tích hợp host hoặc dùng dữ liệu nhạy cảm, thống nhất phân quyền, mã hóa lưu trữ, bảo vệ kênh truyền, xử lý lỗi và yêu cầu dung lượng với nhóm. Không khẳng định PIN một mình đủ bảo mật.
