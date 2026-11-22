package com.nhom11.smartcard.host.repository;

import com.nhom11.smartcard.host.model.AttendanceRecord;
import java.time.LocalDate;
import java.util.List;

/**
 * Persistence contract for attendance punch records (US10, consumed by US11, US13, and US06).
 */
public interface AttendanceRepository {

    AttendanceRecord save(AttendanceRecord record);

    List<AttendanceRecord> findAll();

    List<AttendanceRecord> findByEmployee(String employeeId);

    List<AttendanceRecord> findByDateRange(LocalDate fromDate, LocalDate toDate);

    List<AttendanceRecord> findByEmployeeAndDateRange(String employeeId, LocalDate fromDate, LocalDate toDate);

    void clear();
}
