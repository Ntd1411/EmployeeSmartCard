package com.nhom11.smartcard.host.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Simulated virtual smart card linked to an employee.
 */
public final class VirtualCard {

    private final String cardId;
    private final String employeeId;
    private CardStatus status;
    private boolean pinVerified;
    private boolean cryptoVerified;
    private final LocalDateTime issuedAt;

    /**
     * Creates a virtual card record.
     *
     * @param cardId unique virtual card identifier
     * @param employeeId owner employee identifier
     * @param status current lifecycle status
     * @param pinVerified whether PIN verification has succeeded
     * @param cryptoVerified whether simulated AES/RSA verification succeeded
     * @param issuedAt issuance timestamp
     */
    public VirtualCard(
            String cardId,
            String employeeId,
            CardStatus status,
            boolean pinVerified,
            boolean cryptoVerified,
            LocalDateTime issuedAt) {
        this.cardId = Objects.requireNonNull(cardId, "cardId");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId");
        this.status = Objects.requireNonNull(status, "status");
        this.pinVerified = pinVerified;
        this.cryptoVerified = cryptoVerified;
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
    }

    public String getCardId() {
        return cardId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public CardStatus getStatus() {
        return status;
    }

    public void setStatus(CardStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public boolean isPinVerified() {
        return pinVerified;
    }

    public void setPinVerified(boolean pinVerified) {
        this.pinVerified = pinVerified;
    }

    public boolean isCryptoVerified() {
        return cryptoVerified;
    }

    public void setCryptoVerified(boolean cryptoVerified) {
        this.cryptoVerified = cryptoVerified;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    @Override
    public String toString() {
        return cardId + " [" + status + "]";
    }
}
