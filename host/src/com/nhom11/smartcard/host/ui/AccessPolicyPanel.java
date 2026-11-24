package com.nhom11.smartcard.host.ui;

import com.nhom11.smartcard.host.model.AccessRule;
import com.nhom11.smartcard.host.model.Area;
import com.nhom11.smartcard.host.model.Employee;
import com.nhom11.smartcard.host.model.TemporaryGrant;
import com.nhom11.smartcard.host.provider.EmployeeProvider;
import com.nhom11.smartcard.host.provider.FileAccessPolicyProvider;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** Local administrative demo only; no login or authorization is provided by this panel. */
public final class AccessPolicyPanel extends JPanel {
    private final FileAccessPolicyProvider policies;
    private final EmployeeProvider employees;
    private final Runnable afterChange;
    private final DefaultTableModel ruleModel = UiTheme.readOnlyTableModel(
            "Mã", "Khu vực", "Phòng ban", "Chức vụ", "Kết hợp", "Bật");
    private final DefaultTableModel grantModel = UiTheme.readOnlyTableModel(
            "Mã", "Nhân viên", "Khu vực", "Bắt đầu", "Kết thúc", "Thu hồi", "Lý do");
    private final JTable ruleTable = new JTable(ruleModel);
    private final JTable grantTable = new JTable(grantModel);
    private List<AccessRule> visibleRules;
    private List<TemporaryGrant> visibleGrants;

