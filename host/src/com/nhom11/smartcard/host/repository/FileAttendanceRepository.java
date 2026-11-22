package com.nhom11.smartcard.host.repository;

import com.nhom11.smartcard.host.model.AttendanceEventType;
import com.nhom11.smartcard.host.model.AttendanceRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Thread-safe UTF-8 TSV repository for attendance records with optional disk persistence.
 */
public class FileAttendanceRepository implements AttendanceRepository {

    private final Path storagePath;
    private final List<AttendanceRecord> records = new ArrayList<>();

    /**
     * Creates an in-memory-only repository.
     */
    public FileAttendanceRepository() {
        this.storagePath = null;
    }

    /**
     * Creates a disk-backed repository and loads existing records if present.
     *
     * @param storagePath path to TSV backing file
     */
    public FileAttendanceRepository(Path storagePath) {
        this.storagePath = Objects.requireNonNull(storagePath, "storagePath");
        loadFromDisk();
    }

    @Override
    public synchronized AttendanceRecord save(AttendanceRecord record) {
        Objects.requireNonNull(record, "record");
        records.add(record);
        persistToDisk();
        return record;
    }

    @Override
    public synchronized List<AttendanceRecord> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    @Override
    public synchronized List<AttendanceRecord> findByEmployee(String employeeId) {
        if (employeeId == null) {
            return Collections.emptyList();
        }
        List<AttendanceRecord> result = new ArrayList<>();
        for (AttendanceRecord record : records) {
            if (employeeId.equals(record.getEmployeeId())) {
                result.add(record);
            }
        }
        return result;
    }

    @Override
    public synchronized List<AttendanceRecord> findByDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate and toDate must not be null");
        }
        List<AttendanceRecord> result = new ArrayList<>();
        for (AttendanceRecord record : records) {
            LocalDate date = record.getTimestamp().toLocalDate();
            if (!date.isBefore(fromDate) && !date.isAfter(toDate)) {
                result.add(record);
            }
        }
        return result;
    }

    @Override
    public synchronized List<AttendanceRecord> findByEmployeeAndDateRange(
            String employeeId,
            LocalDate fromDate,
            LocalDate toDate) {
        if (employeeId == null) {
            return Collections.emptyList();
        }
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate and toDate must not be null");
        }
        List<AttendanceRecord> result = new ArrayList<>();
        for (AttendanceRecord record : records) {
            LocalDate date = record.getTimestamp().toLocalDate();
            if (employeeId.equals(record.getEmployeeId())
                    && !date.isBefore(fromDate)
                    && !date.isAfter(toDate)) {
                result.add(record);
            }
        }
        return result;
    }

    @Override
    public synchronized void clear() {
        records.clear();
        persistToDisk();
    }

    private void loadFromDisk() {
        if (storagePath == null || !Files.exists(storagePath)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(storagePath, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length < 7) {
                    continue;
                }
                records.add(new AttendanceRecord(
                        unescape(parts[0]),
                        unescape(parts[1]),
                        unescape(parts[2]),
                        AttendanceEventType.valueOf(parts[3]),
                        LocalDateTime.parse(parts[4]),
                        unescape(parts[5]),
                        unescape(parts[6])));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load attendance records from " + storagePath, ex);
        }
    }

    private void persistToDisk() {
        if (storagePath == null) {
            return;
        }
        try {
            if (storagePath.getParent() != null) {
                Files.createDirectories(storagePath.getParent());
            }
            List<String> lines = new ArrayList<>(records.size());
            for (AttendanceRecord r : records) {
                lines.add(String.join("\t",
                        escape(r.getRecordId()),
                        escape(r.getEmployeeId()),
                        escape(r.getCardId()),
                        r.getEventType().name(),
                        r.getTimestamp().toString(),
                        escape(r.getSource()),
                        escape(r.getNote())));
            }
            Files.write(storagePath, lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist attendance records to " + storagePath, ex);
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaping) {
                if (ch == 't') {
                    out.append('\t');
                } else if (ch == 'n') {
                    out.append('\n');
                } else {
                    out.append(ch);
                }
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else {
                out.append(ch);
            }
        }
        if (escaping) {
            out.append('\\');
        }
        return out.toString();
    }
}
