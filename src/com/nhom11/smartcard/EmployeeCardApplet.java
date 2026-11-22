/**
 * Copyright (c) 1998, 2026, Oracle and/or its affiliates.
 */

package com.nhom11.smartcard;

import javacard.framework.*;

/**
 * Java Card applet for employee smart card verification, attendance punching,
 * and card-side area access checking.
 */
public class EmployeeCardApplet extends Applet {

    private static final byte CLA_PROPRIETARY = (byte) 0x80;

    // Experimental photo-byte protocol. Not for production or real employee data.
    private static final byte INS_PHOTO_BEGIN = (byte) 0x10;
    private static final byte INS_PHOTO_WRITE = (byte) 0x11;
    private static final byte INS_PHOTO_FINISH = (byte) 0x12;
    private static final byte INS_PHOTO_READ = (byte) 0x13;
    private static final byte INS_VERIFY_PIN = (byte) 0x20;
    private static final byte INS_SET_CARD_STATUS = (byte) 0x30;
    private static final byte INS_SET_AREA_MASK = (byte) 0x32;
    private static final byte INS_GET_CARD_STATUS = (byte) 0x34;
    private static final byte INS_ATTENDANCE_PUNCH = (byte) 0x40;
    private static final byte INS_GET_LAST_ATTENDANCE = (byte) 0x42;
    private static final byte INS_CHECK_AREA_ACCESS = (byte) 0x50;

    private static final byte STATUS_ACTIVE = (byte) 0x00;
    private static final byte STATUS_LOCKED = (byte) 0x01;
    private static final byte STATUS_REVOKED = (byte) 0x02;

    private static final byte EVENT_CHECK_IN = (byte) 0x01;
    private static final byte EVENT_CHECK_OUT = (byte) 0x02;

    private static final byte MODE_STRICT = (byte) 0x00;
    private static final byte MODE_ALLOW_ROLLOVER = (byte) 0x01;

    private static final byte SHIFT_CLOSED = (byte) 0x00;
    private static final byte SHIFT_OPEN = (byte) 0x01;

    private static final byte DECISION_DENY = (byte) 0x00;
    private static final byte DECISION_ALLOW = (byte) 0x01;
    private static final byte REASON_OK = (byte) 0x00;
    private static final byte REASON_NO_PERMISSION = (byte) 0x04;

    private static final short SW_CARD_LOCKED_OR_REVOKED = (short) 0x6983;

    private static final byte PIN_TRY_LIMIT = (byte) 3;
    private static final byte MAX_PIN_SIZE = (byte) 8;
    private static final short AREA_MASK_SIZE = (short) 4;
    private static final short MAX_AREA_ID = (short) 32;
    private static final short MAX_ATTENDANCE_ENTRIES = (short) 16;
    private static final short RECORD_SIZE = (short) 6;
    private static final short PUNCH_PAYLOAD_SIZE = (short) 5;
    private static final short TIMESTAMP_SIZE = (short) 4;
    private static final short PHOTO_MAX_SIZE = (short) 4096;
    private static final short PHOTO_MAX_READ = (short) 128;

    // Development fallback PIN used only when install parameters omit app data.
    private static final byte[] DEFAULT_DEV_PIN = {
        (byte) 0x31, (byte) 0x32, (byte) 0x33, (byte) 0x34
    };

    private final OwnerPIN pin;
    private final byte[] areaPermissionMask;
    private final byte[] attendanceBuffer;
    private final byte[] photoBuffer;

    private short photoExpected;
    private short photoWritten;
    private boolean photoUploading;
    private boolean photoReady;

    private byte cardStatus;
    private byte currentShiftState;
    private byte punchCounter;
    private byte nextWriteIndex;
    private short lastRecordOffset;
    private boolean hasAttendanceRecord;

