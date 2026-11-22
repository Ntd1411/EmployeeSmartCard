package com.nhom11.smartcard.host;

import com.nhom11.smartcard.host.model.AccessAttemptLog;
import com.nhom11.smartcard.host.model.AccessDecision;
import com.nhom11.smartcard.host.model.AccessReasonCode;
import com.nhom11.smartcard.host.model.AccessRule;
import com.nhom11.smartcard.host.model.Area;
import com.nhom11.smartcard.host.model.AttendanceEventType;
import com.nhom11.smartcard.host.model.AttendanceRecord;
import com.nhom11.smartcard.host.model.AttendanceStatistics;
import com.nhom11.smartcard.host.model.CardStatus;
import com.nhom11.smartcard.host.model.Employee;
import com.nhom11.smartcard.host.model.ShiftStatus;
import com.nhom11.smartcard.host.model.TemporaryGrant;
import com.nhom11.smartcard.host.model.VirtualCard;
import com.nhom11.smartcard.host.model.WorkShift;
import com.nhom11.smartcard.host.provider.ApduSimulatorCardGateway;
import com.nhom11.smartcard.host.provider.CardGateway;
import com.nhom11.smartcard.host.provider.InMemoryAccessPolicyProvider;
import com.nhom11.smartcard.host.provider.InMemoryEmployeeProvider;
import com.nhom11.smartcard.host.provider.InMemoryVirtualCardProvider;
import com.nhom11.smartcard.host.provider.SwitchableCardGateway;
import com.nhom11.smartcard.host.provider.VirtualStoreCardGateway;
import com.nhom11.smartcard.host.repository.FileAccessLogRepository;
import com.nhom11.smartcard.host.repository.FileAttendanceRepository;
import com.nhom11.smartcard.host.service.AccessControlService;
import com.nhom11.smartcard.host.service.AttendanceService;
import com.nhom11.smartcard.host.service.AttendanceStatisticsService;
import com.nhom11.smartcard.host.service.PunchResult;
import com.nhom11.smartcard.host.service.ShiftPairingService;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.smartcardio.TerminalFactory;

/**
 * Self-contained automated test runner verifying US10-US13 and repository/gateway contracts.
 */
