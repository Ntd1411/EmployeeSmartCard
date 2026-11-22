package com.nhom11.smartcard.host.ui;

import com.nhom11.smartcard.host.model.AttendanceStatistics;
import com.nhom11.smartcard.host.model.Employee;
import com.nhom11.smartcard.host.provider.EmployeeProvider;
import com.nhom11.smartcard.host.service.AttendanceStatisticsService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;

/**
 * Swing panel for US13: attendance statistics across employees, departments, and date ranges.
 */
public class StatisticsPanel extends JPanel {

    private final EmployeeProvider employeeProvider;
    private final AttendanceStatisticsService statisticsService;

    private final JComboBox<FilterOption> employeeCombo = new JComboBox<>();
    private final JComboBox<FilterOption> departmentCombo = new JComboBox<>();
    private final JTextField fromDateField = new JTextField(10);
    private final JTextField toDateField = new JTextField(10);

    private final JLabel kpiPunches = createKpiLabel("Tổng lượt chấm công", "0");
    private final JLabel kpiCompleted = createKpiLabel("Ca hoàn chỉnh", "0");
    private final JLabel kpiIncomplete = createKpiLabel("Ca thiếu dữ liệu", "0");
    private final JLabel kpiWorkDays = createKpiLabel("Tổng ngày công", "0");
    private final JLabel kpiTotalHours = createKpiLabel("Tổng giờ làm", "0.00");

    private final JLabel statusBanner = new JLabel("Chọn bộ lọc và nhấn Thống kê.");
    private final DefaultTableModel tableModel = UiTheme.readOnlyTableModel(
            "Mã NV", "Họ tên", "Phòng ban", "Tổng lượt", "Lượt vào", "Lượt ra",
            "Ca đủ", "Ca thiếu", "Ngày công", "Tổng giờ", "TB giờ/ngày");

    public StatisticsPanel(
            EmployeeProvider employeeProvider,
            AttendanceStatisticsService statisticsService) {
        this.employeeProvider = employeeProvider;
        this.statisticsService = statisticsService;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        LocalDate today = LocalDate.now();
        fromDateField.setText(today.minusDays(7).format(UiTheme.DATE_FMT));
        toDateField.setText(today.format(UiTheme.DATE_FMT));

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(buildFilterBar(), BorderLayout.NORTH);
        top.add(buildKpiRow(), BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);
        add(statusBanner, BorderLayout.SOUTH);

        refreshData();
    }

    private JPanel buildFilterBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        bar.setBorder(BorderFactory.createTitledBorder("US13 — Thống kê dữ liệu chấm công"));

        bar.add(new JLabel("Nhân viên:"));
        bar.add(employeeCombo);
        bar.add(new JLabel("Phòng ban:"));
        bar.add(departmentCombo);
        bar.add(new JLabel("Từ ngày:"));
        bar.add(fromDateField);
        bar.add(new JLabel("Đến ngày:"));
        bar.add(toDateField);

        JButton runButton = new JButton("Thống kê");
        runButton.addActionListener(e -> runStatistics());
        bar.add(runButton);

