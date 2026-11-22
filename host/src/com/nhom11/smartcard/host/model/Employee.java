package com.nhom11.smartcard.host.model;

import java.util.Objects;

/**
 * Shared employee record model aligned with the employee profile module.
 */
public final class Employee {

    private final String employeeId;
    private final String fullName;
    private final String department;
    private final String position;
    private final boolean active;

    /**
     * Creates an employee record.
     *
     * @param employeeId unique employee identifier
     * @param fullName employee display name
     * @param department department name
     * @param position job title or position
     * @param active whether the employee is currently employed
     */
    public Employee(String employeeId, String fullName, String department, String position, boolean active) {
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId");
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.department = Objects.requireNonNull(department, "department");
        this.position = Objects.requireNonNull(position, "position");
        this.active = active;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getDepartment() {
        return department;
    }

    public String getPosition() {
        return position;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public String toString() {
        return employeeId + " - " + fullName + " (" + department + " / " + position + ")";
    }
}