    /**
     * Installs this applet.
     *
     * @param bArray
     *            the array containing installation parameters
     * @param bOffset
     *            the starting offset in bArray
     * @param bLength
     *            the length in bytes of the parameter data in bArray
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new EmployeeCardApplet(bArray, bOffset, bLength);
    }

    /**
     * Creates and registers the applet, pre-allocating all fixed-size buffers.
     *
     * @param bArray
     *            installation parameters
     * @param bOffset
     *            starting offset
     * @param bLength
     *            parameter byte length
     */
    protected EmployeeCardApplet(byte[] bArray, short bOffset, byte bLength) {
        pin = new OwnerPIN(PIN_TRY_LIMIT, MAX_PIN_SIZE);
        areaPermissionMask = new byte[AREA_MASK_SIZE];
        attendanceBuffer = new byte[(short) (MAX_ATTENDANCE_ENTRIES * RECORD_SIZE)];
        photoBuffer = new byte[PHOTO_MAX_SIZE];

        cardStatus = STATUS_ACTIVE;
        currentShiftState = SHIFT_CLOSED;
        punchCounter = (byte) 0;
        nextWriteIndex = (byte) 0;
        lastRecordOffset = (short) 0;
        hasAttendanceRecord = false;

        short end = (short) (bOffset + (short) (bLength & (short) 0x00FF));
        short pos = bOffset;
        short aidLen = (short) 0;
        short aidOffset = bOffset;

        if (pos < end) {
            aidLen = (short) (bArray[pos] & (short) 0x00FF);
            aidOffset = (short) (pos + 1);
            pos = (short) (aidOffset + aidLen);
        }

        if (pos < end) {
            short privLen = (short) (bArray[pos] & (short) 0x00FF);
            pos = (short) (pos + 1 + privLen);
        }

        boolean pinConfigured = false;
        if (pos < end) {
            short paramLen = (short) (bArray[pos] & (short) 0x00FF);
            short paramOffset = (short) (pos + 1);
            if (paramLen > (short) 0 && paramLen <= (short) (MAX_PIN_SIZE & (short) 0x00FF)
                    && (short) (paramOffset + paramLen) <= end) {
                pin.update(bArray, paramOffset, (byte) paramLen);
                pinConfigured = true;
            }
        }

        if (!pinConfigured) {
            pin.update(DEFAULT_DEV_PIN, (short) 0, (byte) DEFAULT_DEV_PIN.length);
        }

        if (aidLen > (short) 0 && (short) (aidOffset + aidLen) <= end) {
            register(bArray, aidOffset, (byte) aidLen);
        } else {
            register();
        }
    }

    /**
     * Resets the transient PIN validation state when the applet is deselected.
     */
    @Override
    public void deselect() {
        pin.reset();
    }

