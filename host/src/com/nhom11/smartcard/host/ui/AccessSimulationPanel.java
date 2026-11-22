package com.nhom11.smartcard.host.ui;

import com.nhom11.smartcard.host.model.AccessAttemptLog;
import com.nhom11.smartcard.host.model.AccessDecision;
import com.nhom11.smartcard.host.model.Area;
import com.nhom11.smartcard.host.model.CardStatus;
import com.nhom11.smartcard.host.model.Employee;
import com.nhom11.smartcard.host.model.VirtualCard;
import com.nhom11.smartcard.host.provider.AccessPolicyProvider;
import com.nhom11.smartcard.host.provider.ApduSimulatorCardGateway;
import com.nhom11.smartcard.host.provider.CardGateway;
import com.nhom11.smartcard.host.provider.EmployeeProvider;
import com.nhom11.smartcard.host.provider.VirtualCardProvider;
import com.nhom11.smartcard.host.service.AccessControlService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
 * Swing panel for US12: interactive area access simulation and attempt audit log.
 */
public class AccessSimulationPanel extends JPanel {

    private final EmployeeProvider employeeProvider;
    private final VirtualCardProvider cardProvider;
    private final AccessPolicyProvider policyProvider;
    private final AccessControlService accessControlService;
    private final BooleanSupplier securityCheckEnabled;
    private final Runnable onCardStateChanged;
    private final ApduSimulatorCardGateway simulatorGateway;
    private final Runnable onGatewayActivity;

    private final JComboBox<SelectOption> cardCombo = new JComboBox<>();
    private final JComboBox<SelectOption> areaCombo = new JComboBox<>();
    private final JTextField attemptTimeField = new JTextField(19);
    private final JComboBox<CardStatus> quickStatusCombo = new JComboBox<>(CardStatus.values());
    private final JCheckBox quickPinBox = new JCheckBox("PIN đã xác thực");
    private final JCheckBox quickCryptoBox = new JCheckBox("AES/RSA hợp lệ");

    private final JLabel decisionBanner = new JLabel("Chọn thẻ, khu vực, thời điểm và nhấn Kiểm tra ra vào.");
    private final DefaultTableModel tableModel = UiTheme.readOnlyTableModel(
            "Mã log", "Mã thẻ", "Mã NV", "Khu vực", "Thời điểm", "Kết quả", "Mã lý do", "Quy tắc khớp", "Chi tiết");

    private boolean updatingControls = false;

    public AccessSimulationPanel(
            EmployeeProvider employeeProvider,
            VirtualCardProvider cardProvider,
            AccessPolicyProvider policyProvider,
            AccessControlService accessControlService,
            BooleanSupplier securityCheckEnabled,
            Runnable onCardStateChanged) {
        this(employeeProvider, cardProvider, policyProvider, accessControlService,
                securityCheckEnabled, onCardStateChanged, null, () -> { });
    }

    public AccessSimulationPanel(
            EmployeeProvider employeeProvider,
            VirtualCardProvider cardProvider,
            AccessPolicyProvider policyProvider,
            AccessControlService accessControlService,
            BooleanSupplier securityCheckEnabled,
            Runnable onCardStateChanged,
            ApduSimulatorCardGateway simulatorGateway,
            Runnable onGatewayActivity) {
        this.employeeProvider = employeeProvider;
        this.cardProvider = cardProvider;
        this.policyProvider = policyProvider;
        this.accessControlService = accessControlService;
        this.securityCheckEnabled = securityCheckEnabled;
        this.onCardStateChanged = onCardStateChanged;
        this.simulatorGateway = simulatorGateway;
        this.onGatewayActivity = onGatewayActivity;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildSimulationForm(), BorderLayout.NORTH);
        add(buildLogTable(), BorderLayout.CENTER);
        decisionBanner.setFont(decisionBanner.getFont().deriveFont(Font.BOLD, 14f));
        UiTheme.setNeutralBanner(decisionBanner, "Chọn thẻ, khu vực, thời điểm và nhấn Kiểm tra ra vào.");
        add(decisionBanner, BorderLayout.SOUTH);

