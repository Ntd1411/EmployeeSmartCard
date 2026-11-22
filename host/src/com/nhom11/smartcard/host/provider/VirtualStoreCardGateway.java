package com.nhom11.smartcard.host.provider;

import com.nhom11.smartcard.host.model.AttendanceEventType;
import com.nhom11.smartcard.host.model.CardStatus;
import com.nhom11.smartcard.host.model.VirtualCard;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-process card gateway mirroring the applet's lifecycle, PIN, shift-state, and SW rules.
 */
public class VirtualStoreCardGateway implements CardGateway {

    private final VirtualCardProvider cardProvider;
    private final Map<String, Boolean> openShiftByCard = new HashMap<>();

    public VirtualStoreCardGateway(VirtualCardProvider cardProvider) {
        this.cardProvider = Objects.requireNonNull(cardProvider, "cardProvider");
    }

    public void syncShiftState(String cardId, boolean shiftOpen) {
        openShiftByCard.put(cardId, shiftOpen);
    }

    public void resetAllShiftStates() {
        openShiftByCard.clear();
    }

    @Override
    public CardGatewayResult recordAttendancePunch(
            String cardId,
            AttendanceEventType eventType,
            LocalDateTime timestamp,
            int areaCode,
            boolean allowRollover,
            boolean requireSecurityCheck) {
        Optional<VirtualCard> cardOpt = cardProvider.findByCardId(cardId);
        if (cardOpt.isEmpty()) {
            return CardGatewayResult.failure(SW_WRONG_DATA, "Card not found on virtual store");
        }
        VirtualCard card = cardOpt.get();
        if (card.getStatus() != CardStatus.ACTIVE) {
            return CardGatewayResult.failure(SW_CARD_LOCKED_OR_REVOKED, "Card is locked or revoked");
        }
        if (requireSecurityCheck && (!card.isPinVerified() || !card.isCryptoVerified())) {
            return CardGatewayResult.failure(SW_SECURITY_STATUS_NOT_SATISFIED, "PIN or crypto not verified");
        }
        if (timestamp == null || areaCode < 1 || areaCode > 32) {
            return CardGatewayResult.failure(SW_WRONG_DATA, "Invalid punch timestamp or area code");
        }

        boolean currentlyOpen = openShiftByCard.getOrDefault(cardId, false);
        if (!allowRollover) {
            if (eventType == AttendanceEventType.CHECK_IN && currentlyOpen) {
                return CardGatewayResult.failure(SW_CONDITIONS_NOT_SATISFIED, "Shift already open on card");
            }
            if (eventType == AttendanceEventType.CHECK_OUT && !currentlyOpen) {
                return CardGatewayResult.failure(SW_CONDITIONS_NOT_SATISFIED, "No open shift on card");
            }
        }

        openShiftByCard.put(cardId, eventType == AttendanceEventType.CHECK_IN);
        return CardGatewayResult.ok("Recorded on virtual card");
    }

    @Override
    public CardGatewayResult checkAreaAccess(
            String cardId,
            int areaCode,
            LocalDateTime attemptTime,
            boolean requireSecurityCheck) {
        Optional<VirtualCard> cardOpt = cardProvider.findByCardId(cardId);
        if (cardOpt.isEmpty()) {
            return CardGatewayResult.failure(SW_WRONG_DATA, "Card not found on virtual store");
        }
        VirtualCard card = cardOpt.get();
        if (card.getStatus() != CardStatus.ACTIVE) {
            return CardGatewayResult.failure(SW_CARD_LOCKED_OR_REVOKED, "Card is locked or revoked");
        }
        if (requireSecurityCheck && (!card.isPinVerified() || !card.isCryptoVerified())) {
            return CardGatewayResult.failure(SW_SECURITY_STATUS_NOT_SATISFIED, "PIN or crypto not verified");
        }
        if (attemptTime == null || areaCode < 1 || areaCode > 32) {
            return CardGatewayResult.failure(SW_WRONG_DATA, "Invalid area code or timestamp");
        }
        return CardGatewayResult.allowed(SW_NO_ERROR, "Card eligible for access check");
    }
}
