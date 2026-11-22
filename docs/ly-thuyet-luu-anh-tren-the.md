# Lý thuyết và nhật ký thử nghiệm lưu ảnh trên thẻ Java Card

> **Trạng thái:** thử nghiệm trên nhánh `feat/photo-card`, không phải chức năng sản phẩm. Chỉ dùng byte và ảnh giả; không dùng ảnh nhân viên thật, PIN thật hoặc thẻ thật. Tài liệu mô tả những gì đã quan sát, không khẳng định các khả năng chưa kiểm thử.

## 1. Mục tiêu và các thành phần

Mục tiêu là tìm hiểu cách lưu một file ảnh nhỏ dưới dạng byte trong applet, rồi đọc lại qua APDU. Applet không hiểu định dạng ảnh: nó chỉ lưu và trả lại byte. Việc chọn file, chuyển ảnh thành byte, chọn định dạng/kích thước phù hợp, so sánh dữ liệu và hiển thị ảnh thuộc ứng dụng host trên máy tính.

```text
File ảnh thử → Host: đọc byte → APDU theo đoạn → Applet: lưu byte
File kết quả ← Host: ghép byte ← APDU theo đoạn ← Applet: đọc byte
```

Hiện tại host của nhóm đã có giao diện và kết nối simulator cho chấm công/kiểm soát ra vào, nhưng **chưa hỗ trợ gửi hay đọc ảnh**. Bài thử mới được thực hiện bằng Eclipse Console, chưa gửi file ảnh thực tế.

## 2. Vì sao cần chia đoạn?

Một ảnh thường lớn hơn dữ liệu nên gửi trong một APDU thông thường. Chia theo đoạn cho phép host gửi dữ liệu lần lượt và yêu cầu đọc lại một phần. Hai byte `P1`, `P2` được ghép theo thứ tự big-endian để biểu diễn tổng độ dài hoặc vị trí bắt đầu (offset): ví dụ `00 08` là 8, `00 04` là 4. Đây là quy ước của bài thử, không phải ý nghĩa cố định của P1/P2 với mọi applet.

Sau khi applet được chọn, quy trình thử nghiệm gồm BEGIN → một hoặc nhiều WRITE → FINISH → một hoặc nhiều READ. Mỗi phản hồi chứa status word (SW); host phải dừng và xử lý khi SW không phải `9000`, thay vì tiếp tục mặc định đã thành công.

## 3. Giao thức byte ảnh thử nghiệm

Các lệnh dưới đây dùng CLA `80`. `Lc` là số byte dữ liệu gửi vào; `Le` là số byte phản hồi mong muốn. Mỗi lệnh ảnh trong `EmployeeCardApplet` yêu cầu instance đang ACTIVE và PIN đã được xác thực trong phiên chọn applet.

| INS | P1/P2 | Dữ liệu | Ý nghĩa |
|---|---|---|---|
| `10` BEGIN | Tổng độ dài | Không | Bắt đầu một lần ghi, tối đa 4096 byte trong bản thử |
| `11` WRITE | Offset tiếp theo | `Lc` byte | Ghi nối tiếp vào bộ đệm |
| `12` FINISH | `0000` | Không | Cho phép đọc nếu đã nhận đủ số byte |
| `13` READ | Offset | Không; `Le` là số byte muốn đọc | Đọc từ vị trí yêu cầu, tối đa 128 byte mỗi lệnh |

Bản thử cấp phát bộ đệm 4096 byte cho **mỗi instance** lúc cài đặt. Năm instance cần khoảng 20 KiB chỉ cho các bộ đệm này, chưa tính phần còn lại. Đây không phải kết luận về dung lượng thẻ thật hoặc việc năm instance chắc chắn cài thành công.

### Ví dụ 8 byte

`41 42 43 44 45 46 47 48` là `ABCDEFGH` theo ASCII. Chuỗi dưới đây chỉ chạy sau khi chọn đúng applet và xác thực PIN thử; các lệnh không bao gồm từ `send` trong simulator Console:

```text
0x80 0x10 0x00 0x08;
0x80 0x11 0x00 0x00 0x04 0x41 0x42 0x43 0x44;
0x80 0x11 0x00 0x04 0x04 0x45 0x46 0x47 0x48;
0x80 0x12 0x00 0x00;
0x80 0x13 0x00 0x00 0x08;
```

Phản hồi cuối kỳ vọng: `4142434445464748`, SW `9000`. Khi đọc từ offset 4, độ dài 4, kết quả kỳ vọng là `45464748` (`EFGH`). Không gửi PIN thật trong log, tài liệu hoặc chat.

### Một số mã trạng thái

