package com.nhom11.smartcard.host.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Shared formatting and visual helpers across all Swing panels.
 */
final class UiTheme {

    static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static final Color COLOR_ALLOW_BG = new Color(220, 245, 228);
    static final Color COLOR_ALLOW_FG = new Color(20, 110, 45);
    static final Color COLOR_DENY_BG = new Color(252, 225, 225);
    static final Color COLOR_DENY_FG = new Color(165, 28, 28);
    static final Color COLOR_NEUTRAL_BG = new Color(240, 244, 248);
    static final Color COLOR_NEUTRAL_FG = new Color(45, 55, 72);

    private UiTheme() {
    }

    static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim(), DATE_FMT);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Định dạng ngày phải là yyyy-MM-dd (ví dụ 2026-11-22).");
        }
    }

    static LocalDateTime parseDateTime(String text) {
        String trimmed = text == null ? "" : text.trim();
        try {
            return LocalDateTime.parse(trimmed, DATE_TIME_FMT);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(trimmed);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException(
                        "Định dạng ngày giờ phải là yyyy-MM-dd HH:mm:ss (ví dụ 2026-11-22 08:30:00).");
            }
        }
    }

    static DefaultTableModel readOnlyTableModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    static void styleTable(JTable table) {
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));
        DefaultTableCellRenderer paddingRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl,
                    Object value,
                    boolean isSelected,
                    boolean hasFocus,
                    int row,
                    int column) {
                Component comp = super.getTableCellRendererComponent(
                        tbl, value, isSelected, hasFocus, row, column);
                if (comp instanceof JLabel) {
                    ((JLabel) comp).setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
                }
                return comp;
            }
        };
        table.setDefaultRenderer(Object.class, paddingRenderer);
    }

    static void setBanner(JLabel banner, boolean ok, String text) {
        banner.setOpaque(true);
        banner.setText(text);
        if (ok) {
            banner.setBackground(COLOR_ALLOW_BG);
            banner.setForeground(COLOR_ALLOW_FG);
            banner.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(COLOR_ALLOW_FG),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        } else {
            banner.setBackground(COLOR_DENY_BG);
            banner.setForeground(COLOR_DENY_FG);
            banner.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(COLOR_DENY_FG),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        }
    }

    static void setNeutralBanner(JLabel banner, String text) {
        banner.setOpaque(true);
        banner.setText(text);
        banner.setBackground(COLOR_NEUTRAL_BG);
        banner.setForeground(COLOR_NEUTRAL_FG);
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
    }
}