    /**
     * Dispatches incoming proprietary APDUs without allocating heap objects.
     *
     * @param apdu
     *            the incoming APDU
     */
    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }

        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_CLA] != CLA_PROPRIETARY) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        byte ins = buf[ISO7816.OFFSET_INS];
        switch (ins) {
            case INS_PHOTO_BEGIN:
            case INS_PHOTO_WRITE:
            case INS_PHOTO_FINISH:
            case INS_PHOTO_READ:
                handlePhotoCommand(apdu, buf, ins);
                break;
            case INS_VERIFY_PIN:
                handleVerifyPin(apdu, buf);
                break;
            case INS_SET_CARD_STATUS:
                handleSetCardStatus(apdu, buf);
                break;
            case INS_SET_AREA_MASK:
                handleSetAreaMask(apdu, buf);
                break;
            case INS_GET_CARD_STATUS:
                handleGetCardStatus(apdu, buf);
                break;
            case INS_ATTENDANCE_PUNCH:
                handleAttendancePunch(apdu, buf);
                break;
            case INS_GET_LAST_ATTENDANCE:
                handleGetLastAttendance(apdu, buf);
                break;
            case INS_CHECK_AREA_ACCESS:
                handleCheckAreaAccess(apdu, buf);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
                break;
        }
    }

    /**
     * Verifies the cardholder PIN and returns remaining tries on mismatch.
     *
     * @param apdu
     *            current APDU
     * @param buf
     *            APDU buffer
     */
    private void handleVerifyPin(APDU apdu, byte[] buf) {
        if (buf[ISO7816.OFFSET_P1] != (byte) 0x00 || buf[ISO7816.OFFSET_P2] != (byte) 0x00) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
        if (cardStatus != STATUS_ACTIVE || pin.getTriesRemaining() == (byte) 0) {
            ISOException.throwIt(SW_CARD_LOCKED_OR_REVOKED);
        }

        short lc = apdu.setIncomingAndReceive();
        if (lc <= (short) 0 || lc > (short) (MAX_PIN_SIZE & (short) 0x00FF)) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        if (!pin.check(buf, ISO7816.OFFSET_CDATA, (byte) lc)) {
            short remaining = (short) (pin.getTriesRemaining() & (short) 0x000F);
            if (remaining == (short) 0) {
                ISOException.throwIt(SW_CARD_LOCKED_OR_REVOKED);
            }
            ISOException.throwIt((short) ((short) 0x63C0 | remaining));
        }
    }

    /**
     * Updates the lifecycle state of the card (ACTIVE, LOCKED, or REVOKED).
     *
     * @param apdu
     *            current APDU
     * @param buf
     *            APDU buffer
     */
    private void handleSetCardStatus(APDU apdu, byte[] buf) {
        byte newStatus = buf[ISO7816.OFFSET_P1];
        if (buf[ISO7816.OFFSET_P2] != (byte) 0x00) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
        if (newStatus != STATUS_ACTIVE && newStatus != STATUS_LOCKED && newStatus != STATUS_REVOKED) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }

        short lc = apdu.setIncomingAndReceive();
        if (lc != (short) 0) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        if (cardStatus == STATUS_REVOKED && newStatus != STATUS_REVOKED) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        cardStatus = newStatus;
        if (cardStatus != STATUS_ACTIVE) {
            pin.reset();
        }
    }

    /**
     * Stores the 32-bit area permission bitmask (areas 1..32) on the card.
     *
     * @param apdu
     *            current APDU
     * @param buf
     *            APDU buffer
     */
    private void handleSetAreaMask(APDU apdu, byte[] buf) {
        if (buf[ISO7816.OFFSET_P1] != (byte) 0x00 || buf[ISO7816.OFFSET_P2] != (byte) 0x00) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
        if (cardStatus == STATUS_REVOKED) {
            ISOException.throwIt(SW_CARD_LOCKED_OR_REVOKED);
        }

        short lc = apdu.setIncomingAndReceive();
        if (lc != AREA_MASK_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        Util.arrayCopy(buf, ISO7816.OFFSET_CDATA, areaPermissionMask, (short) 0, AREA_MASK_SIZE);
    }

    /**
     * Returns card status, PIN validation flag, punch counter, and shift state.
     *
     * @param apdu
     *            current APDU
     * @param buf
     *            APDU buffer
     */
    private void handleGetCardStatus(APDU apdu, byte[] buf) {
        if (buf[ISO7816.OFFSET_P1] != (byte) 0x00 || buf[ISO7816.OFFSET_P2] != (byte) 0x00) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }

        buf[0] = cardStatus;
        buf[1] = pin.isValidated() ? (byte) 0x01 : (byte) 0x00;
        buf[2] = punchCounter;
        buf[3] = currentShiftState;
        apdu.setOutgoingAndSend((short) 0, (short) 4);
    }

    /**
     * Records a check-in or check-out event into the fixed circular buffer.
     *
     * @param apdu
     *            current APDU
     * @param buf
     *            APDU buffer
     */
    private void handleAttendancePunch(APDU apdu, byte[] buf) {
        byte eventType = buf[ISO7816.OFFSET_P1];
        byte mode = buf[ISO7816.OFFSET_P2];

        if ((eventType != EVENT_CHECK_IN && eventType != EVENT_CHECK_OUT)
                || (mode != MODE_STRICT && mode != MODE_ALLOW_ROLLOVER)) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
        if (cardStatus != STATUS_ACTIVE) {
            ISOException.throwIt(SW_CARD_LOCKED_OR_REVOKED);
        }
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        short lc = apdu.setIncomingAndReceive();
        if (lc != PUNCH_PAYLOAD_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        byte areaId = buf[(short) (ISO7816.OFFSET_CDATA + TIMESTAMP_SIZE)];
        if (areaId == (byte) 0x00 || !hasNonZeroBytes(buf, ISO7816.OFFSET_CDATA, TIMESTAMP_SIZE)) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }

        if (mode == MODE_STRICT) {
            if (eventType == EVENT_CHECK_IN && currentShiftState == SHIFT_OPEN) {
                ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
            }
            if (eventType == EVENT_CHECK_OUT && currentShiftState != SHIFT_OPEN) {
                ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
            }
        }

        JCSystem.beginTransaction();
        short slotOffset = (short) ((short) (nextWriteIndex & (short) 0x00FF) * RECORD_SIZE);
        attendanceBuffer[slotOffset] = eventType;
        Util.arrayCopy(buf, ISO7816.OFFSET_CDATA, attendanceBuffer, (short) (slotOffset + 1), PUNCH_PAYLOAD_SIZE);
        lastRecordOffset = slotOffset;
        hasAttendanceRecord = true;
        nextWriteIndex = (byte) ((short) ((nextWriteIndex & (short) 0x00FF) + 1) % MAX_ATTENDANCE_ENTRIES);
        punchCounter = (byte) ((short) (punchCounter & (short) 0x00FF) + 1);
        currentShiftState = (eventType == EVENT_CHECK_IN) ? SHIFT_OPEN : SHIFT_CLOSED;
        JCSystem.commitTransaction();

        buf[0] = punchCounter;
        buf[1] = currentShiftState;
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }

    /**
     * Returns the latest recorded attendance event and current shift state.
     *
     * @param apdu
     *            current APDU
     * @param buf
     *            APDU buffer
     */
    private void handleGetLastAttendance(APDU apdu, byte[] buf) {
        if (buf[ISO7816.OFFSET_P1] != (byte) 0x00 || buf[ISO7816.OFFSET_P2] != (byte) 0x00) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }

        if (!hasAttendanceRecord) {
            Util.arrayFillNonAtomic(buf, (short) 0, RECORD_SIZE, (byte) 0x00);
        } else {
            Util.arrayCopyNonAtomic(attendanceBuffer, lastRecordOffset, buf, (short) 0, RECORD_SIZE);
        }
        buf[RECORD_SIZE] = currentShiftState;
        apdu.setOutgoingAndSend((short) 0, (short) (RECORD_SIZE + 1));
    }

    /**
     * Evaluates card-side eligibility for an area access check (areas 1..32).
     *
     * @param apdu
     *            current APDU
     * @param buf
     *            APDU buffer
     */
    private void handleCheckAreaAccess(APDU apdu, byte[] buf) {
        short areaId = (short) (buf[ISO7816.OFFSET_P1] & (short) 0x00FF);
        if (areaId < (short) 1 || areaId > MAX_AREA_ID || buf[ISO7816.OFFSET_P2] != (byte) 0x00) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
        if (cardStatus != STATUS_ACTIVE) {
            ISOException.throwIt(SW_CARD_LOCKED_OR_REVOKED);
        }
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        short lc = apdu.setIncomingAndReceive();
        if (lc != TIMESTAMP_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        if (!hasNonZeroBytes(buf, ISO7816.OFFSET_CDATA, TIMESTAMP_SIZE)) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }

        short zeroBased = (short) (areaId - 1);
        short byteIdx = (short) (zeroBased >> 3);
        short bitIdx = (short) (zeroBased & (short) 7);
        byte bitMask = (byte) ((short) 1 << bitIdx);
        boolean allowed = (byte) (areaPermissionMask[byteIdx] & bitMask) != (byte) 0;

        buf[0] = allowed ? DECISION_ALLOW : DECISION_DENY;
        buf[1] = allowed ? REASON_OK : REASON_NO_PERMISSION;
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }

    /** Experimental PIN-gated photo storage; transport and data are not encrypted. */
    private void handlePhotoCommand(APDU apdu, byte[] buf, byte ins) {
        if (cardStatus != STATUS_ACTIVE) {
            ISOException.throwIt(SW_CARD_LOCKED_OR_REVOKED);
        }
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        short offset = Util.makeShort(buf[ISO7816.OFFSET_P1], buf[ISO7816.OFFSET_P2]);
        switch (ins) {
            case INS_PHOTO_BEGIN:
                beginPhoto(offset);
                break;
            case INS_PHOTO_WRITE:
                writePhoto(apdu, offset);
                break;
            case INS_PHOTO_FINISH:
                if (offset != (short) 0) {
                    ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
                }
                finishPhoto();
                break;
            case INS_PHOTO_READ:
                readPhoto(apdu, offset);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    /** P1/P2 contain total length; beginning invalidates the old photo. */
    private void beginPhoto(short length) {
        if (length <= (short) 0 || length > PHOTO_MAX_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        JCSystem.beginTransaction();
        photoReady = false;
        photoExpected = length;
        photoWritten = (short) 0;
        photoUploading = true;
        JCSystem.commitTransaction();
    }

    /** Accepts only the next sequential chunk; data stays unreadable until finish. */
    private void writePhoto(APDU apdu, short offset) {
        if (!photoUploading || offset != photoWritten) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        short received = apdu.setIncomingAndReceive();
        short incoming = apdu.getIncomingLength();
        if (incoming <= (short) 0 || incoming > (short) (photoExpected - photoWritten)) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short total = (short) 0;
        short dataOffset = apdu.getOffsetCdata();
        while (total < incoming) {
            if (received <= (short) 0 || received > (short) (incoming - total)) {
                ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            }
            Util.arrayCopyNonAtomic(apdu.getBuffer(), dataOffset, photoBuffer,
                    (short) (offset + total), received);
            total = (short) (total + received);
            if (total < incoming) {
                received = apdu.receiveBytes(dataOffset);
            }
        }
        photoWritten = (short) (photoWritten + total);
    }

    /** Makes a complete upload readable. */
    private void finishPhoto() {
        if (!photoUploading || photoWritten != photoExpected) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        JCSystem.beginTransaction();
        photoUploading = false;
        photoReady = true;
        JCSystem.commitTransaction();
    }

    /** Reads at most 128 bytes, starting at P1/P2 and bounded by Le. */
    private void readPhoto(APDU apdu, short offset) {
        if (!photoReady) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        if (offset < (short) 0 || offset >= photoExpected) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
        short le = apdu.setOutgoing();
        short count = (short) (photoExpected - offset);
        if (count > PHOTO_MAX_READ) {
            count = PHOTO_MAX_READ;
        }
        if (le < count) {
            count = le;
        }
        if (count <= (short) 0) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        apdu.setOutgoingLength(count);
        apdu.sendBytesLong(photoBuffer, offset, count);
    }

    private boolean hasNonZeroBytes(byte[] data, short offset, short length) {
        for (short i = (short) 0; i < length; i++) {
            if (data[(short) (offset + i)] != (byte) 0x00) {
                return true;
            }
        }
        return false;
    }
}