- `9000`: thao tác thành công.
- `6982`: PIN chưa được xác thực cho lệnh ảnh.
- `6983`: thẻ không ở trạng thái ACTIVE.
- `6985`: trạng thái ghi/đọc chưa phù hợp, offset ghi không phải đoạn tiếp theo hoặc chưa nhận đủ.
- `6700`: độ dài không hợp lệ.
- `6A86`: offset đọc hoặc tham số FINISH không hợp lệ.
- `6D00`: INS không được applet đang nhận lệnh hỗ trợ; cũng có thể xảy ra khi chưa chọn đúng applet.
- `6A82` khi SELECT: không tìm thấy applet AID được yêu cầu trong trạng thái simulator hiện tại.

Các dòng warning kèm `ISOException` có thể là cách simulator hiển thị một status word bị từ chối có chủ đích; cần đọc SW và ngữ cảnh thay vì mặc định đó là lỗi crash.

## 4. Những gì đã thực hiện

1. Tạo `PhotoSpikeApplet` độc lập và thử bằng `PhotoCardSpike`. Theo log người dùng, lệnh ghi hai đoạn và đọc lại `ABCDEFGH` thành công; các phép đọc `ABCD`, `EFGH`, `CD` theo offset và độ dài cũng thành công. Đây là dữ liệu giả, không phải ảnh.
2. Tạo nhánh `feat/photo-card` từ main đã cập nhật. Chuyển ý tưởng giao thức vào `EmployeeCardApplet` hiện có, thêm kiểm tra PIN và trạng thái ACTIVE; không sửa ứng dụng host.
3. Theo log Eclipse/simulator, CAP thử đã được Load/Install/Select. READ trước PIN trả `6982`; sau xác thực PIN thử, BEGIN, hai WRITE, FINISH và READ đều trả `9000`, đọc đúng `ABCDEFGH`.
4. Những thử nghiệm trên **không** chứng minh tất cả biên, lỗi, tính năng cũ, nhiều instance hoặc ảnh thật hoạt động; chúng chưa được kiểm thử đầy đủ.

`spikes/photo-card/` là bài thử độc lập và tài liệu cục bộ để đối chiếu; không bắt buộc đưa vào main. File EEPROM là trạng thái mô phỏng, không phải file ảnh. Các file EEPROM thử nghiệm được bỏ qua trong `.gitignore`; không nên chia sẻ file đó nếu có dữ liệu nhạy cảm.

## 5. Lưu và nạp trạng thái simulator

Lần đầu dừng/chạy simulator mà không cấu hình file EEPROM, SELECT applet thử trả `6A82`: instance không được tìm thấy trong trạng thái mới. Không thể từ đó suy ra thẻ thật không lưu dữ liệu.

Sau đó, trong Properties của Java Card Simulator 26.0, đã cấu hình **Output File For EEPROM Data**, chạy lại simulator, cài applet thử, ghi dữ liệu, dừng và thấy file được tạo. Cấu hình file đó làm **Input File With EEPROM Data**, dùng một output khác, rồi khởi động lại. Theo log người dùng, SELECT thành công và đọc lại `ABCDEFGH` với `9000` mà không cài lại. Đây là kết quả cho lần thử với bản EEPROM đã nạp, không phải phép đo độ bền hay kiểm thử thẻ thật.

Chú ý input và output là hai đường dẫn cấu hình riêng. Nếu muốn giữ trạng thái mới nhất cho lần chạy sau, phải xác định file output của lần chạy đã được tạo thành công và chọn nó làm input cho lần chạy sau; tránh ghi đè bản sao đang cần giữ. Simulator có thể chứa dữ liệu của nhiều applet: không xóa, thay file hoặc khởi tạo lại tùy tiện.

## 6. Giới hạn an toàn và việc còn lại

- PIN chỉ là một kiểm tra truy cập trong bài thử; ảnh chưa được mã hóa khi lưu hoặc truyền, chưa có xác thực mật mã. Không đủ an toàn cho dữ liệu cá nhân.
- BEGIN làm dữ liệu cũ không còn đọc được, ngay cả khi lần ghi mới thất bại. Chưa có cơ chế bảo toàn ảnh cũ, xử lý mất điện hoặc khôi phục dữ liệu được kiểm chứng.
- Cần thử offset sai, dữ liệu thiếu/thừa, giới hạn, lệnh khi chưa xác thực và sau deselect, các trạng thái thẻ, khởi động lại applet chung, và hồi quy chấm công/kiểm soát ra vào.
- Cần kiểm tra tổng bộ nhớ với nhiều instance; không giả định bộ đệm 4096 byte là phù hợp với thẻ thật.
- Bước phát triển tiếp theo là thống nhất cách mở rộng host sẵn có: chọn file giả, giới hạn kích thước, gửi và đọc theo đoạn, kiểm tra SW, so sánh toàn bộ byte. Cần cân nhắc cách xác thực lại sau khi chọn thẻ và không khóa giao diện khi trao đổi với simulator.
- Trước khi merge hay dùng dữ liệu thật: thống nhất với nhóm về phân quyền, mã hóa lưu trữ, bảo vệ kênh truyền, dữ liệu nhạy cảm, khôi phục sau lỗi và thử nghiệm trên phần cứng đích.
