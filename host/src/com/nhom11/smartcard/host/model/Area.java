package com.nhom11.smartcard.host.model;

import java.util.Objects;

/**
 * Restricted physical or logical area managed by access simulation.
 */
public final class Area {

    private final String areaId;
    private final int areaCode;
    private final String areaName;
    private final int securityLevel;

    /**
     * Creates an area definition.
     *
     * @param areaId unique string identifier
     * @param areaCode numeric APDU area code (1..32)
     * @param areaName human-readable area name
     * @param securityLevel security tier indicator
     */
    public Area(String areaId, int areaCode, String areaName, int securityLevel) {
        if (areaCode < 1 || areaCode > 32) {
            throw new IllegalArgumentException("areaCode must be in range 1..32");
        }
        this.areaId = Objects.requireNonNull(areaId, "areaId");
        this.areaCode = areaCode;
        this.areaName = Objects.requireNonNull(areaName, "areaName");
        this.securityLevel = securityLevel;
    }

    public String getAreaId() {
        return areaId;
    }

    public int getAreaCode() {
        return areaCode;
    }

    public String getAreaName() {
        return areaName;
    }

    public int getSecurityLevel() {
        return securityLevel;
    }

    @Override
    public String toString() {
        return areaId + " - " + areaName;
    }
}
