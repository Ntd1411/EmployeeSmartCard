package com.nhom11.smartcard.host.provider;

import com.nhom11.smartcard.host.model.Employee;
import java.util.List;
import java.util.Optional;

/**
 * Contract for reading employee profile records (owned by US05).
 */
public interface EmployeeProvider {

    Optional<Employee> findById(String employeeId);

    List<Employee> findAll();

    boolean exists(String employeeId);
}