public class AttendanceAndAccessTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        runTest("TC-10-01", AttendanceAndAccessTest::tc1001ValidCheckInAndCheckOut);
        runTest("TC-10-02", AttendanceAndAccessTest::tc1002LockedOrRevokedCardRejected);
        runTest("TC-10-03", AttendanceAndAccessTest::tc1003DoubleTapDebounced);
        runTest("TC-10-04", AttendanceAndAccessTest::tc1004UnknownCardRejected);

        runTest("TC-11-01", AttendanceAndAccessTest::tc1101StandardDayShift);
        runTest("TC-11-02", AttendanceAndAccessTest::tc1102OvernightShift);
        runTest("TC-11-03", AttendanceAndAccessTest::tc1103MissingCheckOut);
        runTest("TC-11-04", AttendanceAndAccessTest::tc1104MissingCheckIn);
        runTest("TC-11-05", AttendanceAndAccessTest::tc1105InvalidOrderAndExceed24Hours);

        runTest("TC-13-01", AttendanceAndAccessTest::tc1301EmployeeAndRangeAggregation);
        runTest("TC-13-02", AttendanceAndAccessTest::tc1302EmptyDateRange);
        runTest("TC-13-03", AttendanceAndAccessTest::tc1303InvalidDateRangeThrows);
        runTest("TC-13-04", AttendanceAndAccessTest::tc1304CrossCheckWithRawRecords);

        runTest("TC-12-01", AttendanceAndAccessTest::tc1201PermanentRoleAllow);
        runTest("TC-12-02", AttendanceAndAccessTest::tc1202TemporaryGrantValidAllow);
        runTest("TC-12-03", AttendanceAndAccessTest::tc1203TemporaryGrantExpiredDeny);
        runTest("TC-12-04", AttendanceAndAccessTest::tc1204LockedOrRevokedOverridesValidGrant);
        runTest("TC-12-05", AttendanceAndAccessTest::tc1205NoPermissionAndSecurityDenies);
        runTest("TC-12-06", AttendanceAndAccessTest::tc1206ExactTemporaryGrantBoundaries);

        runTest("TC-REPO-01", AttendanceAndAccessTest::tcRepo01DiskRoundtrip);
        runTest("TC-GW-01", AttendanceAndAccessTest::tcGateway01StatusWords);
        runTest("TC-GW-02", AttendanceAndAccessTest::tcGateway02SwitchableDelegate);
        runTest("TC-GW-03", AttendanceAndAccessTest::tcGateway03ApduCodecAndRedaction);
        runTest("TC-GW-04", AttendanceAndAccessTest::tcGateway04SocketProviderLoading);
        runTest("TC-GW-05", AttendanceAndAccessTest::tcGateway05ConnectTimeoutOnBusySimulator);
        runTest("TC-12-07", AttendanceAndAccessTest::tc1207GatewayStatusWordMapping);

        System.out.println("Summary: " + passed + " passed, " + failed + " failed.");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static TestFixture createFixture() {
        InMemoryEmployeeProvider employees = new InMemoryEmployeeProvider();
        InMemoryVirtualCardProvider cards = new InMemoryVirtualCardProvider();
        InMemoryAccessPolicyProvider policies = new InMemoryAccessPolicyProvider();
        FileAttendanceRepository attRepo = new FileAttendanceRepository();
        FileAccessLogRepository logRepo = new FileAccessLogRepository();
        VirtualStoreCardGateway gateway = new VirtualStoreCardGateway(cards);

        employees.put(new Employee("E1", "Employee One", "Engineering", "Developer", true));
        employees.put(new Employee("E2", "Employee Two", "Finance", "Accountant", true));

        LocalDateTime issued = LocalDateTime.of(2026, 11, 1, 9, 0);
        cards.put(new VirtualCard("C-ACT", "E1", CardStatus.ACTIVE, true, true, issued));
        cards.put(new VirtualCard("C-LOCK", "E1", CardStatus.LOCKED, true, true, issued));
        cards.put(new VirtualCard("C-REV", "E1", CardStatus.REVOKED, true, true, issued));
        cards.put(new VirtualCard("C-NOPIN", "E2", CardStatus.ACTIVE, false, true, issued));
        cards.put(new VirtualCard("C-BADCRYPTO", "E2", CardStatus.ACTIVE, true, false, issued));
        cards.put(new VirtualCard("C-E2", "E2", CardStatus.ACTIVE, true, true, issued));

        policies.putArea(new Area("AREA-DEV", 1, "Dev Lab", 2));
        policies.putArea(new Area("AREA-VAULT", 2, "Vault", 3));
        policies.addRule(new AccessRule(
                "RULE-ENG", "AREA-DEV", "Engineering", "Developer", AccessRule.MatchMode.DEPARTMENT_AND_POSITION, true));

        LocalDateTime grantStart = LocalDateTime.of(2026, 11, 20, 8, 0, 0);
        LocalDateTime grantEnd = LocalDateTime.of(2026, 11, 22, 18, 0, 0);
        policies.addGrant(new TemporaryGrant(
                "GRANT-E1-VAULT", "E1", "AREA-VAULT", grantStart, grantEnd, false, "Audit"));

        AttendanceService attService = new AttendanceService(employees, cards, attRepo, gateway);
        ShiftPairingService shiftService = new ShiftPairingService(attRepo);
        AttendanceStatisticsService statsService = new AttendanceStatisticsService(employees, attRepo, shiftService);
        AccessControlService accessService = new AccessControlService(employees, cards, policies, logRepo, gateway);

        return new TestFixture(
                employees, cards, policies, attRepo, logRepo, gateway,
                attService, shiftService, statsService, accessService);
    }

    private static void tc1001ValidCheckInAndCheckOut() {
        TestFixture f = createFixture();
        PunchResult inRes = f.attService.punch(
                "C-ACT", AttendanceEventType.CHECK_IN, LocalDateTime.of(2026, 11, 20, 8, 0), true, false);
        PunchResult outRes = f.attService.punch(
                "C-ACT", AttendanceEventType.CHECK_OUT, LocalDateTime.of(2026, 11, 20, 17, 0), true, false);

        assertTrue(inRes.isAccepted(), "Check-in should succeed");
        assertTrue(outRes.isAccepted(), "Check-out should succeed");
        assertEquals(2, f.attRepo.findAll().size(), "Expected 2 saved attendance records");
    }

    private static void tc1002LockedOrRevokedCardRejected() {
        TestFixture f = createFixture();
        PunchResult locked = f.attService.punch(
                "C-LOCK", AttendanceEventType.CHECK_IN, LocalDateTime.of(2026, 11, 20, 8, 0), true, false);
        PunchResult revoked = f.attService.punch(
                "C-REV", AttendanceEventType.CHECK_IN, LocalDateTime.of(2026, 11, 20, 8, 0), true, false);

        assertTrue(!locked.isAccepted(), "LOCKED card must be rejected");
        assertEquals(CardGateway.SW_CARD_LOCKED_OR_REVOKED, locked.getCardStatusWord(), "Expected SW 0x6983");
        assertTrue(!revoked.isAccepted(), "REVOKED card must be rejected");
        assertEquals(0, f.attRepo.findAll().size(), "No records saved for blocked cards");
    }

    private static void tc1003DoubleTapDebounced() {
        TestFixture f = createFixture();
        PunchResult first = f.attService.punch(
                "C-ACT", AttendanceEventType.CHECK_IN, LocalDateTime.of(2026, 11, 20, 8, 0, 0), true, false);
        PunchResult second = f.attService.punch(
                "C-ACT", AttendanceEventType.CHECK_IN, LocalDateTime.of(2026, 11, 20, 8, 0, 10), true, false);

        assertTrue(first.isAccepted(), "First punch succeeds");
        assertTrue(!second.isAccepted(), "Double-tap within 10s must be blocked");
        assertEquals(1, f.attRepo.findAll().size(), "Only 1 punch recorded");
    }

    private static void tc1004UnknownCardRejected() {
        TestFixture f = createFixture();
        PunchResult res = f.attService.punch(
                "CARD-MISSING", AttendanceEventType.CHECK_IN, LocalDateTime.of(2026, 11, 20, 8, 0), true, false);
        assertTrue(!res.isAccepted(), "Unknown card must be rejected");
        assertEquals(0, f.attRepo.findAll().size(), "No record saved");
    }

    private static void tc1101StandardDayShift() {
        TestFixture f = createFixture();
        f.attService.punch("C-ACT", AttendanceEventType.CHECK_IN, LocalDateTime.of(2026, 11, 20, 8, 0), true, false);
        f.attService.punch("C-ACT", AttendanceEventType.CHECK_OUT, LocalDateTime.of(2026, 11, 20, 17, 30), true, false);

        List<WorkShift> shifts = f.shiftService.calculateShifts(
                "E1", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20));
        assertEquals(1, shifts.size(), "Expected 1 shift");
        WorkShift s = shifts.get(0);
        assertEquals(ShiftStatus.COMPLETE, s.getStatus(), "Expected COMPLETE");
        assertEquals(570L, s.getWorkedMinutes(), "Expected 570 minutes");
        assertEquals(new BigDecimal("9.50"), s.getWorkedHours(), "Expected 9.50 hours");
    }

    private static void tc1102OvernightShift() {
        TestFixture f = createFixture();
        f.attService.punch("C-ACT", AttendanceEventType.CHECK_IN, LocalDateTime.of(2026, 11, 10, 22, 0), true, false);
        f.attService.punch("C-ACT", AttendanceEventType.CHECK_OUT, LocalDateTime.of(2026, 11, 11, 6, 0), true, false);

        List<WorkShift> shifts = f.shiftService.calculateShifts(
                "E1", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10));
        assertEquals(1, shifts.size(), "Expected 1 overnight shift attributed to start workDate");
        WorkShift s = shifts.get(0);
        assertEquals(ShiftStatus.COMPLETE, s.getStatus(), "Expected COMPLETE");
        assertEquals(480L, s.getWorkedMinutes(), "Expected 480 minutes");
        assertEquals(new BigDecimal("8.00"), s.getWorkedHours(), "Expected 8.00 hours");
        assertEquals(LocalDate.of(2026, 11, 10), s.getWorkDate(), "Expected start date as workDate");
    }

    private static void tc1103MissingCheckOut() {
        TestFixture f = createFixture();
        f.attService.punch("C-ACT", AttendanceEventType.CHECK_IN, LocalDateTime.of(2026, 11, 20, 8, 0), true, false);
        f.attService.punch("C-ACT", AttendanceEventType.CHECK_IN, LocalDateTime.of(2026, 11, 21, 8, 0), true, false);

        List<WorkShift> shifts = f.shiftService.calculateShifts(
                "E1", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21));
        assertEquals(2, shifts.size(), "Expected 2 shifts");
        assertEquals(ShiftStatus.MISSING_CHECK_OUT, shifts.get(0).getStatus(), "Day 20 missing checkout");
        assertEquals(new BigDecimal("0.00"), shifts.get(0).getWorkedHours(), "Expected 0.00 hours");
        assertEquals(ShiftStatus.MISSING_CHECK_OUT, shifts.get(1).getStatus(), "Day 21 open checkout");
    }

    private static void tc1104MissingCheckIn() {
        TestFixture f = createFixture();
        f.attRepo.save(new AttendanceRecord(
                "R-ORPHAN-OUT", "E1", "C-ACT", AttendanceEventType.CHECK_OUT,
                LocalDateTime.of(2026, 11, 20, 17, 0), "TEST", ""));

        List<WorkShift> shifts = f.shiftService.calculateShifts(
                "E1", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20));
        assertEquals(1, shifts.size(), "Expected 1 shift");
        assertEquals(ShiftStatus.MISSING_CHECK_IN, shifts.get(0).getStatus(), "Expected MISSING_CHECK_IN");
        assertEquals(new BigDecimal("0.00"), shifts.get(0).getWorkedHours(), "Expected 0.00 hours");
    }

    private static void tc1105InvalidOrderAndExceed24Hours() {
        TestFixture f = createFixture();
        // Same timestamp check-in and check-out -> INVALID_ORDER
        f.attRepo.save(new AttendanceRecord(
                "R1", "E1", "C-ACT", AttendanceEventType.CHECK_IN,
                LocalDateTime.of(2026, 11, 15, 8, 0), "TEST", ""));
        f.attRepo.save(new AttendanceRecord(
                "R2", "E1", "C-ACT", AttendanceEventType.CHECK_OUT,
                LocalDateTime.of(2026, 11, 15, 8, 0), "TEST", ""));
        // Check-in and check-out separated by 30 hours -> split into two missing shifts
        f.attRepo.save(new AttendanceRecord(
                "R3", "E2", "C-E2", AttendanceEventType.CHECK_IN,
                LocalDateTime.of(2026, 11, 15, 8, 0), "TEST", ""));
        f.attRepo.save(new AttendanceRecord(
                "R4", "E2", "C-E2", AttendanceEventType.CHECK_OUT,
                LocalDateTime.of(2026, 11, 16, 14, 0), "TEST", ""));

        List<WorkShift> e1Shifts = f.shiftService.calculateShifts(
                "E1", LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 15));
        assertEquals(1, e1Shifts.size(), "Expected 1 invalid-order shift");
        assertEquals(ShiftStatus.INVALID_ORDER, e1Shifts.get(0).getStatus(), "Expected INVALID_ORDER");
        assertEquals(0L, e1Shifts.get(0).getWorkedMinutes(), "Expected 0 workedMinutes");

        List<WorkShift> e2Shifts = f.shiftService.calculateShifts(
                "E2", LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 16));
        assertEquals(2, e2Shifts.size(), "Shift >24h must split into MISSING_CHECK_OUT and MISSING_CHECK_IN");
        assertEquals(ShiftStatus.MISSING_CHECK_OUT, e2Shifts.get(0).getStatus(), "First part missing check-out");
        assertEquals(ShiftStatus.MISSING_CHECK_IN, e2Shifts.get(1).getStatus(), "Second part missing check-in");
    }

    private static void tc1301EmployeeAndRangeAggregation() {
        TestFixture f = createFixture();
        // Day 1: 8.00h
        f.attService.punch("C-ACT", AttendanceEventType.CHECK_IN, LocalDateTime.of(2026, 11, 18, 8, 0), true, false);
        f.attService.punch("C-ACT", AttendanceEventType.CHECK_OUT, LocalDateTime.of(2026, 11, 18, 16, 0), true, false);
        // Day 2: 8.50h
        f.attService.punch("C-ACT", AttendanceEventType.CHECK_IN, LocalDateTime.of(2026, 11, 19, 8, 0), true, false);
        f.attService.punch("C-ACT", AttendanceEventType.CHECK_OUT, LocalDateTime.of(2026, 11, 19, 16, 30), true, false);
        // Day 3: 7.50h
        f.attService.punch("C-ACT", AttendanceEventType.CHECK_IN, LocalDateTime.of(2026, 11, 20, 8, 30), true, false);
        f.attService.punch("C-ACT", AttendanceEventType.CHECK_OUT, LocalDateTime.of(2026, 11, 20, 16, 0), true, false);
        // Day 4: missing check-out
        f.attService.punch("C-ACT", AttendanceEventType.CHECK_IN, LocalDateTime.of(2026, 11, 21, 8, 0), true, false);

        List<AttendanceStatistics> stats = f.statsService.calculateStatistics(
                "E1", null, LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 21));
        assertEquals(1, stats.size(), "Expected 1 employee statistics row");
        AttendanceStatistics s = stats.get(0);
        assertEquals(7, s.getTotalPunches(), "totalPunches");
        assertEquals(4, s.getCheckInCount(), "checkInCount");
        assertEquals(3, s.getCheckOutCount(), "checkOutCount");
        assertEquals(3, s.getCompletedShifts(), "completedShifts");
        assertEquals(1, s.getIncompleteShifts(), "incompleteShifts");
        assertEquals(3, s.getWorkDays(), "workDays");
        assertEquals(new BigDecimal("24.00"), s.getTotalHours(), "totalHours");
        assertEquals(new BigDecimal("8.00"), s.getAverageHoursPerWorkDay(), "averageHoursPerWorkDay");
    }

    private static void tc1302EmptyDateRange() {
        TestFixture f = createFixture();
        List<AttendanceStatistics> stats = f.statsService.calculateStatistics(
                "E1", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7));
        assertEquals(1, stats.size(), "Row returned with zero counts");
        assertEquals(0, stats.get(0).getTotalPunches(), "totalPunches=0");
        assertEquals(new BigDecimal("0.00"), stats.get(0).getTotalHours(), "totalHours=0.00");
    }

    private static void tc1303InvalidDateRangeThrows() {
        TestFixture f = createFixture();
        boolean threw = false;
        try {
            f.statsService.calculateStatistics(null, null, LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 20));
        } catch (IllegalArgumentException ex) {
            threw = true;
        }
        assertTrue(threw, "fromDate > toDate must throw IllegalArgumentException");
    }

    private static void tc1304CrossCheckWithRawRecords() {
        TestFixture f = createFixture();
        f.attService.punch("C-ACT", AttendanceEventType.CHECK_IN, LocalDateTime.of(2026, 11, 20, 8, 0), true, false);
        f.attService.punch("C-ACT", AttendanceEventType.CHECK_OUT, LocalDateTime.of(2026, 11, 20, 12, 15), true, false);
        f.attService.punch("C-E2", AttendanceEventType.CHECK_IN, LocalDateTime.of(2026, 11, 20, 9, 0), true, false);
        f.attService.punch("C-E2", AttendanceEventType.CHECK_OUT, LocalDateTime.of(2026, 11, 20, 17, 30), true, false);

        LocalDate d = LocalDate.of(2026, 11, 20);
        List<AttendanceStatistics> stats = f.statsService.calculateStatistics(null, null, d, d);
        int rawPunches = f.attRepo.findByDateRange(d, d).size();
        int statPunches = stats.stream().mapToInt(AttendanceStatistics::getTotalPunches).sum();
        BigDecimal statHours = stats.stream()
                .map(AttendanceStatistics::getTotalHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long shiftMinutes = f.shiftService.calculateShifts(null, d, d).stream()
                .mapToLong(WorkShift::getWorkedMinutes)
                .sum();

        assertEquals(rawPunches, statPunches, "Punch counts must match raw records");
        assertEquals(ShiftPairingService.minutesToHours(shiftMinutes), statHours, "Total hours must match shift sums");
    }

    private static void tc1201PermanentRoleAllow() {
        TestFixture f = createFixture();
        AccessAttemptLog log = f.accessService.evaluateAccess(
                "C-ACT", "AREA-DEV", LocalDateTime.of(2026, 11, 20, 9, 0), true);
        assertEquals(AccessDecision.ALLOW, log.getDecision(), "Expected ALLOW");
        assertEquals(AccessReasonCode.PERMANENT_ROLE_MATCH, log.getReasonCode(), "Expected PERMANENT_ROLE_MATCH");
        assertEquals("RULE-ENG", log.getMatchedRuleId(), "Expected RULE-ENG");
    }

    private static void tc1202TemporaryGrantValidAllow() {
        TestFixture f = createFixture();
        AccessAttemptLog log = f.accessService.evaluateAccess(
                "C-ACT", "AREA-VAULT", LocalDateTime.of(2026, 11, 21, 12, 0), true);
        assertEquals(AccessDecision.ALLOW, log.getDecision(), "Expected ALLOW via temp grant");
        assertEquals(AccessReasonCode.TEMPORARY_GRANT_VALID, log.getReasonCode(), "Expected TEMPORARY_GRANT_VALID");
        assertEquals("GRANT-E1-VAULT", log.getMatchedRuleId(), "Expected GRANT-E1-VAULT");
    }

    private static void tc1203TemporaryGrantExpiredDeny() {
        TestFixture f = createFixture();
        AccessAttemptLog expired = f.accessService.evaluateAccess(
                "C-ACT", "AREA-VAULT", LocalDateTime.of(2026, 11, 22, 18, 0, 1), true);
        assertEquals(AccessDecision.DENY, expired.getDecision(), "Expected DENY after grant end");
        assertEquals(AccessReasonCode.TEMP_ACCESS_EXPIRED, expired.getReasonCode(), "Expected TEMP_ACCESS_EXPIRED");
    }

    private static void tc1204LockedOrRevokedOverridesValidGrant() {
        TestFixture f = createFixture();
        // E1 has both permanent access to AREA-DEV and active grant to AREA-VAULT; LOCKED/REVOKED must still DENY
        AccessAttemptLog lockedLog = f.accessService.evaluateAccess(
                "C-LOCK", "AREA-DEV", LocalDateTime.of(2026, 11, 21, 10, 0), true);
        AccessAttemptLog revokedLog = f.accessService.evaluateAccess(
                "C-REV", "AREA-VAULT", LocalDateTime.of(2026, 11, 21, 10, 0), true);

        assertEquals(AccessDecision.DENY, lockedLog.getDecision(), "LOCKED must override permanent rule");
        assertEquals(AccessReasonCode.CARD_LOCKED, lockedLog.getReasonCode(), "Expected CARD_LOCKED");
        assertEquals(AccessDecision.DENY, revokedLog.getDecision(), "REVOKED must override temp grant");
        assertEquals(AccessReasonCode.CARD_REVOKED, revokedLog.getReasonCode(), "Expected CARD_REVOKED");
    }

    private static void tc1205NoPermissionAndSecurityDenies() {
        TestFixture f = createFixture();
        AccessAttemptLog noPerm = f.accessService.evaluateAccess(
                "C-E2", "AREA-DEV", LocalDateTime.of(2026, 11, 21, 10, 0), true);
        AccessAttemptLog noPin = f.accessService.evaluateAccess(
                "C-NOPIN", "AREA-DEV", LocalDateTime.of(2026, 11, 21, 10, 0), true);
        AccessAttemptLog badCrypto = f.accessService.evaluateAccess(
                "C-BADCRYPTO", "AREA-DEV", LocalDateTime.of(2026, 11, 21, 10, 0), true);

        assertEquals(AccessDecision.DENY, noPerm.getDecision(), "Expected DENY");
        assertEquals(AccessReasonCode.NO_PERMISSION, noPerm.getReasonCode(), "Expected NO_PERMISSION");
        assertEquals(AccessReasonCode.PIN_NOT_VERIFIED, noPin.getReasonCode(), "Expected PIN_NOT_VERIFIED");
        assertEquals(AccessReasonCode.CRYPTO_INVALID, badCrypto.getReasonCode(), "Expected CRYPTO_INVALID");
    }

    private static void tc1206ExactTemporaryGrantBoundaries() {
        TestFixture f = createFixture();
        AccessAttemptLog before = f.accessService.evaluateAccess(
                "C-ACT", "AREA-VAULT", LocalDateTime.of(2026, 11, 20, 7, 59, 59), true);
        AccessAttemptLog atStart = f.accessService.evaluateAccess(
                "C-ACT", "AREA-VAULT", LocalDateTime.of(2026, 11, 20, 8, 0, 0), true);
        AccessAttemptLog atEnd = f.accessService.evaluateAccess(
                "C-ACT", "AREA-VAULT", LocalDateTime.of(2026, 11, 22, 18, 0, 0), true);

        assertEquals(AccessDecision.DENY, before.getDecision(), "1s before startTime denied");
        assertEquals(AccessReasonCode.TEMP_ACCESS_NOT_YET_ACTIVE, before.getReasonCode(), "Expected not yet active");
        assertEquals(AccessDecision.ALLOW, atStart.getDecision(), "Inclusive startTime allowed");
        assertEquals(AccessDecision.ALLOW, atEnd.getDecision(), "Inclusive endTime allowed");
    }

    private static void tcRepo01DiskRoundtrip() throws Exception {
        Path tempDir = Files.createTempDirectory("emp-card-test-");
        try {
            Path attFile = tempDir.resolve("att.tsv");
            Path logFile = tempDir.resolve("log.tsv");

            FileAttendanceRepository att1 = new FileAttendanceRepository(attFile);
            att1.save(new AttendanceRecord(
                    "ATT-1", "E1", "C-ACT", AttendanceEventType.CHECK_IN,
                    LocalDateTime.of(2026, 11, 22, 8, 0, 0), "HOST_UI", "Roundtrip\tnote"));

            FileAttendanceRepository att2 = new FileAttendanceRepository(attFile);
            assertEquals(1, att2.findAll().size(), "Attendance reload count");
            assertEquals("Roundtrip\tnote", att2.findAll().get(0).getNote(), "Escaped note preserved");

            FileAccessLogRepository log1 = new FileAccessLogRepository(logFile);
            log1.save(new AccessAttemptLog(
                    "LOG-1", "C-ACT", "E1", "AREA-DEV",
                    LocalDateTime.of(2026, 11, 22, 9, 0, 0),
                    AccessDecision.ALLOW, AccessReasonCode.PERMANENT_ROLE_MATCH, "OK", "RULE-ENG"));

            FileAccessLogRepository log2 = new FileAccessLogRepository(logFile);
            assertEquals(1, log2.findAll().size(), "Access log reload count");
            assertEquals(AccessReasonCode.PERMANENT_ROLE_MATCH, log2.findAll().get(0).getReasonCode(), "Reason preserved");
        } finally {
            Files.deleteIfExists(tempDir.resolve("att.tsv"));
            Files.deleteIfExists(tempDir.resolve("log.tsv"));
            Files.deleteIfExists(tempDir);
        }
    }

    private static void tcGateway01StatusWords() {
        TestFixture f = createFixture();
        LocalDateTime t1 = LocalDateTime.of(2026, 11, 22, 8, 0);

        CardGateway.CardGatewayResult noOpenOut = f.gateway.recordAttendancePunch(
                "C-ACT", AttendanceEventType.CHECK_OUT, t1, 1, false, true);
        assertEquals(CardGateway.SW_CONDITIONS_NOT_SATISFIED, noOpenOut.getStatusWord(), "SW 0x6985 without check-in");

        CardGateway.CardGatewayResult noPin = f.gateway.recordAttendancePunch(
                "C-NOPIN", AttendanceEventType.CHECK_IN, t1, 1, false, true);
        assertEquals(CardGateway.SW_SECURITY_STATUS_NOT_SATISFIED, noPin.getStatusWord(), "SW 0x6982 unverified PIN");

        CardGateway.CardGatewayResult locked = f.gateway.checkAreaAccess("C-LOCK", 1, t1, true);
        assertEquals(CardGateway.SW_CARD_LOCKED_OR_REVOKED, locked.getStatusWord(), "SW 0x6983 locked card");

        CardGateway.CardGatewayResult okIn = f.gateway.recordAttendancePunch(
                "C-ACT", AttendanceEventType.CHECK_IN, t1, 1, false, true);
        assertEquals(CardGateway.SW_NO_ERROR, okIn.getStatusWord(), "SW 0x9000 valid check-in");
    }

    private static void tcGateway02SwitchableDelegate() {
        TestFixture f = createFixture();
        SwitchableCardGateway swGateway = new SwitchableCardGateway(f.gateway);
        LocalDateTime now = LocalDateTime.of(2026, 11, 22, 9, 0);

        assertEquals(CardGateway.SW_NO_ERROR,
                swGateway.checkAreaAccess("C-ACT", 1, now, true).getStatusWord(),
                "Initial delegate returns 0x9000");

        swGateway.useDelegate(new CardGateway() {
            @Override
            public CardGatewayResult recordAttendancePunch(
                    String cardId, AttendanceEventType eventType, LocalDateTime timestamp,
                    int areaCode, boolean allowRollover, boolean requireSecurityCheck) {
                return CardGatewayResult.failure(CardGateway.SW_CONDITIONS_NOT_SATISFIED, "Stub denied");
            }

            @Override
            public CardGatewayResult checkAreaAccess(
                    String cardId, int areaCode, LocalDateTime attemptTime, boolean requireSecurityCheck) {
                return CardGatewayResult.failure(CardGateway.SW_SECURITY_STATUS_NOT_SATISFIED, "Stub PIN denied");
            }
        });

        assertEquals(CardGateway.SW_SECURITY_STATUS_NOT_SATISFIED,
                swGateway.checkAreaAccess("C-ACT", 1, now, true).getStatusWord(),
                "Switched delegate returns 0x6982");
    }

    private static void tcGateway03ApduCodecAndRedaction() {
        LocalDateTime ts = LocalDateTime.of(2026, 11, 22, 8, 30, 0);
        long expectedEpoch = ts.atZone(ZoneId.systemDefault()).toEpochSecond();

        byte[] punchPayload = ApduSimulatorCardGateway.buildPunchPayload(ts, 3);
        assertEquals(5, punchPayload.length, "Punch payload must be 5 bytes");
        long decodedEpoch = ((punchPayload[0] & 0xFFL) << 24)
                | ((punchPayload[1] & 0xFFL) << 16)
                | ((punchPayload[2] & 0xFFL) << 8)
                | (punchPayload[3] & 0xFFL);
        assertEquals(expectedEpoch, decodedEpoch, "Big-endian epoch must roundtrip");
        assertEquals(3, punchPayload[4] & 0xFF, "Area code byte");

        ApduSimulatorCardGateway.CardChipStatus chipStatus = ApduSimulatorCardGateway.decodeCardStatus(
                new byte[] { 0x01, 0x01, 0x05, 0x01 });
        assertEquals(CardStatus.LOCKED, chipStatus.getStatus(), "Decoded card status");
        assertTrue(chipStatus.isPinValidated(), "Decoded PIN flag");
        assertEquals(5, chipStatus.getPunchCounter(), "Decoded punch counter");
        assertTrue(chipStatus.isShiftOpen(), "Decoded shift state");

        byte[] lastBytes = new byte[] {
            0x02, punchPayload[0], punchPayload[1], punchPayload[2], punchPayload[3], 0x03, 0x00
        };
        ApduSimulatorCardGateway.LastCardAttendance last = ApduSimulatorCardGateway.decodeLastAttendance(lastBytes);
        assertEquals(AttendanceEventType.CHECK_OUT, last.getEventType().orElse(null), "Decoded event type");
        assertEquals(ts, last.getTimestamp().orElse(null), "Decoded timestamp");
        assertEquals(3, last.getAreaCode(), "Decoded area code");
        assertTrue(!last.isShiftOpen(), "Decoded closed shift");

        String redactedSummary = ApduSimulatorCardGateway.formatApduSummary(
                0x80, 0x20, 0x00, 0x00, new byte[] { 0x31, 0x32, 0x33, 0x34 }, 0x9000, new byte[0]);
        assertTrue(redactedSummary.contains("[REDACTED]"), "PIN summary must redact payload");
        assertTrue(!redactedSummary.contains("31323334"), "PIN hex bytes must never appear in trace");
    }

    private static void tcGateway04SocketProviderLoading() {
        Optional<Path> jarOpt = ApduSimulatorCardGateway.detectSocketProviderJar();
        assertTrue(jarOpt.isPresent(), "socketprovider.jar should be detected on workstation");
        TerminalFactory factory = ApduSimulatorCardGateway.createSocketTerminalFactory(
                "127.0.0.1", 9025, jarOpt.get());
        assertTrue(factory != null, "Socket TerminalFactory should initialize on Eclipse JRE");
    }

    private static void tcGateway05ConnectTimeoutOnBusySimulator() throws Exception {
        try (ServerSocket silentServer = new ServerSocket(0)) {
            int port = silentServer.getLocalPort();
            Thread acceptThread = new Thread(() -> {
                try (Socket client = silentServer.accept()) {
                    Thread.sleep(5000L);
                } catch (Exception ignored) {
                    // Server socket closes after timeout check.
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();

            ApduSimulatorCardGateway gateway = new ApduSimulatorCardGateway();
            long startMs = System.currentTimeMillis();
            IllegalStateException timeoutEx = null;
            try {
                gateway.connect("127.0.0.1", port, 600L);
            } catch (IllegalStateException ex) {
                timeoutEx = ex;
            } finally {
                gateway.disconnect();
            }
            long elapsedMs = System.currentTimeMillis() - startMs;

            assertTrue(timeoutEx != null, "Silent busy simulator must fail with timeout IllegalStateException");
            assertTrue(elapsedMs < 3500L, "Connect timeout must return promptly instead of hanging; elapsed=" + elapsedMs);
            assertTrue(timeoutEx.getMessage().contains("disconnect;"),
                    "Timeout message should instruct running disconnect; in APDU Console");
            assertTrue(!gateway.isConnected(), "Gateway must stay disconnected after timeout");
        }
    }

    private static void tc1207GatewayStatusWordMapping() {
        TestFixture f = createFixture();
        SwitchableCardGateway swGateway = new SwitchableCardGateway(new CardGateway() {
            @Override
            public CardGatewayResult recordAttendancePunch(
                    String cardId, AttendanceEventType eventType, LocalDateTime timestamp,
                    int areaCode, boolean allowRollover, boolean requireSecurityCheck) {
                return CardGatewayResult.ok("OK");
            }

            @Override
            public CardGatewayResult checkAreaAccess(
                    String cardId, int areaCode, LocalDateTime attemptTime, boolean requireSecurityCheck) {
                return CardGatewayResult.failure(CardGateway.SW_SECURITY_STATUS_NOT_SATISFIED, "Chip PIN missing");
            }
        });
        AccessControlService svc = new AccessControlService(
                f.employees, f.cards, f.policies, f.logRepo, swGateway);

        AccessAttemptLog pinDenied = svc.evaluateAccess(
                "C-ACT", "AREA-DEV", LocalDateTime.of(2026, 11, 22, 9, 0), true);
        assertEquals(AccessDecision.DENY, pinDenied.getDecision(), "0x6982 must deny");
        assertEquals(AccessReasonCode.PIN_NOT_VERIFIED, pinDenied.getReasonCode(), "0x6982 maps to PIN_NOT_VERIFIED");

        swGateway.useDelegate(new CardGateway() {
            @Override
            public CardGatewayResult recordAttendancePunch(
                    String cardId, AttendanceEventType eventType, LocalDateTime timestamp,
                    int areaCode, boolean allowRollover, boolean requireSecurityCheck) {
                return CardGatewayResult.ok("OK");
            }

            @Override
            public CardGatewayResult checkAreaAccess(
                    String cardId, int areaCode, LocalDateTime attemptTime, boolean requireSecurityCheck) {
                return CardGatewayResult.failure(CardGateway.SW_CARD_LOCKED_OR_REVOKED, "Chip locked");
            }
        });
        AccessAttemptLog lockDenied = svc.evaluateAccess(
                "C-ACT", "AREA-DEV", LocalDateTime.of(2026, 11, 22, 9, 0), true);
        assertEquals(AccessDecision.DENY, lockDenied.getDecision(), "0x6983 must deny");
        assertEquals(AccessReasonCode.CARD_LOCKED, lockDenied.getReasonCode(), "0x6983 maps to CARD_LOCKED");
    }

    private static void runTest(String id, ThrowingRunnable test) {
        try {
            test.run();
            passed++;
            System.out.println("[PASS] " + id);
        } catch (Throwable ex) {
            failed++;
            System.err.println("[FAIL] " + id + ": " + ex.getMessage());
            ex.printStackTrace(System.err);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " | expected=" + expected + ", actual=" + actual);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class TestFixture {
        final InMemoryEmployeeProvider employees;
        final InMemoryVirtualCardProvider cards;
        final InMemoryAccessPolicyProvider policies;
        final FileAttendanceRepository attRepo;
        final FileAccessLogRepository logRepo;
        final VirtualStoreCardGateway gateway;
        final AttendanceService attService;
        final ShiftPairingService shiftService;
        final AttendanceStatisticsService statsService;
        final AccessControlService accessService;

        TestFixture(
                InMemoryEmployeeProvider employees,
                InMemoryVirtualCardProvider cards,
                InMemoryAccessPolicyProvider policies,
                FileAttendanceRepository attRepo,
                FileAccessLogRepository logRepo,
                VirtualStoreCardGateway gateway,
                AttendanceService attService,
                ShiftPairingService shiftService,
                AttendanceStatisticsService statsService,
                AccessControlService accessService) {
            this.employees = employees;
            this.cards = cards;
            this.policies = policies;
            this.attRepo = attRepo;
            this.logRepo = logRepo;
            this.gateway = gateway;
            this.attService = attService;
            this.shiftService = shiftService;
            this.statsService = statsService;
            this.accessService = accessService;
        }
    }
}
