package com.nhom11.smartcard.host.service;

import com.nhom11.smartcard.host.model.AttendanceEventType;
import com.nhom11.smartcard.host.model.AttendanceRecord;
import com.nhom11.smartcard.host.model.AttendanceStatistics;
import com.nhom11.smartcard.host.model.Employee;
import com.nhom11.smartcard.host.model.ShiftStatus;
import com.nhom11.smartcard.host.model.WorkShift;
import com.nhom11.smartcard.host.provider.EmployeeProvider;
import com.nhom11.smartcard.host.repository.AttendanceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Service implementing US13: aggregating attendance punches, work days, and total hours
 * over a selected date range and employee/department scope.
 */
public class AttendanceStatisticsService {

    private static final BigDecimal ZERO_HOURS = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final EmployeeProvider employeeProvider;
    private final AttendanceRepository attendanceRepository;
    private final ShiftPairingService shiftPairingService;

    public AttendanceStatisticsService(
            EmployeeProvider employeeProvider,
            AttendanceRepository attendanceRepository,
            ShiftPairingService shiftPairingService) {
        this.employeeProvider = Objects.requireNonNull(employeeProvider, "employeeProvider");
        this.attendanceRepository = Objects.requireNonNull(attendanceRepository, "attendanceRepository");
        this.shiftPairingService = Objects.requireNonNull(shiftPairingService, "shiftPairingService");
    }

    /**
     * Computes attendance statistics per employee for the requested scope and inclusive date range.
     *
     * @param employeeIdFilter specific employee ID or null/blank for all
     * @param departmentFilter specific department or null/blank for all
     * @param fromDate inclusive start date
     * @param toDate inclusive end date
     * @return list of per-employee statistics rows
     */
    public List<AttendanceStatistics> calculateStatistics(
            String employeeIdFilter,
            String departmentFilter,
            LocalDate fromDate,
            LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("Khoảng ngày thống kê không được để trống.");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Ngày bắt đầu không được lớn hơn ngày kết thúc.");
        }

        List<Employee> targetEmployees = resolveEmployees(employeeIdFilter, departmentFilter);
        List<AttendanceStatistics> rows = new ArrayList<>(targetEmployees.size());

        for (Employee employee : targetEmployees) {
            List<AttendanceRecord> rangeRecords = attendanceRepository.findByEmployeeAndDateRange(
                    employee.getEmployeeId(), fromDate, toDate);

            int checkInCount = 0;
            int checkOutCount = 0;
            for (AttendanceRecord record : rangeRecords) {
                if (record.getEventType() == AttendanceEventType.CHECK_IN) {
                    checkInCount++;
                } else {
                    checkOutCount++;
                }
            }
            int totalPunches = checkInCount + checkOutCount;

            List<WorkShift> shifts = shiftPairingService.calculateShifts(
                    employee.getEmployeeId(), fromDate, toDate);

            int completedShifts = 0;
            int incompleteShifts = 0;
            long totalMinutes = 0L;
            Set<LocalDate> workDayDates = new HashSet<>();

            for (WorkShift shift : shifts) {
                if (shift.getStatus() == ShiftStatus.COMPLETE) {
                    completedShifts++;
                    totalMinutes += shift.getWorkedMinutes();
                    workDayDates.add(shift.getWorkDate());
                } else {
                    incompleteShifts++;
                }
            }

            int workDays = workDayDates.size();
            BigDecimal totalHours = ShiftPairingService.minutesToHours(totalMinutes);
            BigDecimal averageHours = workDays > 0
                    ? totalHours.divide(BigDecimal.valueOf(workDays), 2, RoundingMode.HALF_UP)
                    : ZERO_HOURS;

            rows.add(new AttendanceStatistics(
                    employee.getEmployeeId(),
                    employee.getFullName(),
                    employee.getDepartment(),
                    fromDate,
                    toDate,
                    totalPunches,
                    checkInCount,
                    checkOutCount,
                    completedShifts,
                    incompleteShifts,
                    workDays,
                    totalHours,
                    averageHours));
        }

        return rows;
    }

    private List<Employee> resolveEmployees(String employeeIdFilter, String departmentFilter) {
        String empFilter = employeeIdFilter == null ? "" : employeeIdFilter.trim();
        String deptFilter = departmentFilter == null ? "" : departmentFilter.trim();

        List<Employee> all = employeeProvider.findAll();
        List<Employee> matched = new ArrayList<>();
        for (Employee emp : all) {
            if (!empFilter.isEmpty() && !empFilter.equalsIgnoreCase(emp.getEmployeeId())) {
                continue;
            }
            if (!deptFilter.isEmpty() && !deptFilter.equalsIgnoreCase(emp.getDepartment())) {
                continue;
            }
            matched.add(emp);
        }
        return matched;
    }
}
