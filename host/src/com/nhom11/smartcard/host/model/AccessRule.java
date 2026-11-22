package com.nhom11.smartcard.host.model;

import java.util.Objects;

/**
 * Permanent area access rule matched against employee department and position.
 */
public final class AccessRule {

    /**
     * Matching mode when both department and position criteria exist.
     */
    public enum MatchMode {
        DEPARTMENT_OR_POSITION,
        DEPARTMENT_AND_POSITION
    }

    private final String ruleId;
    private final String areaId;
    private final String department;
    private final String position;
    private final MatchMode matchMode;
    private final boolean enabled;

    /**
     * Creates a permanent access rule.
     *
     * @param ruleId unique rule identifier
     * @param areaId target area identifier
     * @param department target department (nullable when matching by position only)
     * @param position target position (nullable when matching by department only)
     * @param matchMode matching mode between department and position
     * @param enabled whether the rule is active
     */
    public AccessRule(
            String ruleId,
            String areaId,
            String department,
            String position,
            MatchMode matchMode,
            boolean enabled) {
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId");
        this.areaId = Objects.requireNonNull(areaId, "areaId");
        this.department = normalizeNullable(department);
        this.position = normalizeNullable(position);
        this.matchMode = Objects.requireNonNull(matchMode, "matchMode");
        this.enabled = enabled;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getAreaId() {
        return areaId;
    }

    public String getDepartment() {
        return department;
    }

    public String getPosition() {
        return position;
    }

    public MatchMode getMatchMode() {
        return matchMode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Evaluates whether this enabled rule matches the given employee.
     *
     * @param employee candidate employee
     * @return true if the employee satisfies this rule
     */
    public boolean matches(Employee employee) {
        if (!enabled || employee == null) {
            return false;
        }
        boolean hasDept = department != null;
        boolean hasPos = position != null;
        if (!hasDept && !hasPos) {
            return false;
        }
        boolean deptMatch = hasDept && department.equalsIgnoreCase(employee.getDepartment().trim());
        boolean posMatch = hasPos && position.equalsIgnoreCase(employee.getPosition().trim());
        if (hasDept && hasPos) {
            return matchMode == MatchMode.DEPARTMENT_AND_POSITION
                    ? (deptMatch && posMatch)
                    : (deptMatch || posMatch);
        }
        return deptMatch || posMatch;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