        refreshData();
    }

    private JPanel buildSimulationForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("US12 — Mô phỏng kiểm soát ra vào khu vực"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        form.add(new JLabel("Thẻ ảo:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        cardCombo.addActionListener(e -> syncQuickCardControls());
        form.add(cardCombo, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        form.add(new JLabel("Khu vực:"), gbc);

        gbc.gridx = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        form.add(areaCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        form.add(new JLabel("Thời điểm (yyyy-MM-dd HH:mm:ss):"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        attemptTimeField.setText(LocalDateTime.now().format(UiTheme.DATE_TIME_FMT));
        form.add(attemptTimeField, gbc);

        JButton nowBtn = new JButton("Hiện tại");
        nowBtn.addActionListener(e -> setAttemptTimeOffset(0));
        JButton futureBtn = new JButton("+3 ngày (tương lai)");
        futureBtn.addActionListener(e -> setAttemptTimeOffset(3));
        JButton pastBtn = new JButton("-3 ngày (quá khứ)");
        pastBtn.addActionListener(e -> setAttemptTimeOffset(-3));

        JPanel timeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        timeButtons.add(nowBtn);
        timeButtons.add(futureBtn);
        timeButtons.add(pastBtn);

        gbc.gridx = 2;
        gbc.gridwidth = 2;
        form.add(timeButtons, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        form.add(new JLabel("Điều chỉnh thẻ mô phỏng:"), gbc);

        JPanel quickPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        quickPanel.add(quickStatusCombo);
        quickPanel.add(quickPinBox);
        quickPanel.add(quickCryptoBox);
        JButton applyCardBtn = new JButton("Áp dụng trạng thái thẻ");
        applyCardBtn.addActionListener(e -> applyQuickCardControls());
        quickPanel.add(applyCardBtn);

        gbc.gridx = 1;
        gbc.gridwidth = 3;
        form.add(quickPanel, gbc);

        JButton evaluateBtn = new JButton("Kiểm tra ra vào");
        evaluateBtn.setFont(evaluateBtn.getFont().deriveFont(Font.BOLD));
        evaluateBtn.addActionListener(e -> runEvaluation());

        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        form.add(evaluateBtn, gbc);

        return form;
    }

    private JScrollPane buildLogTable() {
        JTable table = new JTable(tableModel);
        UiTheme.styleTable(table);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Nhật ký kiểm soát ra vào khu vực"));
        return scrollPane;
    }

    private void setAttemptTimeOffset(int daysOffset) {
        LocalDateTime dt = LocalDateTime.now().plusDays(daysOffset);
        attemptTimeField.setText(dt.format(UiTheme.DATE_TIME_FMT));
    }

    private void syncQuickCardControls() {
        SelectOption opt = (SelectOption) cardCombo.getSelectedItem();
        if (opt == null) {
            return;
        }
        VirtualCard card = cardProvider.findByCardId(opt.id).orElse(null);
        if (card == null) {
            return;
        }
        updatingControls = true;
        quickStatusCombo.setSelectedItem(card.getStatus());
        quickPinBox.setSelected(card.isPinVerified());
        quickCryptoBox.setSelected(card.isCryptoVerified());
        updatingControls = false;
    }

    private void applyQuickCardControls() {
        if (updatingControls) {
            return;
        }
        SelectOption opt = (SelectOption) cardCombo.getSelectedItem();
        if (opt == null) {
            return;
        }
        VirtualCard card = cardProvider.findByCardId(opt.id).orElse(null);
        if (card == null) {
            return;
        }
        CardStatus targetStatus = (CardStatus) quickStatusCombo.getSelectedItem();
        card.setStatus(targetStatus);
        card.setPinVerified(quickPinBox.isSelected());
        card.setCryptoVerified(quickCryptoBox.isSelected());
        onCardStateChanged.run();

        if (simulatorGateway != null && simulatorGateway.isConnected()) {
            CardGateway.CardGatewayResult simRes = simulatorGateway.setCardStatus(targetStatus);
            onGatewayActivity.run();
            UiTheme.setBanner(decisionBanner, simRes.isSuccess(),
                    "Đã cập nhật " + card.getCardId() + " (" + targetStatus + ") | jcsl: "
                            + simRes.getMessage() + " (SW=" + String.format("0x%04X", simRes.getStatusWord()) + ")");
        } else {
            UiTheme.setNeutralBanner(decisionBanner,
                    "Đã cập nhật trạng thái mô phỏng cho " + card.getCardId() + ": " + targetStatus);
        }
    }

    private void runEvaluation() {
        SelectOption cardOpt = (SelectOption) cardCombo.getSelectedItem();
        SelectOption areaOpt = (SelectOption) areaCombo.getSelectedItem();
        if (cardOpt == null || areaOpt == null) {
            UiTheme.setBanner(decisionBanner, false, "Vui lòng chọn đầy đủ thẻ ảo và khu vực.");
            return;
        }
        LocalDateTime attemptTime;
        try {
            attemptTime = UiTheme.parseDateTime(attemptTimeField.getText());
        } catch (IllegalArgumentException ex) {
            UiTheme.setBanner(decisionBanner, false, ex.getMessage());
            return;
        }

        AccessAttemptLog log = accessControlService.evaluateAccess(
                cardOpt.id,
                areaOpt.id,
                attemptTime,
                securityCheckEnabled.getAsBoolean());

        boolean allowed = log.getDecision() == AccessDecision.ALLOW;
        String prefix = allowed ? "CHO PHÉP (ALLOW)" : "TỪ CHỐI (DENY)";
        UiTheme.setBanner(decisionBanner, allowed,
                prefix + " — [" + log.getReasonCode().name() + "] " + log.getReasonDetail());
        refreshLogs();
        onGatewayActivity.run();
    }

    public final void refreshData() {
        String prevCard = cardCombo.getSelectedItem() instanceof SelectOption
                ? ((SelectOption) cardCombo.getSelectedItem()).id
                : null;
        String prevArea = areaCombo.getSelectedItem() instanceof SelectOption
                ? ((SelectOption) areaCombo.getSelectedItem()).id
                : null;

        cardCombo.removeAllItems();
        for (VirtualCard card : cardProvider.findAll()) {
            String empInfo = employeeProvider.findById(card.getEmployeeId())
                    .map(Employee::getFullName)
                    .orElse(card.getEmployeeId());
            cardCombo.addItem(new SelectOption(
                    card.getCardId(),
                    card.getCardId() + " — " + empInfo + " [" + card.getStatus() + "]"));
        }

        areaCombo.removeAllItems();
        for (Area area : policyProvider.findAllAreas()) {
            areaCombo.addItem(new SelectOption(
                    area.getAreaId(),
                    area.getAreaId() + " — " + area.getAreaName()));
        }

        restoreSelection(cardCombo, prevCard);
        restoreSelection(areaCombo, prevArea);
        syncQuickCardControls();
        refreshLogs();
    }

    private void refreshLogs() {
        tableModel.setRowCount(0);
        List<AccessAttemptLog> logs = new ArrayList<>(accessControlService.listAllLogs());
        Collections.reverse(logs);
        for (AccessAttemptLog log : logs) {
            tableModel.addRow(new Object[] {
                    log.getLogId(),
                    log.getCardId(),
                    log.getEmployeeId() == null ? "-" : log.getEmployeeId(),
                    log.getAreaId(),
                    log.getAttemptTime().format(UiTheme.DATE_TIME_FMT),
                    log.getDecision().name(),
                    log.getReasonCode().name(),
                    log.getMatchedRuleId() == null ? "-" : log.getMatchedRuleId(),
                    log.getReasonDetail()
            });
        }
    }

    private static void restoreSelection(JComboBox<SelectOption> combo, String id) {
        if (id == null) {
            return;
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (id.equals(combo.getItemAt(i).id)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private static final class SelectOption {
        final String id;
        final String label;

        SelectOption(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
