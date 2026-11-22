package com.nhom11.smartcard.host.model;

/**
 * Detailed outcome reason code produced by the 7-step access evaluation pipeline.
 */
public enum AccessReasonCode {
    PERMANENT_ROLE_MATCH,
    TEMPORARY_GRANT_VALID,
    CARD_NOT_FOUND,
    CARD_LOCKED,
    CARD_REVOKED,
    PIN_NOT_VERIFIED,
    CRYPTO_INVALID,
    EMPLOYEE_NOT_FOUND,
    AREA_NOT_FOUND,
    TEMP_ACCESS_EXPIRED,
    TEMP_ACCESS_NOT_YET_ACTIVE,
    NO_PERMISSION
}
