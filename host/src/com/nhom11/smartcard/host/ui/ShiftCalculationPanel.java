package com.nhom11.smartcard.host.ui;

import com.nhom11.smartcard.host.model.Employee;
import com.nhom11.smartcard.host.model.ShiftStatus;
import com.nhom11.smartcard.host.model.WorkShift;
import com.nhom11.smartcard.host.provider.EmployeeProvider;
import com.nhom11.smartcard.host.service.ShiftPairingService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 * Swing panel for US11: shift pairing and automatic work-hour calculation.
 */
public class ShiftCalculationPanel extends JPanel {

    private final EmployeeProvider employeeProvider;
    private final ShiftPairingService shiftPairingService;

    private final JComboBox<EmployeeOption> employeeCombo = new JComboBox<>();
    private final JTextField fromDateField = new JTextField(10);
    private final JTextField toDateField = new JTextField(10);
    private final JLabel statusBanner = new JLabel("Chọn nhân viên và khoảng ngày rồi nhấn Tính giờ làm.");
    private final DefaultTableModel tableModel = UiTheme.readOnlyTableModel(
            "Mã ca", "Mã NV", "Họ tên", "Ngày làm", "Giờ vào", "Giờ ra", "Số phút", "Số giờ", "Trạng thái");

    public ShiftCalculationPanel(
            EmployeeProvider employeeProvider,
            ShiftPairingService shiftPairingService) {
        this.employeeProvider = employeeProvider;
        this.shiftPairingService = shiftPairingService;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        LocalDate today = LocalDate.now();
        fromDateField.setText(today.minusDays(7).format(UiTheme.DATE_FMT));
        toDateField.setText(today.format(UiTheme.DATE_FMT));

        add(buildFilterBar(), BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);
        add(statusBanner, BorderLayout.SOUTH);

        refreshData();
    }

    private JPanel buildFilterBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        bar.setBorder(BorderFactory.createTitledBorder("US11 — Tự động tính thời gian làm việc"));

        bar.add(new JLabel("Nhân viên:"));
        bar.add(employeeCombo);
        bar.add(new JLabel("Từ ngày:"));
        bar.add(fromDateField);
        bar.add(new JLabel("Đến ngày:"));
        bar.add(toDateField);

        JButton calcButton = new JButton("Tính thời gian làm việc");
        calcButton.addActionListener(e -> recalculate());
        bar.add(calcButton);

        return bar;
    }

    private JScrollPane buildTablePanel() {
        JTable table = new JTable(tableModel);
        UiTheme.styleTable(table);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Kết quả ghép cặp ca & xử lý ca thiếu dữ liệu"));
        return scrollPane;
    }

    public final void refreshData() {
        String prevId = employeeCombo.getSelectedItem() instanceof EmployeeOption
                ? ((EmployeeOption) employeeCombo.getSelectedItem()).employeeId
                : null;
        employeeCombo.removeAllItems();
        employeeCombo.addItem(new EmployeeOption(null, "-- Tất cả nhân viên --"));
        for (Employee emp : employeeProvider.findAll()) {
            employeeCombo.addItem(new EmployeeOption(
                    emp.getEmployeeId(),
                    emp.getEmployeeId() + " — " + emp.getFullName()));
        }
        if (prevId != null) {
            for (int i = 0; i < employeeCombo.getItemCount(); i++) {
                if (prevId.equals(employeeCombo.getItemAt(i).employeeId)) {
                    employeeCombo.setSelectedIndex(i);
                    break;
                }
            }
        }
        recalculate();
    }

    private void recalculate() {
        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = UiTheme.parseDate(fromDateField.getText());
            toDate = UiTheme.parseDate(toDateField.getText());
        } catch (IllegalArgumentException ex) {
            UiTheme.setBanner(statusBanner, false, ex.getMessage());
            return;
        }

        EmployeeOption selected = (EmployeeOption) employeeCombo.getSelectedItem();
        String empFilter = selected == null ? null : selected.employeeId;

        List<WorkShift> shifts;
        try {
            shifts = shiftPairingService.calculateShifts(empFilter, fromDate, toDate);
        } catch (IllegalArgumentException ex) {
            UiTheme.setBanner(statusBanner, false, ex.getMessage());
            return;
        }

        tableModel.setRowCount(0);
        int completeCount = 0;
        int incompleteCount = 0;

        for (WorkShift s : shifts) {
            if (s.getStatus() == ShiftStatus.COMPLETE) {
                completeCount++;
            } else {
                incompleteCount++;
            }
            String empName = employeeProvider.findById(s.getEmployeeId())
                    .map(Employee::getFullName)
                    .orElse(s.getEmployeeId());
            tableModel.addRow(new Object[] {
                    s.getShiftId(),
                    s.getEmployeeId(),
                    empName,
                    s.getWorkDate().format(UiTheme.DATE_FMT),
                    s.getCheckInTime() == null ? "-" : s.getCheckInTime().format(UiTheme.DATE_TIME_FMT),
                    s.getCheckOutTime() == null ? "-" : s.getCheckOutTime().format(UiTheme.DATE_TIME_FMT),
                    s.getWorkedMinutes(),
                    s.getWorkedHours().toPlainString(),
                    formatShiftStatus(s.getStatus())
            });
        }

        UiTheme.setNeutralBanner(statusBanner,
                "Tổng số ca: " + shifts.size()
                        + " | Đủ cặp: " + completeCount
                        + " | Thiếu dữ liệu / Lỗi: " + incompleteCount);
    }

    private static String formatShiftStatus(ShiftStatus status) {
        switch (status) {
            case COMPLETE:
                return "Đủ ca (COMPLETE)";
            case MISSING_CHECK_OUT:
                return "Thiếu giờ ra (MISSING_CHECK_OUT)";
            case MISSING_CHECK_IN:
                return "Thiếu giờ vào (MISSING_CHECK_IN)";
            case INVALID_ORDER:
                return "Sai thứ tự (INVALID_ORDER)";
            default:
                return status.name();
        }
    }

    private static final class EmployeeOption {
        final String employeeId;
        final String label;

        EmployeeOption(String employeeId, String label) {
            this.employeeId = employeeId;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
