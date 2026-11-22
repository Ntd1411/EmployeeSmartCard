package com.nhom11.smartcard.host.ui;

import com.nhom11.smartcard.host.model.AttendanceEventType;
import com.nhom11.smartcard.host.model.AttendanceRecord;
import com.nhom11.smartcard.host.model.Employee;
import com.nhom11.smartcard.host.model.VirtualCard;
import com.nhom11.smartcard.host.provider.EmployeeProvider;
import com.nhom11.smartcard.host.provider.VirtualCardProvider;
import com.nhom11.smartcard.host.service.AttendanceService;
import com.nhom11.smartcard.host.service.PunchResult;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 * Swing panel for US10: check-in and check-out punching and live punch history.
 */
public class AttendancePanel extends JPanel {

    private final EmployeeProvider employeeProvider;
    private final VirtualCardProvider cardProvider;
    private final AttendanceService attendanceService;
    private final BooleanSupplier securityCheckEnabled;
    private final Runnable onGatewayActivity;

    private final JComboBox<CardOption> cardCombo = new JComboBox<>();
    private final JLabel cardInfoLabel = new JLabel("-");
    private final JTextField timestampField = new JTextField(19);
    private final JCheckBox useCurrentTimeCheckbox = new JCheckBox("Tự lấy giờ hiện tại", true);
    private final JCheckBox allowOrphanCheckbox = new JCheckBox("Cho phép ghi ca thiếu cặp (test US11)");
    private final JLabel statusBanner = new JLabel("Chọn thẻ ảo và nhấn Vào ca / Ra ca để chấm công.");
    private final DefaultTableModel tableModel = UiTheme.readOnlyTableModel(
            "Mã bản ghi", "Mã NV", "Họ tên", "Mã thẻ", "Loại", "Thời điểm", "Nguồn", "Ghi chú");

    public AttendancePanel(
            EmployeeProvider employeeProvider,
            VirtualCardProvider cardProvider,
            AttendanceService attendanceService,
            BooleanSupplier securityCheckEnabled) {
        this(employeeProvider, cardProvider, attendanceService, securityCheckEnabled, () -> { });
    }

    public AttendancePanel(
            EmployeeProvider employeeProvider,
            VirtualCardProvider cardProvider,
            AttendanceService attendanceService,
            BooleanSupplier securityCheckEnabled,
            Runnable onGatewayActivity) {
        this.employeeProvider = employeeProvider;
        this.cardProvider = cardProvider;
        this.attendanceService = attendanceService;
        this.securityCheckEnabled = securityCheckEnabled;
        this.onGatewayActivity = onGatewayActivity;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildControlPanel(), BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);
        add(statusBanner, BorderLayout.SOUTH);
        UiTheme.setNeutralBanner(statusBanner, "Chọn thẻ ảo và nhấn Vào ca / Ra ca để chấm công.");

