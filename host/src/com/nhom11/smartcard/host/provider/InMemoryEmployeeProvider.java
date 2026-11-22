package com.nhom11.smartcard.host.provider;

import com.nhom11.smartcard.host.model.Employee;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of {@link EmployeeProvider} for standalone development and testing.
 */
public class InMemoryEmployeeProvider implements EmployeeProvider {

    private final Map<String, Employee> employees = new LinkedHashMap<>();

    public void put(Employee employee) {
        employees.put(employee.getEmployeeId(), employee);
    }

    public void clear() {
        employees.clear();
    }

    @Override
    public Optional<Employee> findById(String employeeId) {
        if (employeeId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(employees.get(employeeId));
    }

    @Override
    public List<Employee> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(employees.values()));
    }

    @Override
    public boolean exists(String employeeId) {
        return employeeId != null && employees.containsKey(employeeId);
    }
}
