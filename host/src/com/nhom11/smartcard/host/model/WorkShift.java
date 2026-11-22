package com.nhom11.smartcard.host.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Paired work shift computed from attendance check-in and check-out records.
 */
public final class WorkShift {

    private final String shiftId;
    private final String employeeId;
    private final LocalDate workDate;
    private final String checkInRecordId;
    private final String checkOutRecordId;
    private final LocalDateTime checkInTime;
    private final LocalDateTime checkOutTime;
    private final long workedMinutes;
    private final BigDecimal workedHours;
    private final ShiftStatus status;

    /**
     * Creates a work shift summary.
     *
     * @param shiftId shift identifier
     * @param employeeId employee identifier
     * @param workDate shift work date
     * @param checkInRecordId check-in record ID (nullable)
     * @param checkOutRecordId check-out record ID (nullable)
     * @param checkInTime check-in timestamp (nullable)
     * @param checkOutTime check-out timestamp (nullable)
     * @param workedMinutes exact worked minutes
     * @param workedHours worked hours rounded to 2 decimal places
     * @param status pairing status
     */
    public WorkShift(
            String shiftId,
            String employeeId,
            LocalDate workDate,
            String checkInRecordId,
            String checkOutRecordId,
            LocalDateTime checkInTime,
            LocalDateTime checkOutTime,
            long workedMinutes,
            BigDecimal workedHours,
            ShiftStatus status) {
        this.shiftId = Objects.requireNonNull(shiftId, "shiftId");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId");
        this.workDate = Objects.requireNonNull(workDate, "workDate");
        this.checkInRecordId = checkInRecordId;
        this.checkOutRecordId = checkOutRecordId;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
        this.workedMinutes = workedMinutes;
        this.workedHours = Objects.requireNonNull(workedHours, "workedHours");
        this.status = Objects.requireNonNull(status, "status");
    }

    public String getShiftId() {
        return shiftId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public String getCheckInRecordId() {
        return checkInRecordId;
    }

    public String getCheckOutRecordId() {
        return checkOutRecordId;
    }

    public LocalDateTime getCheckInTime() {
        return checkInTime;
    }

    public LocalDateTime getCheckOutTime() {
        return checkOutTime;
    }

    public long getWorkedMinutes() {
        return workedMinutes;
    }

    public BigDecimal getWorkedHours() {
        return workedHours;
    }

    public ShiftStatus getStatus() {
        return status;
    }
}
