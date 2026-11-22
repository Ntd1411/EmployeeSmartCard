package com.nhom11.smartcard.host.provider;

import com.nhom11.smartcard.host.model.AttendanceEventType;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Runtime-switchable CardGateway allowing seamless toggle between virtual store and live jcsl.
 */
public class SwitchableCardGateway implements CardGateway {

    private volatile CardGateway activeDelegate;

    public SwitchableCardGateway(CardGateway initialDelegate) {
        this.activeDelegate = Objects.requireNonNull(initialDelegate, "initialDelegate");
    }

    public void useDelegate(CardGateway delegate) {
        this.activeDelegate = Objects.requireNonNull(delegate, "delegate");
    }

    public CardGateway getActiveDelegate() {
        return activeDelegate;
    }

    @Override
    public CardGatewayResult recordAttendancePunch(
            String cardId,
            AttendanceEventType eventType,
            LocalDateTime timestamp,
            int areaCode,
            boolean allowRollover,
            boolean requireSecurityCheck) {
        return activeDelegate.recordAttendancePunch(
                cardId, eventType, timestamp, areaCode, allowRollover, requireSecurityCheck);
    }

    @Override
    public CardGatewayResult checkAreaAccess(
            String cardId,
            int areaCode,
            LocalDateTime attemptTime,
            boolean requireSecurityCheck) {
        return activeDelegate.checkAreaAccess(cardId, areaCode, attemptTime, requireSecurityCheck);
    }
}
