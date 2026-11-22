package com.nhom11.smartcard.host.provider;

import com.nhom11.smartcard.host.model.AttendanceEventType;
import java.time.LocalDateTime;

/**
 * Abstraction over card-side commands, decoupling host services from simulator vs virtual store.
 */
public interface CardGateway {

    int SW_NO_ERROR = 0x9000;
    int SW_SECURITY_STATUS_NOT_SATISFIED = 0x6982;
    int SW_CARD_LOCKED_OR_REVOKED = 0x6983;
    int SW_CONDITIONS_NOT_SATISFIED = 0x6985;
    int SW_WRONG_DATA = 0x6A80;

    CardGatewayResult recordAttendancePunch(
            String cardId,
            AttendanceEventType eventType,
            LocalDateTime timestamp,
            int areaCode,
            boolean allowRollover,
            boolean requireSecurityCheck);

    CardGatewayResult checkAreaAccess(
            String cardId,
            int areaCode,
            LocalDateTime attemptTime,
            boolean requireSecurityCheck);

    /**
     * Immutable card gateway response mirroring ISO7816 status words.
     */
    final class CardGatewayResult {

        private final boolean success;
        private final int statusWord;
        private final boolean cardAllowed;
        private final String message;

        private CardGatewayResult(boolean success, int statusWord, boolean cardAllowed, String message) {
            this.success = success;
            this.statusWord = statusWord;
            this.cardAllowed = cardAllowed;
            this.message = message;
        }

        public static CardGatewayResult ok(String message) {
            return new CardGatewayResult(true, SW_NO_ERROR, true, message);
        }

        public static CardGatewayResult allowed(int statusWord, String message) {
            return new CardGatewayResult(true, statusWord, true, message);
        }

        public static CardGatewayResult failure(int statusWord, String message) {
            return new CardGatewayResult(false, statusWord, false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public int getStatusWord() {
            return statusWord;
        }

        public boolean isCardAllowed() {
            return cardAllowed;
        }

        public String getMessage() {
            return message;
        }
    }
}
