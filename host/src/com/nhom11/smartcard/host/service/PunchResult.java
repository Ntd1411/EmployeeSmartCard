package com.nhom11.smartcard.host.service;

import com.nhom11.smartcard.host.model.AttendanceRecord;
import java.util.Optional;

/**
 * Outcome returned by {@link AttendanceService} for a check-in or check-out request.
 */
public final class PunchResult {

    private final boolean accepted;
    private final String message;
    private final AttendanceRecord record;
    private final int cardStatusWord;

    private PunchResult(boolean accepted, String message, AttendanceRecord record, int cardStatusWord) {
        this.accepted = accepted;
        this.message = message;
        this.record = record;
        this.cardStatusWord = cardStatusWord;
    }

    public static PunchResult accepted(AttendanceRecord record, String message, int cardStatusWord) {
        return new PunchResult(true, message, record, cardStatusWord);
    }

    public static PunchResult rejected(String message, int cardStatusWord) {
        return new PunchResult(false, message, null, cardStatusWord);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getMessage() {
        return message;
    }

    public Optional<AttendanceRecord> getRecord() {
        return Optional.ofNullable(record);
    }

    public int getCardStatusWord() {
        return cardStatusWord;
    }
}
