package com.nhom11.smartcard.host.ui;

import com.nhom11.smartcard.host.provider.ApduSimulatorCardGateway;
import com.nhom11.smartcard.host.provider.CardGateway;
import com.nhom11.smartcard.host.provider.InMemoryAccessPolicyProvider;
import com.nhom11.smartcard.host.provider.InMemoryEmployeeProvider;
import com.nhom11.smartcard.host.provider.InMemoryVirtualCardProvider;
import com.nhom11.smartcard.host.provider.MockSeedDataFactory;
import com.nhom11.smartcard.host.provider.SwitchableCardGateway;
import com.nhom11.smartcard.host.provider.VirtualStoreCardGateway;
import com.nhom11.smartcard.host.repository.FileAccessLogRepository;
import com.nhom11.smartcard.host.repository.FileAttendanceRepository;
import com.nhom11.smartcard.host.service.AccessControlService;
import com.nhom11.smartcard.host.service.AttendanceService;
import com.nhom11.smartcard.host.service.AttendanceStatisticsService;
import com.nhom11.smartcard.host.service.ShiftPairingService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

/**
 * Entry point for the standalone host application covering US10-US13 with live jcsl integration.
 */
public class MainApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            installLookAndFeel();
            new MainApp().showWindow();
        });
    }

    private static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Fall back to default Swing look and feel.
        }
    }

    private void showWindow() {
        Path dataDir = Files.isDirectory(Path.of("host"))
                ? Path.of("host", "data")
                : Path.of("data");

        InMemoryEmployeeProvider employeeProvider = new InMemoryEmployeeProvider();
        InMemoryVirtualCardProvider cardProvider = new InMemoryVirtualCardProvider();
        InMemoryAccessPolicyProvider policyProvider = new InMemoryAccessPolicyProvider();
        VirtualStoreCardGateway virtualGateway = new VirtualStoreCardGateway(cardProvider);
        ApduSimulatorCardGateway apduGateway = new ApduSimulatorCardGateway(cardProvider);
        SwitchableCardGateway switchableGateway = new SwitchableCardGateway(virtualGateway);

        FileAttendanceRepository attendanceRepo = new FileAttendanceRepository(
                dataDir.resolve("attendance_records.tsv"));
        FileAccessLogRepository accessLogRepo = new FileAccessLogRepository(
                dataDir.resolve("access_logs.tsv"));

        if (attendanceRepo.findAll().isEmpty()) {
            MockSeedDataFactory.populateDemoData(
                    employeeProvider, cardProvider, policyProvider,
                    attendanceRepo, virtualGateway, LocalDate.now());
        } else {
            // Keep persisted attendance history while loading demo employee/card/policy catalog.
            FileAttendanceRepository discardRepo = new FileAttendanceRepository();
            MockSeedDataFactory.populateDemoData(
                    employeeProvider, cardProvider, policyProvider,
                    discardRepo, null, LocalDate.now());
            virtualGateway.syncFromRecords(attendanceRepo.findAll());
        }

        AttendanceService attendanceService = new AttendanceService(
                employeeProvider, cardProvider, attendanceRepo, switchableGateway);
        ShiftPairingService shiftPairingService = new ShiftPairingService(attendanceRepo);
        AttendanceStatisticsService statisticsService = new AttendanceStatisticsService(
                employeeProvider, attendanceRepo, shiftPairingService);
        AccessControlService accessControlService = new AccessControlService(
                employeeProvider, cardProvider, policyProvider, accessLogRepo, switchableGateway);

        JCheckBox securityCheck = new JCheckBox("Bật kiểm tra PIN & AES/RSA mô phỏng", true);
        JLabel modeBadge = new JLabel("Chế độ: Thẻ ảo nội bộ");
        modeBadge.setFont(modeBadge.getFont().deriveFont(Font.BOLD));
        modeBadge.setForeground(new Color(45, 55, 72));

        JLabel apduTraceLabel = new JLabel("APDU trace: Chưa kết nối tới Simulator jcsl.");
        apduTraceLabel.setBorder(BorderFactory.createEmptyBorder(2, 10, 4, 10));

        Runnable updateSimulatorTrace = () -> {
            if (apduGateway.isConnected()) {
                apduTraceLabel.setText("APDU trace: " + apduGateway.getLastApduSummary());
            } else {
                apduTraceLabel.setText("APDU trace: Đang chạy chế độ Thẻ ảo nội bộ.");
            }
        };

        JFrame frame = new JFrame("Nhóm 11 — Quản lý Chấm công & Kiểm soát Ra vào (US10–US13)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1120, 720);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(6, 6));
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                apduGateway.disconnect();
            }
        });

        AttendancePanel attendancePanel = new AttendancePanel(
                employeeProvider, cardProvider, attendanceService,
                securityCheck::isSelected, updateSimulatorTrace);
        ShiftCalculationPanel shiftPanel = new ShiftCalculationPanel(
                employeeProvider, shiftPairingService);
        StatisticsPanel statsPanel = new StatisticsPanel(
                employeeProvider, statisticsService);
        AccessSimulationPanel accessPanel = new AccessSimulationPanel(
                employeeProvider, cardProvider, policyProvider, accessControlService,
                securityCheck::isSelected,
                attendancePanel::refreshData,
                apduGateway,
                updateSimulatorTrace);

        Runnable refreshAllTabs = () -> {
            attendancePanel.refreshData();
            shiftPanel.refreshData();
            statsPanel.refreshData();
            accessPanel.refreshData();
        };

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        JButton resetSeedBtn = new JButton("Nạp lại dữ liệu mẫu");
        resetSeedBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    frame,
                    "Khôi phục toàn bộ dữ liệu chấm công và nhật ký về bộ dữ liệu mẫu ban đầu?",
                    "Xác nhận nạp dữ liệu mẫu",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                accessLogRepo.clear();
                MockSeedDataFactory.populateDemoData(
                        employeeProvider, cardProvider, policyProvider,
                        attendanceRepo, virtualGateway, LocalDate.now());
                refreshAllTabs.run();
            }
        });
        row1.add(resetSeedBtn);
        row1.add(securityCheck);
        row1.add(modeBadge);
        row1.add(new JLabel("Dữ liệu: " + dataDir.toAbsolutePath()));

        JTextField hostField = new JTextField("127.0.0.1", 10);
        JTextField portField = new JTextField("9025", 5);
        JPasswordField pinField = new JPasswordField("1234", 7);

        JButton deployBtn = new JButton("Cài 5 thẻ lên jcsl");
        JButton connectBtn = new JButton("Kết nối jcsl");
        JButton verifyPinBtn = new JButton("Verify PIN");
        JButton readChipBtn = new JButton("Đọc trạng thái jcsl");
        JButton disconnectBtn = new JButton("Ngắt kết nối");
        verifyPinBtn.setEnabled(false);
        readChipBtn.setEnabled(false);
        disconnectBtn.setEnabled(false);

        JTabbedPane tabs = new JTabbedPane();

        java.util.function.Supplier<String> resolveFocusedCardId = () -> {
            int idx = tabs.getSelectedIndex();
            String selected = null;
            if (idx == 0) {
                selected = attendancePanel.getSelectedCardId();
            } else if (idx == 3) {
                selected = accessPanel.getSelectedCardId();
            }
            return selected != null ? selected : "CARD-001";
        };

        deployBtn.addActionListener(e -> {
            String host = hostField.getText().trim();
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Cổng port không hợp lệ.", "Lỗi cài 5 thẻ jcsl",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            char[] pinChars = pinField.getPassword();
            deployBtn.setEnabled(false);
            connectBtn.setEnabled(false);
            hostField.setEnabled(false);
            portField.setEnabled(false);
            pinField.setEnabled(false);
            verifyPinBtn.setEnabled(false);
            readChipBtn.setEnabled(false);
            disconnectBtn.setEnabled(false);
            modeBadge.setText("Đang cài 5 thẻ lên jcsl...");
            modeBadge.setForeground(UiTheme.COLOR_NEUTRAL_FG);

            new SwingWorker<DeployOutcome, Void>() {
                @Override
                protected DeployOutcome doInBackground() {
                    try {
                        int count = apduGateway.deployMultiCards(
                                host,
                                port,
                                ApduSimulatorCardGateway.DEFAULT_DEPLOY_TIMEOUT_MS,
                                cardProvider.findAll(),
                                pinChars);
                        return new DeployOutcome(true, count, null);
                    } catch (Exception ex) {
                        return new DeployOutcome(false, 0, ex.getMessage());
                    } finally {
                        Arrays.fill(pinChars, '\0');
                    }
                }

                @Override
                protected void done() {
                    DeployOutcome outcome;
                    try {
                        outcome = get();
                    } catch (Exception ex) {
                        outcome = new DeployOutcome(false, 0, ex.getMessage());
                    }
                    pinField.setEnabled(true);
                    if (outcome.deployed) {
                        switchableGateway.useDelegate(apduGateway);
                        deployBtn.setEnabled(true);
                        connectBtn.setEnabled(false);
                        hostField.setEnabled(false);
                        portField.setEnabled(false);
                        verifyPinBtn.setEnabled(true);
                        readChipBtn.setEnabled(true);
                        disconnectBtn.setEnabled(true);
                        modeBadge.setText("Chế độ: Simulator jcsl — Nhiều thẻ (" + host + ":" + port + ")");
                        modeBadge.setForeground(UiTheme.COLOR_ALLOW_FG);
                        updateSimulatorTrace.run();
                        JOptionPane.showMessageDialog(frame,
                                "Đã nạp và cài đặt thành công " + outcome.count + " thẻ độc lập trên jcsl:\n"
                                        + "- CARD-001 -> AID 112233445501 (ACTIVE)\n"
                                        + "- CARD-002 -> AID 112233445502 (ACTIVE)\n"
                                        + "- CARD-003 -> AID 112233445503 (LOCKED)\n"
                                        + "- CARD-004 -> AID 112233445504 (ACTIVE, chưa Verify PIN)\n"
                                        + "- CARD-005 -> AID 112233445505 (REVOKED)",
                                "Cài 5 thẻ lên jcsl thành công",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        switchableGateway.useDelegate(virtualGateway);
                        deployBtn.setEnabled(true);
                        connectBtn.setEnabled(true);
                        hostField.setEnabled(true);
                        portField.setEnabled(true);
                        verifyPinBtn.setEnabled(false);
                        readChipBtn.setEnabled(false);
                        disconnectBtn.setEnabled(false);
                        modeBadge.setText("Chế độ: Thẻ ảo nội bộ");
                        modeBadge.setForeground(UiTheme.COLOR_NEUTRAL_FG);
                        updateSimulatorTrace.run();
                        JOptionPane.showMessageDialog(frame, outcome.errorMessage,
                                "Không cài được 5 thẻ lên jcsl", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        connectBtn.addActionListener(e -> {
            String host = hostField.getText().trim();
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Cổng port không hợp lệ.", "Lỗi kết nối jcsl",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            char[] pinChars = pinField.getPassword();
            deployBtn.setEnabled(false);
            connectBtn.setEnabled(false);
            hostField.setEnabled(false);
            portField.setEnabled(false);
            pinField.setEnabled(false);
            verifyPinBtn.setEnabled(false);
            readChipBtn.setEnabled(false);
            disconnectBtn.setEnabled(false);
            modeBadge.setText("Đang kết nối tới jcsl...");
            modeBadge.setForeground(UiTheme.COLOR_NEUTRAL_FG);

            new SwingWorker<ConnectOutcome, Void>() {
                @Override
                protected ConnectOutcome doInBackground() {
                    try {
                        apduGateway.connect(host, port);
                        CardGateway.CardGatewayResult pinRes = null;
                        if (pinChars.length > 0) {
                            pinRes = apduGateway.verifyPin(pinChars);
                        }
                        return new ConnectOutcome(true, pinRes, null);
                    } catch (Exception ex) {
                        return new ConnectOutcome(false, null, ex.getMessage());
                    } finally {
                        Arrays.fill(pinChars, '\0');
                    }
                }

                @Override
                protected void done() {
                    ConnectOutcome outcome;
                    try {
                        outcome = get();
                    } catch (Exception ex) {
                        outcome = new ConnectOutcome(false, null, ex.getMessage());
                    }
                    pinField.setEnabled(true);
                    deployBtn.setEnabled(true);
                    if (outcome.connected) {
                        switchableGateway.useDelegate(apduGateway);
                        connectBtn.setEnabled(false);
                        hostField.setEnabled(false);
                        portField.setEnabled(false);
                        verifyPinBtn.setEnabled(true);
                        readChipBtn.setEnabled(true);
                        disconnectBtn.setEnabled(true);
                        String modeSuffix = apduGateway.isMultiCardMode() ? "Nhiều thẻ" : "1 thẻ";
                        modeBadge.setText("Chế độ: Simulator jcsl — " + modeSuffix + " (" + host + ":" + port + ")");
                        modeBadge.setForeground(UiTheme.COLOR_ALLOW_FG);
                        updateSimulatorTrace.run();
                        if (outcome.pinResult != null && !outcome.pinResult.isSuccess()) {
                            JOptionPane.showMessageDialog(frame,
                                    "Đã kết nối tới jcsl nhưng xác thực PIN thất bại: "
                                            + outcome.pinResult.getMessage()
                                            + "\nBạn có thể sửa PIN và bấm 'Verify PIN'.",
                                    "Cảnh báo PIN trên jcsl",
                                    JOptionPane.WARNING_MESSAGE);
                        }
                    } else {
                        switchableGateway.useDelegate(virtualGateway);
                        connectBtn.setEnabled(true);
                        hostField.setEnabled(true);
                        portField.setEnabled(true);
                        verifyPinBtn.setEnabled(false);
                        readChipBtn.setEnabled(false);
                        disconnectBtn.setEnabled(false);
                        modeBadge.setText("Chế độ: Thẻ ảo nội bộ");
                        modeBadge.setForeground(UiTheme.COLOR_NEUTRAL_FG);
                        updateSimulatorTrace.run();
                        JOptionPane.showMessageDialog(frame, outcome.errorMessage,
                                "Không kết nối được jcsl", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        verifyPinBtn.addActionListener(e -> {
            String targetCardId = resolveFocusedCardId.get();
            char[] pinChars = pinField.getPassword();
            try {
                CardGateway.CardGatewayResult res = apduGateway.verifyPin(targetCardId, pinChars);
                if (res.isSuccess()) {
                    cardProvider.findByCardId(targetCardId).ifPresent(vc -> vc.setPinVerified(true));
                    refreshAllTabs.run();
                }
                updateSimulatorTrace.run();
                JOptionPane.showMessageDialog(frame,
                        "Thẻ: " + targetCardId + " (AID " + apduGateway.getActiveAidHex() + ")\n" + res.getMessage(),
                        res.isSuccess() ? "Verify PIN thành công" : "Verify PIN thất bại",
                        res.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
            } finally {
                Arrays.fill(pinChars, '\0');
            }
        });

        readChipBtn.addActionListener(e -> {
            String targetCardId = resolveFocusedCardId.get();
            try {
                ApduSimulatorCardGateway.CardChipStatus status = apduGateway.readCardStatus(targetCardId);
                ApduSimulatorCardGateway.LastCardAttendance last = apduGateway.readLastAttendance(targetCardId);
                updateSimulatorTrace.run();
                String msg = "Thẻ đang chọn: " + targetCardId + " (AID " + apduGateway.getActiveAidHex() + ")"
                        + "\nChế độ nhiều thẻ: " + (apduGateway.isMultiCardMode() ? "Bật" : "Tắt (1 thẻ)")
                        + "\nTrạng thái thẻ: " + status.getStatus()
                        + "\nPIN đã xác thực: " + (status.isPinValidated() ? "Có" : "Chưa")
                        + "\nTổng lượt quẹt trên chip: " + status.getPunchCounter()
                        + "\nTrạng thái ca trên chip: " + (status.isShiftOpen() ? "Đang mở (CHECK_IN)" : "Đang đóng")
                        + "\nBản ghi cuối trên chip: "
                        + (last.getEventType().isPresent()
                                ? last.getEventType().get() + " lúc " + last.getTimestamp().get()
                                        + " (khu vực " + last.getAreaCode() + ")"
                                : "Chưa có dữ liệu");
                JOptionPane.showMessageDialog(frame, msg, "Trạng thái Simulator jcsl", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                updateSimulatorTrace.run();
                JOptionPane.showMessageDialog(frame, ex.getMessage(), "Lỗi đọc trạng thái jcsl",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        disconnectBtn.addActionListener(e -> {
            apduGateway.disconnect();
            switchableGateway.useDelegate(virtualGateway);
            deployBtn.setEnabled(true);
            connectBtn.setEnabled(true);
            hostField.setEnabled(true);
            portField.setEnabled(true);
            verifyPinBtn.setEnabled(false);
            readChipBtn.setEnabled(false);
            disconnectBtn.setEnabled(false);
            modeBadge.setText("Chế độ: Thẻ ảo nội bộ");
            modeBadge.setForeground(UiTheme.COLOR_NEUTRAL_FG);
            updateSimulatorTrace.run();
        });

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row2.setBorder(BorderFactory.createTitledBorder("Kết nối trực tiếp Java Card Simulator (jcsl)"));
        row2.add(new JLabel("Host:"));
        row2.add(hostField);
        row2.add(new JLabel("Port:"));
        row2.add(portField);
        row2.add(new JLabel("PIN thẻ:"));
        row2.add(pinField);
        row2.add(deployBtn);
        row2.add(connectBtn);
        row2.add(verifyPinBtn);
        row2.add(readChipBtn);
        row2.add(disconnectBtn);

        JPanel topArea = new JPanel(new BorderLayout(4, 4));
        JPanel topRows = new JPanel(new GridLayout(2, 1, 0, 2));
        topRows.add(row1);
        topRows.add(row2);
        topArea.add(topRows, BorderLayout.CENTER);
        topArea.add(apduTraceLabel, BorderLayout.SOUTH);

        tabs.addTab("US10 · Chấm công", attendancePanel);
        tabs.addTab("US11 · Tính giờ làm", shiftPanel);
        tabs.addTab("US13 · Thống kê", statsPanel);
        tabs.addTab("US12 · Kiểm soát ra vào", accessPanel);
        tabs.addTab("Thử ảnh (dữ liệu giả)", new PhotoExperimentPanel(apduGateway));
        tabs.addChangeListener(e -> {
            int idx = tabs.getSelectedIndex();
            if (idx == 0) {
                attendancePanel.refreshData();
            } else if (idx == 1) {
                shiftPanel.refreshData();
            } else if (idx == 2) {
                statsPanel.refreshData();
            } else if (idx == 3) {
                accessPanel.refreshData();
            }
        });

        frame.add(topArea, BorderLayout.NORTH);
        frame.add(tabs, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private static final class DeployOutcome {
        final boolean deployed;
        final int count;
        final String errorMessage;

        DeployOutcome(boolean deployed, int count, String errorMessage) {
            this.deployed = deployed;
            this.count = count;
            this.errorMessage = errorMessage;
        }
    }

    private static final class ConnectOutcome {
        final boolean connected;
        final CardGateway.CardGatewayResult pinResult;
        final String errorMessage;

        ConnectOutcome(boolean connected, CardGateway.CardGatewayResult pinResult, String errorMessage) {
            this.connected = connected;
            this.pinResult = pinResult;
            this.errorMessage = errorMessage;
        }
    }
}
