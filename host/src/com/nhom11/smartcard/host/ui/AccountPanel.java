package com.nhom11.smartcard.host.ui;

import com.nhom11.smartcard.host.service.AccountService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** Local account administration. Authentication is not card-side security. */
public final class AccountPanel extends JPanel {
    private final AccountService accounts;
    private final Runnable onSessionChanged;
    private final JLabel sessionLabel = new JLabel();
    private final DefaultTableModel model = UiTheme.readOnlyTableModel("Tài khoản", "Vai trò");
    private final JTable table = new JTable(model);
    private final JButton setup=new JButton("Tạo quản trị viên đầu tiên");
    private final JButton login=new JButton("Đăng nhập"), logout=new JButton("Đăng xuất");
    private final JButton add=new JButton("Tạo tài khoản"), edit=new JButton("Sửa tài khoản"), delete=new JButton("Xóa tài khoản");
    public AccountPanel(AccountService accounts, Runnable onSessionChanged) {
        this.accounts = accounts; this.onSessionChanged = onSessionChanged;
        setLayout(new BorderLayout(6,6));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(sessionLabel); actions.add(setup); actions.add(login); actions.add(logout); actions.add(add); actions.add(edit); actions.add(delete);
        add(actions,BorderLayout.NORTH); add(new JScrollPane(table),BorderLayout.CENTER);
        setup.addActionListener(e -> credentials(true)); login.addActionListener(e -> credentials(false));
        logout.addActionListener(e -> { accounts.logout(); refresh(); onSessionChanged.run(); });
        add.addActionListener(e -> editAccount(false)); edit.addActionListener(e -> editAccount(true));
        delete.addActionListener(e -> {
            int row=table.getSelectedRow(); if(row<0) return;
            String name=(String)model.getValueAt(table.convertRowIndexToModel(row),0);
            if(JOptionPane.showConfirmDialog(this,"Xóa tài khoản " + name + "?","Xác nhận",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION) return;
            try { accounts.delete(name); refresh(); onSessionChanged.run(); } catch(Exception ex) { showError(ex); }
        });
        refresh();
    }
    public void refresh() {
        sessionLabel.setText(accounts.currentUser()==null ? "Chưa đăng nhập" : "Đã đăng nhập: " + accounts.currentUser() + (accounts.isAdmin()?" (quản trị)":""));
        setup.setVisible(accounts.needsSetup());
        login.setVisible(accounts.currentUser()==null && !accounts.needsSetup());
        logout.setVisible(accounts.currentUser()!=null);
        add.setVisible(accounts.isAdmin()); edit.setVisible(accounts.isAdmin()); delete.setVisible(accounts.isAdmin());
        model.setRowCount(0);
        if(accounts.isAdmin()) for(String entry:accounts.listUsers()) { String[] p=entry.split("\\t",-1); model.addRow(p); }
    }
    private void credentials(boolean setup) {
        if(setup && !accounts.needsSetup()) { showError(new IllegalStateException("Tài khoản đã được thiết lập.")); return; }
        JTextField name=new JTextField(); JPasswordField pass=new JPasswordField();
        JPanel p=new JPanel(new GridLayout(0,2)); p.add(new JLabel("Tài khoản:")); p.add(name); p.add(new JLabel("Mật khẩu:")); p.add(pass);
        if(JOptionPane.showConfirmDialog(this,p,setup?"Tạo quản trị viên đầu tiên":"Đăng nhập",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) return;
        char[] password=pass.getPassword();
        try {
            if(setup) { accounts.setup(name.getText(),password); JOptionPane.showMessageDialog(this,"Đã tạo quản trị viên. Hãy đăng nhập."); }
            else if(!accounts.login(name.getText(),password)) JOptionPane.showMessageDialog(this,"Sai tài khoản hoặc mật khẩu.");
            refresh(); onSessionChanged.run();
        } catch(Exception ex) { showError(ex); }
        finally { Arrays.fill(password,'\0'); pass.setText(""); }
    }
    private void editAccount(boolean edit) {
        try { accounts.requireAdmin(); } catch(Exception ex) { showError(ex); return; }
        int row=table.getSelectedRow(); if(edit && row<0) { showError(new IllegalArgumentException("Chọn tài khoản cần sửa.")); return; }
        JTextField name=new JTextField(edit?(String)model.getValueAt(table.convertRowIndexToModel(row),0):""); name.setEditable(!edit);
        JPasswordField pass=new JPasswordField(); JComboBox<AccountService.Role> role=new JComboBox<>(AccountService.Role.values());
        if(edit) role.setSelectedItem(AccountService.Role.valueOf((String)model.getValueAt(table.convertRowIndexToModel(row),1)));
        JPanel p=new JPanel(new GridLayout(0,2)); p.add(new JLabel("Tài khoản:")); p.add(name); p.add(new JLabel(edit?"Mật khẩu mới (để trống nếu giữ):":"Mật khẩu:")); p.add(pass); p.add(new JLabel("Vai trò:")); p.add(role);
        if(JOptionPane.showConfirmDialog(this,p,edit?"Sửa tài khoản":"Tạo tài khoản",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) return;
        char[] password=pass.getPassword();
        try { if(edit) accounts.update(name.getText(),password,(AccountService.Role)role.getSelectedItem()); else accounts.create(name.getText(),password,(AccountService.Role)role.getSelectedItem()); refresh(); onSessionChanged.run(); }
        catch(Exception ex) { showError(ex); } finally { Arrays.fill(password,'\0'); pass.setText(""); }
    }
    private void showError(Exception ex) { JOptionPane.showMessageDialog(this,ex.getMessage(),"Tài khoản",JOptionPane.ERROR_MESSAGE); }
}
