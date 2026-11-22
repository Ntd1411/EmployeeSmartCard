package com.nhom11.smartcard.host.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Recorded outcome of an area access attempt in the simulation tool.
 */
public final class AccessAttemptLog {

    private final String logId;
    private final String cardId;
    private final String employeeId;
    private final String areaId;
    private final LocalDateTime attemptTime;
    private final AccessDecision decision;
    private final AccessReasonCode reasonCode;
    private final String reasonDetail;
    private final String matchedRuleId;

    /**
     * Creates an access attempt log entry.
     *
     * @param logId unique log entry identifier
     * @param cardId virtual card identifier
     * @param employeeId employee identifier (nullable if card unknown)
     * @param areaId area identifier
     * @param attemptTime simulated attempt timestamp
     * @param decision allow or deny outcome
     * @param reasonCode machine-readable reason code
     * @param reasonDetail human-readable explanation
     * @param matchedRuleId matching rule or temporary grant identifier (nullable)
     */
    public AccessAttemptLog(
            String logId,
            String cardId,
            String employeeId,
            String areaId,
            LocalDateTime attemptTime,
            AccessDecision decision,
            AccessReasonCode reasonCode,
            String reasonDetail,
            String matchedRuleId) {
        this.logId = Objects.requireNonNull(logId, "logId");
        this.cardId = Objects.requireNonNull(cardId, "cardId");
        this.employeeId = employeeId;
        this.areaId = Objects.requireNonNull(areaId, "areaId");
        this.attemptTime = Objects.requireNonNull(attemptTime, "attemptTime");
        this.decision = Objects.requireNonNull(decision, "decision");
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
        this.reasonDetail = Objects.requireNonNull(reasonDetail, "reasonDetail");
        this.matchedRuleId = matchedRuleId;
    }

    public String getLogId() {
        return logId;
    }

    public String getCardId() {
        return cardId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getAreaId() {
        return areaId;
    }

    public LocalDateTime getAttemptTime() {
        return attemptTime;
    }

    public AccessDecision getDecision() {
        return decision;
    }

    public AccessReasonCode getReasonCode() {
        return reasonCode;
    }

    public String getReasonDetail() {
        return reasonDetail;
    }

    public String getMatchedRuleId() {
        return matchedRuleId;
    }
}
