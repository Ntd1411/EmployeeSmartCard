package com.nhom11.smartcard.host.ui;

import com.nhom11.smartcard.host.provider.ApduSimulatorCardGateway;
import com.nhom11.smartcard.host.provider.PhotoTransfer;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Experimental simulator-only photo round trip; no encryption or real data. */
public class PhotoExperimentPanel extends JPanel {
    private final ApduSimulatorCardGateway gateway;
    private final JButton testButton = new JButton("Chọn ảnh giả và thử ghi/đọc");
    private final JLabel status = new JLabel("Chưa chạy thử.");

    public PhotoExperimentPanel(ApduSimulatorCardGateway gateway) {
        super(new BorderLayout(8, 8));
        this.gateway = gateway;
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(testButton);
        add(new JLabel("Chỉ dùng ảnh giả, tối đa 8192 byte. Dữ liệu chưa được mã hóa."), BorderLayout.NORTH);
        add(actions, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        testButton.addActionListener(e -> startTest());
    }

    private void startTest() {
        if (!gateway.isConnected() || gateway.isMultiCardMode()) {
            JOptionPane.showMessageDialog(this,
                    "Hãy kết nối simulator ở chế độ 1 thẻ và xác thực PIN thử trước.",
                    "Chưa sẵn sàng", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn ảnh giả tối đa 8192 byte");
        chooser.setFileFilter(new FileNameExtensionFilter("Ảnh thử (PNG, JPEG, GIF)", "png", "jpg", "jpeg", "gif"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();
        int confirm = JOptionPane.showConfirmDialog(this,
                "Chỉ dùng ảnh giả, không dùng dữ liệu thật. Dữ liệu không được mã hóa.\n"
                + "Bắt đầu ghi sẽ làm dữ liệu ảnh cũ trên applet thử không còn đọc được, kể cả khi ghi lỗi.\n"
                + "Tiếp tục ghi rồi đọc lại để so sánh?",
                "Xác nhận thử nghiệm", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        testButton.setEnabled(false);
        status.setText("Đang thử ghi và đọc lại; vui lòng chờ...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                byte[] bytes;
                try (InputStream input = Files.newInputStream(file)) {
                    bytes = input.readNBytes(PhotoTransfer.MAX_SIZE + 1);
                }
                if (bytes.length < 1 || bytes.length > PhotoTransfer.MAX_SIZE) {
                    throw new IllegalArgumentException("File phải có từ 1 đến 8192 byte. Không có dữ liệu nào được gửi.");
                }
                try {
                    boolean match = gateway.verifyPhotoRoundTrip(bytes);
                    return match ? "Thành công: đọc lại đúng " + bytes.length + " byte."
                            : "Không khớp: dữ liệu đọc lại khác dữ liệu gửi.";
                } finally {
                    Arrays.fill(bytes, (byte) 0);
                }
            }

            @Override
            protected void done() {
                try {
                    status.setText(get());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    status.setText("Thử nghiệm thất bại: " + cause.getMessage());
                } finally {
                    testButton.setEnabled(true);
                }
            }
        }.execute();
    }
}
