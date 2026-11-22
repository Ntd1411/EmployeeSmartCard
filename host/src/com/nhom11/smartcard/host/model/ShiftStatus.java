package com.nhom11.smartcard.host.model;

/**
 * Pairing and completeness status of a computed work shift.
 */
public enum ShiftStatus {
    COMPLETE,
    MISSING_CHECK_OUT,
    MISSING_CHECK_IN,
    INVALID_ORDER
}
