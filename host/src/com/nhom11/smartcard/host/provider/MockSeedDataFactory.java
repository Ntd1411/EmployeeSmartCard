package com.nhom11.smartcard.host.provider;

import com.nhom11.smartcard.host.model.AccessRule;
import com.nhom11.smartcard.host.model.Area;
import com.nhom11.smartcard.host.model.AttendanceEventType;
import com.nhom11.smartcard.host.model.AttendanceRecord;
import com.nhom11.smartcard.host.model.CardStatus;
import com.nhom11.smartcard.host.model.Employee;
import com.nhom11.smartcard.host.model.TemporaryGrant;
import com.nhom11.smartcard.host.model.VirtualCard;
import com.nhom11.smartcard.host.repository.AttendanceRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Deterministic mock seed generator for standalone demos across US10-US13.
 */
public final class MockSeedDataFactory {

    private MockSeedDataFactory() {
    }

    /**
     * Populates providers, card gateway shift state, and sample attendance records.
     *
     * @param employeeProvider employee store to populate
     * @param cardProvider virtual card store to populate
     * @param policyProvider area/rule/grant store to populate
     * @param attendanceRepository attendance store to populate
     * @param cardGateway optional gateway whose open shift state is synchronized
     * @param referenceDate base date for sample timestamps
     */
    public static void populateDemoData(
            InMemoryEmployeeProvider employeeProvider,
            InMemoryVirtualCardProvider cardProvider,
            InMemoryAccessPolicyProvider policyProvider,
            AttendanceRepository attendanceRepository,
            VirtualStoreCardGateway cardGateway,
            LocalDate referenceDate) {
        employeeProvider.clear();
        cardProvider.clear();
        policyProvider.clear();
        attendanceRepository.clear();
        if (cardGateway != null) {
            cardGateway.resetAllShiftStates();
        }

        // Fictional demo employees for standalone verification.
        employeeProvider.put(new Employee("EMP001", "Tran Minh Khoa", "Ky thuat", "Lap trinh vien", true));
        employeeProvider.put(new Employee("EMP002", "Le Thu Trang", "Ke toan", "Ke toan vien", true));
        employeeProvider.put(new Employee("EMP003", "Pham Gia Huy", "Nhan su", "Chuyen vien", true));
        employeeProvider.put(new Employee("EMP004", "Hoang Anh Thu", "Bao ve", "Truong ca", true));

        LocalDateTime issued = referenceDate.minusDays(30).atTime(9, 0);
        cardProvider.put(new VirtualCard("CARD-001", "EMP001", CardStatus.ACTIVE, true, true, issued));
        cardProvider.put(new VirtualCard("CARD-002", "EMP002", CardStatus.ACTIVE, true, true, issued));
        cardProvider.put(new VirtualCard("CARD-003", "EMP003", CardStatus.LOCKED, true, true, issued));
        cardProvider.put(new VirtualCard("CARD-004", "EMP004", CardStatus.ACTIVE, false, true, issued));
        cardProvider.put(new VirtualCard("CARD-005", "EMP002", CardStatus.REVOKED, true, true, issued.minusDays(10)));

        policyProvider.putArea(new Area("AREA-LOBBY", 1, "Sanh chinh", 1));
        policyProvider.putArea(new Area("AREA-ACCT", 2, "Phong Ke toan", 2));
        policyProvider.putArea(new Area("AREA-SERVER", 3, "Phong May chu", 3));
        policyProvider.putArea(new Area("AREA-ARCHIVE", 4, "Kho luu tru", 3));

        policyProvider.addRule(new AccessRule(
                "RULE-01", "AREA-LOBBY", "Ky thuat", null, AccessRule.MatchMode.DEPARTMENT_OR_POSITION, true));
        policyProvider.addRule(new AccessRule(
                "RULE-02", "AREA-LOBBY", "Ke toan", null, AccessRule.MatchMode.DEPARTMENT_OR_POSITION, true));
        policyProvider.addRule(new AccessRule(
                "RULE-03", "AREA-SERVER", "Ky thuat", "Lap trinh vien", AccessRule.MatchMode.DEPARTMENT_AND_POSITION, true));
        policyProvider.addRule(new AccessRule(
                "RULE-04", "AREA-ACCT", "Ke toan", null, AccessRule.MatchMode.DEPARTMENT_OR_POSITION, true));

        LocalDateTime now = referenceDate.atTime(10, 0);
        policyProvider.addGrant(new TemporaryGrant(
                "GRANT-ACTIVE",
                "EMP002",
                "AREA-ARCHIVE",
                now.minusHours(4),
                now.plusDays(2),
                false,
                "Kiem ke tai lieu dinh ky"));
        policyProvider.addGrant(new TemporaryGrant(
                "GRANT-EXPIRED",
                "EMP001",
                "AREA-ARCHIVE",
                now.minusDays(5),
                now.minusDays(2),
                false,
                "Ho tro sao luu du lieu"));
        policyProvider.addGrant(new TemporaryGrant(
                "GRANT-FUTURE",
                "EMP002",
                "AREA-SERVER",
                now.plusDays(2),
                now.plusDays(4),
                false,
                "Kiem tra he thong dinh ky"));

        LocalDate dMinus2 = referenceDate.minusDays(2);
        LocalDate dMinus1 = referenceDate.minusDays(1);

        // EMP001: normal shift on D-2 (8.5h) + missing checkout on D-1 (tests incomplete shift & rollover)
        attendanceRepository.save(new AttendanceRecord(
                "ATT-SEED-001", "EMP001", "CARD-001", AttendanceEventType.CHECK_IN, dMinus2.atTime(8, 0), "SEED", ""));
        attendanceRepository.save(new AttendanceRecord(
                "ATT-SEED-002", "EMP001", "CARD-001", AttendanceEventType.CHECK_OUT, dMinus2.atTime(16, 30), "SEED", ""));
        attendanceRepository.save(new AttendanceRecord(
                "ATT-SEED-003", "EMP001", "CARD-001", AttendanceEventType.CHECK_IN, dMinus1.atTime(8, 15), "SEED", "Thieu luot ra"));

        // EMP002: two complete shifts (8.0h and 7.5h)
        attendanceRepository.save(new AttendanceRecord(
                "ATT-SEED-004", "EMP002", "CARD-002", AttendanceEventType.CHECK_IN, dMinus2.atTime(8, 30), "SEED", ""));
        attendanceRepository.save(new AttendanceRecord(
                "ATT-SEED-005", "EMP002", "CARD-002", AttendanceEventType.CHECK_OUT, dMinus2.atTime(16, 30), "SEED", ""));
        attendanceRepository.save(new AttendanceRecord(
                "ATT-SEED-006", "EMP002", "CARD-002", AttendanceEventType.CHECK_IN, dMinus1.atTime(9, 0), "SEED", ""));
        attendanceRepository.save(new AttendanceRecord(
                "ATT-SEED-007", "EMP002", "CARD-002", AttendanceEventType.CHECK_OUT, dMinus1.atTime(16, 30), "SEED", ""));

        // EMP004: overnight shift (22:00 D-2 to 06:00 D-1 = 8.0h) + orphan check-out on D-1
        attendanceRepository.save(new AttendanceRecord(
                "ATT-SEED-008", "EMP004", "CARD-004", AttendanceEventType.CHECK_IN, dMinus2.atTime(22, 0), "SEED", "Ca dem"));
        attendanceRepository.save(new AttendanceRecord(
                "ATT-SEED-009", "EMP004", "CARD-004", AttendanceEventType.CHECK_OUT, dMinus1.atTime(6, 0), "SEED", "Ca dem"));
        attendanceRepository.save(new AttendanceRecord(
                "ATT-SEED-010", "EMP004", "CARD-004", AttendanceEventType.CHECK_OUT, dMinus1.atTime(18, 0), "SEED", "Thieu luot vao"));

        if (cardGateway != null) {
            cardGateway.syncShiftState("CARD-001", true);
            cardGateway.syncShiftState("CARD-002", false);
            cardGateway.syncShiftState("CARD-004", false);
        }
    }
}
