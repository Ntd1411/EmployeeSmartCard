package com.nhom11.smartcard.host.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Time-bounded temporary access grant assigned to an employee for an area.
 */
public final class TemporaryGrant {

    private final String grantId;
    private final String employeeId;
    private final String areaId;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final boolean revoked;
    private final String reason;

    /**
     * Creates a temporary access grant.
     *
     * @param grantId unique grant identifier
     * @param employeeId grantee employee identifier
     * @param areaId permitted area identifier
     * @param startTime inclusive start timestamp
     * @param endTime inclusive end timestamp
     * @param revoked whether the grant has been manually revoked
     * @param reason grant purpose note
     */
    public TemporaryGrant(
            String grantId,
            String employeeId,
            String areaId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            boolean revoked,
            String reason) {
        this.grantId = Objects.requireNonNull(grantId, "grantId");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId");
        this.areaId = Objects.requireNonNull(areaId, "areaId");
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.endTime = Objects.requireNonNull(endTime, "endTime");
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime must not precede startTime");
        }
        this.revoked = revoked;
        this.reason = reason == null ? "" : reason;
    }

    public String getGrantId() {
        return grantId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getAreaId() {
        return areaId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public String getReason() {
        return reason;
    }

    /**
     * Checks whether the grant is active at the given timestamp (inclusive bounds).
     *
     * @param attemptTime evaluation timestamp
     * @return true when not revoked and within [startTime, endTime]
     */
    public boolean isActiveAt(LocalDateTime attemptTime) {
        if (revoked || attemptTime == null) {
            return false;
        }
        return !attemptTime.isBefore(startTime) && !attemptTime.isAfter(endTime);
    }
}
