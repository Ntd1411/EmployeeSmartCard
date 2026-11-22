package com.nhom11.smartcard.host.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable attendance check-in or check-out event stored by the host.
 */
public final class AttendanceRecord {

    private final String recordId;
    private final String employeeId;
    private final String cardId;
    private final AttendanceEventType eventType;
    private final LocalDateTime timestamp;
    private final String source;
    private final String note;

    /**
     * Creates an attendance punch record.
     *
     * @param recordId unique record identifier
     * @param employeeId employee identifier
     * @param cardId virtual card identifier
     * @param eventType check-in or check-out event
     * @param timestamp punch timestamp
     * @param source origin identifier (e.g. HOST_UI or APDU_SIM)
     * @param note optional annotation
     */
    public AttendanceRecord(
            String recordId,
            String employeeId,
            String cardId,
            AttendanceEventType eventType,
            LocalDateTime timestamp,
            String source,
            String note) {
        this.recordId = Objects.requireNonNull(recordId, "recordId");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId");
        this.cardId = Objects.requireNonNull(cardId, "cardId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.source = source == null ? "HOST_UI" : source;
        this.note = note == null ? "" : note;
    }

    public String getRecordId() {
        return recordId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getCardId() {
        return cardId;
    }

    public AttendanceEventType getEventType() {
        return eventType;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getSource() {
        return source;
    }

    public String getNote() {
        return note;
    }
}