    public AccessPolicyPanel(FileAccessPolicyProvider policies, EmployeeProvider employees, Runnable afterChange) {
        super(new BorderLayout(4, 4));
        this.policies = policies;
        this.employees = employees;
        this.afterChange = afterChange;
        add(new JLabel("Quản trị mô phỏng trên host — chưa có đăng nhập/phân quyền quản trị (US04)."), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("US01 · Quy tắc cố định", rulesPanel());
        tabs.addTab("US02 · Quyền tạm thời", grantsPanel());
        add(tabs, BorderLayout.CENTER);
        refreshData();
    }
    private JPanel rulesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Tạo quy tắc");
        JButton edit = new JButton("Sửa quy tắc");
        add.addActionListener(e -> editRule(null));
        edit.addActionListener(e -> {
            int row = ruleTable.getSelectedRow();
            if (row < 0) { message("Hãy chọn một quy tắc."); return; }
            editRule(visibleRules.get(ruleTable.convertRowIndexToModel(row)));
        });
        buttons.add(add); buttons.add(edit);
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(ruleTable), BorderLayout.CENTER);
        return panel;
    }
    private JPanel grantsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Cấp quyền");
        JButton edit = new JButton("Sửa quyền");
        JButton revoke = new JButton("Thu hồi quyền");
        add.addActionListener(e -> editGrant(null));
        edit.addActionListener(e -> {
            TemporaryGrant g = selectedGrant();
            if (g != null) editGrant(g);
        });
        revoke.addActionListener(e -> {
            TemporaryGrant g = selectedGrant();
            if (g == null) return;
            if (g.isRevoked()) { message("Quyền này đã bị thu hồi."); return; }
            if (JOptionPane.showConfirmDialog(this, "Thu hồi quyền " + g.getGrantId() + "?",
                    "Xác nhận", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            try {
                policies.saveGrant(new TemporaryGrant(g.getGrantId(), g.getEmployeeId(), g.getAreaId(),
                        g.getStartTime(), g.getEndTime(), true, g.getReason()));
                changed();
            } catch (Exception ex) { showError(ex); }
        });
        buttons.add(add); buttons.add(edit); buttons.add(revoke);
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(grantTable), BorderLayout.CENTER);
        panel.add(new JLabel("Giờ địa phương; hiệu lực gồm cả thời điểm bắt đầu và kết thúc."), BorderLayout.SOUTH);
        return panel;
    }
    private TemporaryGrant selectedGrant() {
        int row = grantTable.getSelectedRow();
        if (row < 0) { message("Hãy chọn một quyền tạm thời."); return null; }
        return visibleGrants.get(grantTable.convertRowIndexToModel(row));
    }
    private void editRule(AccessRule old) {
        JComboBox<Area> area = new JComboBox<>(policies.findAllAreas().toArray(Area[]::new));
        JTextField department = new JTextField(old == null || old.getDepartment() == null ? "" : old.getDepartment());
        JTextField position = new JTextField(old == null || old.getPosition() == null ? "" : old.getPosition());
        JComboBox<AccessRule.MatchMode> mode = new JComboBox<>(AccessRule.MatchMode.values());
        JCheckBox enabled = new JCheckBox("Đang bật", old == null || old.isEnabled());
        if (old != null) {
            selectArea(area, old.getAreaId());
            mode.setSelectedItem(old.getMatchMode());
        }
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        addField(form, "Khu vực", area);
        addField(form, "Phòng ban (có thể để trống)", department);
        addField(form, "Chức vụ (có thể để trống)", position);
        addField(form, "Khi điền cả hai: OR hoặc AND", mode);
        addField(form, "Trạng thái", enabled);
        if (JOptionPane.showConfirmDialog(this, form, old == null ? "Tạo quy tắc" : "Sửa quy tắc",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            Area a = (Area) area.getSelectedItem();
            if (a == null) throw new IllegalArgumentException("Chưa có khu vực.");
            AccessRule r = new AccessRule(old == null ? newId("RULE") : old.getRuleId(), a.getAreaId(),
                    department.getText(), position.getText(), (AccessRule.MatchMode) mode.getSelectedItem(), enabled.isSelected());
            policies.saveRule(r);
            changed();
        } catch (Exception ex) { showError(ex); }
    }
    private void editGrant(TemporaryGrant old) {
        if (old != null && old.isRevoked()) { message("Quyền đã thu hồi không thể sửa; hãy cấp quyền mới."); return; }
        JComboBox<Employee> employee = new JComboBox<>(employees.findAll().toArray(Employee[]::new));
        JComboBox<Area> area = new JComboBox<>(policies.findAllAreas().toArray(Area[]::new));
        JTextField start = new JTextField(old == null ? "" : old.getStartTime().toString().replace('T', ' '));
        JTextField end = new JTextField(old == null ? "" : old.getEndTime().toString().replace('T', ' '));
        JTextField reason = new JTextField(old == null ? "" : old.getReason());
        if (old != null) {
            selectArea(area, old.getAreaId());
            for (int i = 0; i < employee.getItemCount(); i++) {
                if (employee.getItemAt(i).getEmployeeId().equals(old.getEmployeeId())) employee.setSelectedIndex(i);
            }
        }
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        addField(form, "Nhân viên", employee);
        addField(form, "Khu vực", area);
        addField(form, "Bắt đầu (yyyy-MM-dd HH:mm[:ss])", start);
        addField(form, "Kết thúc (yyyy-MM-dd HH:mm[:ss])", end);
        addField(form, "Lý do", reason);
        if (JOptionPane.showConfirmDialog(this, form, old == null ? "Cấp quyền" : "Sửa quyền",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            Employee emp = (Employee) employee.getSelectedItem();
            Area a = (Area) area.getSelectedItem();
            if (emp == null || a == null) throw new IllegalArgumentException("Chưa có nhân viên hoặc khu vực.");
            LocalDateTime from = LocalDateTime.parse(start.getText().trim().replace(' ', 'T'));
            LocalDateTime to = LocalDateTime.parse(end.getText().trim().replace(' ', 'T'));
            TemporaryGrant g = new TemporaryGrant(old == null ? newId("GRANT") : old.getGrantId(),
                    emp.getEmployeeId(), a.getAreaId(), from, to, false, reason.getText().trim());
            policies.saveGrant(g);
            changed();
        } catch (Exception ex) { showError(ex); }
    }
    private static String newId(String prefix) { return prefix + "-" + UUID.randomUUID(); }
    private static void selectArea(JComboBox<Area> box, String id) {
        for (int i = 0; i < box.getItemCount(); i++) if (box.getItemAt(i).getAreaId().equals(id)) box.setSelectedIndex(i);
    }
    private static void addField(JPanel p, String text, java.awt.Component field) {
        p.add(new JLabel(text)); p.add(field);
    }
    private void changed() { refreshData(); afterChange.run(); }
    private void message(String text) { JOptionPane.showMessageDialog(this, text); }
    private void showError(Exception e) {
        JOptionPane.showMessageDialog(this, "Không lưu được: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
    }
    public void refreshData() {
        visibleRules = policies.listRules();
        visibleGrants = policies.listGrants();
        ruleModel.setRowCount(0);
        for (AccessRule r : visibleRules) ruleModel.addRow(new Object[] {
                r.getRuleId(), r.getAreaId(), r.getDepartment(), r.getPosition(), r.getMatchMode(), r.isEnabled()});
        grantModel.setRowCount(0);
        for (TemporaryGrant g : visibleGrants) grantModel.addRow(new Object[] {
                g.getGrantId(), g.getEmployeeId(), g.getAreaId(), g.getStartTime(), g.getEndTime(), g.isRevoked(), g.getReason()});
    }
}
