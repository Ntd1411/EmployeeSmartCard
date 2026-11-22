package com.nhom11.smartcard.host.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Aggregated attendance statistics for a single employee over a date range.
 */
public final class AttendanceStatistics {

    private final String employeeId;
    private final String fullName;
    private final String department;
    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final int totalPunches;
    private final int checkInCount;
    private final int checkOutCount;
    private final int completedShifts;
    private final int incompleteShifts;
    private final int workDays;
    private final BigDecimal totalHours;
    private final BigDecimal averageHoursPerWorkDay;

    /**
     * Creates an attendance statistics summary row.
     *
     * @param employeeId employee identifier
     * @param fullName employee name
     * @param department employee department
     * @param fromDate inclusive start date
     * @param toDate inclusive end date
     * @param totalPunches total punch count
     * @param checkInCount number of check-in punches
     * @param checkOutCount number of check-out punches
     * @param completedShifts number of COMPLETE shifts
     * @param incompleteShifts number of incomplete or invalid shifts
     * @param workDays unique calendar days with at least one COMPLETE shift
     * @param totalHours total worked hours across COMPLETE shifts (scale 2)
     * @param averageHoursPerWorkDay average hours per work day (scale 2)
     */
    public AttendanceStatistics(
            String employeeId,
            String fullName,
            String department,
            LocalDate fromDate,
            LocalDate toDate,
            int totalPunches,
            int checkInCount,
            int checkOutCount,
            int completedShifts,
            int incompleteShifts,
            int workDays,
            BigDecimal totalHours,
            BigDecimal averageHoursPerWorkDay) {
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId");
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.department = Objects.requireNonNull(department, "department");
        this.fromDate = Objects.requireNonNull(fromDate, "fromDate");
        this.toDate = Objects.requireNonNull(toDate, "toDate");
        this.totalPunches = totalPunches;
        this.checkInCount = checkInCount;
        this.checkOutCount = checkOutCount;
        this.completedShifts = completedShifts;
        this.incompleteShifts = incompleteShifts;
        this.workDays = workDays;
        this.totalHours = Objects.requireNonNull(totalHours, "totalHours");
        this.averageHoursPerWorkDay = Objects.requireNonNull(averageHoursPerWorkDay, "averageHoursPerWorkDay");
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getDepartment() {
        return department;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public int getTotalPunches() {
        return totalPunches;
    }

    public int getCheckInCount() {
        return checkInCount;
    }

    public int getCheckOutCount() {
        return checkOutCount;
    }

    public int getCompletedShifts() {
        return completedShifts;
    }

    public int getIncompleteShifts() {
        return incompleteShifts;
    }

    public int getWorkDays() {
        return workDays;
    }

    public BigDecimal getTotalHours() {
        return totalHours;
    }

    public BigDecimal getAverageHoursPerWorkDay() {
        return averageHoursPerWorkDay;
    }
}
