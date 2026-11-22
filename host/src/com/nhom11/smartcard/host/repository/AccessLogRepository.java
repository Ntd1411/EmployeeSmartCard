package com.nhom11.smartcard.host.repository;

import com.nhom11.smartcard.host.model.AccessAttemptLog;
import java.util.List;

/**
 * Persistence contract for area access simulation logs (US12).
 */
public interface AccessLogRepository {

    AccessAttemptLog save(AccessAttemptLog log);

    List<AccessAttemptLog> findAll();

    void clear();
}
