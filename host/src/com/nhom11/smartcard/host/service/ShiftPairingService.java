package com.nhom11.smartcard.host.service;

import com.nhom11.smartcard.host.model.AttendanceEventType;
import com.nhom11.smartcard.host.model.AttendanceRecord;
import com.nhom11.smartcard.host.model.ShiftStatus;
import com.nhom11.smartcard.host.model.WorkShift;
import com.nhom11.smartcard.host.repository.AttendanceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Service implementing US11: chronological check-in/out pairing, overnight shift support,
 * and incomplete-shift handling.
 */
public class ShiftPairingService {

    public static final long MAX_SHIFT_MINUTES = 24L * 60L;
    private static final BigDecimal ZERO_HOURS = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60L);

    private final AttendanceRepository attendanceRepository;

    public ShiftPairingService(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = Objects.requireNonNull(attendanceRepository, "attendanceRepository");
    }

    /**
     * Pairs attendance records for an employee whose resulting workDate falls within [fromDate, toDate].
     * Reads adjacent next-day records so overnight shifts starting on {@code toDate} pair accurately.
     *
     * @param employeeId employee identifier (nullable for all employees)
     * @param fromDate inclusive start date
     * @param toDate inclusive end date
     * @return computed work shifts ordered chronologically
     */
    public List<WorkShift> calculateShifts(String employeeId, LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate and toDate must not be null");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }

        List<AttendanceRecord> rawRecords = (employeeId == null || employeeId.isBlank())
                ? attendanceRepository.findAll()
                : attendanceRepository.findByEmployee(employeeId.trim());

        List<WorkShift> allShifts = calculateShifts(rawRecords);
        List<WorkShift> filtered = new ArrayList<>();
        for (WorkShift shift : allShifts) {
            LocalDate workDate = shift.getWorkDate();
            if (!workDate.isBefore(fromDate) && !workDate.isAfter(toDate)) {
                filtered.add(shift);
            }
        }
        return filtered;
    }

    /**
     * Pairs an arbitrary list of attendance records grouped by employee in chronological order.
     *
     * @param records source punch records
     * @return paired shifts across all employees in the input
     */
    public List<WorkShift> calculateShifts(List<AttendanceRecord> records) {
        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, List<AttendanceRecord>> byEmployee = new TreeMap<>();
        for (AttendanceRecord record : records) {
            byEmployee.computeIfAbsent(record.getEmployeeId(), key -> new ArrayList<>()).add(record);
        }

        Comparator<AttendanceRecord> chronological = Comparator
                .comparing(AttendanceRecord::getTimestamp)
                .thenComparing(AttendanceRecord::getRecordId);

        List<WorkShift> result = new ArrayList<>();
        for (Map.Entry<String, List<AttendanceRecord>> entry : byEmployee.entrySet()) {
            String empId = entry.getKey();
            List<AttendanceRecord> empRecords = entry.getValue();
            empRecords.sort(chronological);

            AttendanceRecord openCheckIn = null;
            int seq = 1;

            for (AttendanceRecord record : empRecords) {
                if (record.getEventType() == AttendanceEventType.CHECK_IN) {
                    if (openCheckIn != null) {
                        result.add(buildIncompleteCheckOut(empId, seq++, openCheckIn));
                    }
                    openCheckIn = record;
                } else {
                    if (openCheckIn == null) {
                        result.add(buildIncompleteCheckIn(empId, seq++, record));
                    } else {
                        long minutes = ChronoUnit.MINUTES.between(openCheckIn.getTimestamp(), record.getTimestamp());
                        if (minutes <= 0L) {
                            result.add(new WorkShift(
                                    formatShiftId(empId, seq++),
                                    empId,
                                    openCheckIn.getTimestamp().toLocalDate(),
                                    openCheckIn.getRecordId(),
                                    record.getRecordId(),
                                    openCheckIn.getTimestamp(),
                                    record.getTimestamp(),
                                    0L,
                                    ZERO_HOURS,
                                    ShiftStatus.INVALID_ORDER));
                        } else if (minutes > MAX_SHIFT_MINUTES) {
                            result.add(buildIncompleteCheckOut(empId, seq++, openCheckIn));
                            result.add(buildIncompleteCheckIn(empId, seq++, record));
                        } else {
                            result.add(new WorkShift(
                                    formatShiftId(empId, seq++),
                                    empId,
                                    openCheckIn.getTimestamp().toLocalDate(),
                                    openCheckIn.getRecordId(),
                                    record.getRecordId(),
                                    openCheckIn.getTimestamp(),
                                    record.getTimestamp(),
                                    minutes,
                                    minutesToHours(minutes),
                                    ShiftStatus.COMPLETE));
                        }
                        openCheckIn = null;
                    }
                }
            }

            if (openCheckIn != null) {
                result.add(buildIncompleteCheckOut(empId, seq, openCheckIn));
            }
        }

        return result;
    }

    public static BigDecimal minutesToHours(long minutes) {
        if (minutes <= 0L) {
            return ZERO_HOURS;
        }
        return BigDecimal.valueOf(minutes).divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
    }

    private static WorkShift buildIncompleteCheckOut(String empId, int seq, AttendanceRecord checkIn) {
        return new WorkShift(
                formatShiftId(empId, seq),
                empId,
                checkIn.getTimestamp().toLocalDate(),
                checkIn.getRecordId(),
                null,
                checkIn.getTimestamp(),
                null,
                0L,
                ZERO_HOURS,
                ShiftStatus.MISSING_CHECK_OUT);
    }

    private static WorkShift buildIncompleteCheckIn(String empId, int seq, AttendanceRecord checkOut) {
        return new WorkShift(
                formatShiftId(empId, seq),
                empId,
                checkOut.getTimestamp().toLocalDate(),
                null,
                checkOut.getRecordId(),
                null,
                checkOut.getTimestamp(),
                0L,
                ZERO_HOURS,
                ShiftStatus.MISSING_CHECK_IN);
    }

    private static String formatShiftId(String empId, int seq) {
        return String.format("SH-%s-%03d", empId, seq);
    }
}