        return bar;
    }

    private JPanel buildKpiRow() {
        JPanel row = new JPanel(new GridLayout(1, 5, 10, 0));
        row.add(kpiPunches);
        row.add(kpiCompleted);
        row.add(kpiIncomplete);
        row.add(kpiWorkDays);
        row.add(kpiTotalHours);
        return row;
    }

    private JScrollPane buildTablePanel() {
        JTable table = new JTable(tableModel);
        UiTheme.styleTable(table);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Bảng tổng hợp theo nhân viên"));
        return scrollPane;
    }

    public final void refreshData() {
        employeeCombo.removeAllItems();
        employeeCombo.addItem(new FilterOption(null, "-- Tất cả nhân viên --"));

        Set<String> departments = new LinkedHashSet<>();
        for (Employee emp : employeeProvider.findAll()) {
            employeeCombo.addItem(new FilterOption(
                    emp.getEmployeeId(),
                    emp.getEmployeeId() + " — " + emp.getFullName()));
            departments.add(emp.getDepartment());
        }

        departmentCombo.removeAllItems();
        departmentCombo.addItem(new FilterOption(null, "-- Tất cả phòng ban --"));
        for (String dept : departments) {
            departmentCombo.addItem(new FilterOption(dept, dept));
        }

        runStatistics();
    }

    private void runStatistics() {
        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = UiTheme.parseDate(fromDateField.getText());
            toDate = UiTheme.parseDate(toDateField.getText());
        } catch (IllegalArgumentException ex) {
            UiTheme.setBanner(statusBanner, false, ex.getMessage());
            return;
        }

        FilterOption empOpt = (FilterOption) employeeCombo.getSelectedItem();
        FilterOption deptOpt = (FilterOption) departmentCombo.getSelectedItem();

        List<AttendanceStatistics> rows;
        try {
            rows = statisticsService.calculateStatistics(
                    empOpt == null ? null : empOpt.value,
                    deptOpt == null ? null : deptOpt.value,
                    fromDate,
                    toDate);
        } catch (IllegalArgumentException ex) {
            UiTheme.setBanner(statusBanner, false, ex.getMessage());
            return;
        }

        tableModel.setRowCount(0);
        int sumPunches = 0;
        int sumCompleted = 0;
        int sumIncomplete = 0;
        int sumWorkDays = 0;
        BigDecimal sumHours = BigDecimal.ZERO;

        for (AttendanceStatistics s : rows) {
            sumPunches += s.getTotalPunches();
            sumCompleted += s.getCompletedShifts();
            sumIncomplete += s.getIncompleteShifts();
            sumWorkDays += s.getWorkDays();
            sumHours = sumHours.add(s.getTotalHours());

            tableModel.addRow(new Object[] {
                    s.getEmployeeId(),
                    s.getFullName(),
                    s.getDepartment(),
                    s.getTotalPunches(),
                    s.getCheckInCount(),
                    s.getCheckOutCount(),
                    s.getCompletedShifts(),
                    s.getIncompleteShifts(),
                    s.getWorkDays(),
                    s.getTotalHours().toPlainString(),
                    s.getAverageHoursPerWorkDay().toPlainString()
            });
        }

        updateKpiLabel(kpiPunches, "Tổng lượt chấm công", String.valueOf(sumPunches));
        updateKpiLabel(kpiCompleted, "Ca hoàn chỉnh", String.valueOf(sumCompleted));
        updateKpiLabel(kpiIncomplete, "Ca thiếu dữ liệu", String.valueOf(sumIncomplete));
        updateKpiLabel(kpiWorkDays, "Tổng ngày công", String.valueOf(sumWorkDays));
        updateKpiLabel(kpiTotalHours, "Tổng giờ làm", sumHours.setScale(2).toPlainString());

        if (sumPunches == 0) {
            UiTheme.setNeutralBanner(statusBanner,
                    "Không có dữ liệu chấm công trong khoảng " + fromDate + " → " + toDate + ".");
        } else {
            UiTheme.setNeutralBanner(statusBanner,
                    "Đã tổng hợp " + rows.size() + " nhân viên từ " + fromDate + " đến " + toDate + ".");
        }
    }

    private static JLabel createKpiLabel(String title, String value) {
        JLabel label = new JLabel("", SwingConstants.CENTER);
        label.setOpaque(true);
        label.setBackground(UiTheme.COLOR_NEUTRAL_BG);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(203, 213, 225)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        updateKpiLabel(label, title, value);
        return label;
    }

    private static void updateKpiLabel(JLabel label, String title, String value) {
        label.setText("<html><div style='text-align:center;'>" + title + "<br><span style='font-size:14px;'>"
                + value + "</span></div></html>");
    }

    private static final class FilterOption {
        final String value;
        final String label;

        FilterOption(String value, String label) {
            this.value = value;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