        refreshData();
    }

    private JPanel buildControlPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("US10 — Chấm công vào ca / ra ca"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        form.add(new JLabel("Thẻ ảo:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        cardCombo.addActionListener(e -> updateSelectedCardInfo());
        form.add(cardCombo, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        form.add(new JLabel("Thông tin thẻ:"), gbc);

        gbc.gridx = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        cardInfoLabel.setFont(cardInfoLabel.getFont().deriveFont(Font.BOLD));
        form.add(cardInfoLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        form.add(new JLabel("Thời điểm (yyyy-MM-dd HH:mm:ss):"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        timestampField.setText(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(UiTheme.DATE_TIME_FMT));
        timestampField.setEnabled(false);
        form.add(timestampField, gbc);

        useCurrentTimeCheckbox.addActionListener(e -> {
            boolean autoNow = useCurrentTimeCheckbox.isSelected();
            timestampField.setEnabled(!autoNow);
            if (autoNow) {
                timestampField.setText(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(UiTheme.DATE_TIME_FMT));
            }
        });

        JButton nowButton = new JButton("Giờ hiện tại");
        nowButton.addActionListener(e -> timestampField.setText(
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(UiTheme.DATE_TIME_FMT)));

        JPanel timeTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        timeTools.add(useCurrentTimeCheckbox);
        timeTools.add(nowButton);
        timeTools.add(allowOrphanCheckbox);

        gbc.gridx = 2;
        gbc.gridwidth = 2;
        form.add(timeTools, gbc);

        JButton checkInBtn = new JButton("Vào ca (CHECK_IN)");
        checkInBtn.addActionListener(e -> executePunch(AttendanceEventType.CHECK_IN));

        JButton checkOutBtn = new JButton("Ra ca (CHECK_OUT)");
        checkOutBtn.addActionListener(e -> executePunch(AttendanceEventType.CHECK_OUT));

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonRow.add(checkInBtn);
        buttonRow.add(checkOutBtn);

        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        form.add(buttonRow, gbc);

        return form;
    }

    private JScrollPane buildTablePanel() {
        JTable table = new JTable(tableModel);
        UiTheme.styleTable(table);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Danh sách bản ghi chấm công (mới nhất ở trên)"));
        return scrollPane;
    }

    private void executePunch(AttendanceEventType eventType) {
        CardOption selected = (CardOption) cardCombo.getSelectedItem();
        if (selected == null) {
            UiTheme.setBanner(statusBanner, false, "Không có thẻ ảo nào được chọn.");
            return;
        }
        LocalDateTime ts;
        try {
            if (useCurrentTimeCheckbox.isSelected()) {
                ts = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
                timestampField.setText(ts.format(UiTheme.DATE_TIME_FMT));
            } else {
                ts = UiTheme.parseDateTime(timestampField.getText());
            }
        } catch (IllegalArgumentException ex) {
            UiTheme.setBanner(statusBanner, false, ex.getMessage());
            return;
        }

        PunchResult result = attendanceService.punch(
                selected.cardId,
                eventType,
                ts,
                securityCheckEnabled.getAsBoolean(),
                allowOrphanCheckbox.isSelected());

        String swHex = String.format("0x%04X", result.getCardStatusWord());
        UiTheme.setBanner(statusBanner, result.isAccepted(),
                result.getMessage() + " (SW=" + swHex + ")");
        refreshTable();
        onGatewayActivity.run();
    }

    public String getSelectedCardId() {
        Object selected = cardCombo.getSelectedItem();
        return selected instanceof CardOption ? ((CardOption) selected).cardId : null;
    }

    public final void refreshData() {
        String prevCardId = cardCombo.getSelectedItem() instanceof CardOption
                ? ((CardOption) cardCombo.getSelectedItem()).cardId
                : null;
        cardCombo.removeAllItems();
        for (VirtualCard card : cardProvider.findAll()) {
            String empName = employeeProvider.findById(card.getEmployeeId())
                    .map(Employee::getFullName)
                    .orElse("Không rõ");
            cardCombo.addItem(new CardOption(card.getCardId(),
                    card.getCardId() + " — " + empName + " [" + card.getStatus() + "]"));
        }
        if (prevCardId != null) {
            for (int i = 0; i < cardCombo.getItemCount(); i++) {
                if (prevCardId.equals(cardCombo.getItemAt(i).cardId)) {
                    cardCombo.setSelectedIndex(i);
                    break;
                }
            }
        }
        updateSelectedCardInfo();
        refreshTable();
    }

    private void updateSelectedCardInfo() {
        CardOption selected = (CardOption) cardCombo.getSelectedItem();
        if (selected == null) {
            cardInfoLabel.setText("-");
            return;
        }
        VirtualCard card = cardProvider.findByCardId(selected.cardId).orElse(null);
        if (card == null) {
            cardInfoLabel.setText("Không tìm thấy thẻ");
            return;
        }
        String empInfo = employeeProvider.findById(card.getEmployeeId())
                .map(e -> e.getFullName() + " (" + e.getDepartment() + ")")
                .orElse(card.getEmployeeId());
        cardInfoLabel.setText(empInfo
                + " | Trạng thái: " + card.getStatus()
                + " | PIN: " + (card.isPinVerified() ? "OK" : "Chưa")
                + " | Crypto: " + (card.isCryptoVerified() ? "OK" : "Chưa"));
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        List<AttendanceRecord> sorted = new ArrayList<>(attendanceService.listAllRecords());
        Collections.reverse(sorted);
        sorted.sort(Comparator.comparing(AttendanceRecord::getTimestamp).reversed());
        for (AttendanceRecord r : sorted) {
            String empName = employeeProvider.findById(r.getEmployeeId())
                    .map(Employee::getFullName)
                    .orElse(r.getEmployeeId());
            tableModel.addRow(new Object[] {
                    r.getRecordId(),
                    r.getEmployeeId(),
                    empName,
                    r.getCardId(),
                    r.getEventType().name(),
                    r.getTimestamp().format(UiTheme.DATE_TIME_FMT),
                    r.getSource(),
                    r.getNote()
            });
        }
    }

    private static final class CardOption {
        final String cardId;
        final String label;

        CardOption(String cardId, String label) {
            this.cardId = cardId;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
