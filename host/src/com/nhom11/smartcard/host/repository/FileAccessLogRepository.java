package com.nhom11.smartcard.host.repository;

import com.nhom11.smartcard.host.model.AccessAttemptLog;
import com.nhom11.smartcard.host.model.AccessDecision;
import com.nhom11.smartcard.host.model.AccessReasonCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Thread-safe UTF-8 TSV repository for access simulation logs with optional disk persistence.
 */
public class FileAccessLogRepository implements AccessLogRepository {

    private final Path storagePath;
    private final List<AccessAttemptLog> logs = new ArrayList<>();

    /**
     * Creates an in-memory-only repository.
     */
    public FileAccessLogRepository() {
        this.storagePath = null;
    }

    /**
     * Creates a disk-backed repository and loads existing logs if present.
     *
     * @param storagePath path to TSV backing file
     */
    public FileAccessLogRepository(Path storagePath) {
        this.storagePath = Objects.requireNonNull(storagePath, "storagePath");
        loadFromDisk();
    }

    @Override
    public synchronized AccessAttemptLog save(AccessAttemptLog log) {
        Objects.requireNonNull(log, "log");
        logs.add(log);
        persistToDisk();
        return log;
    }

    @Override
    public synchronized List<AccessAttemptLog> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(logs));
    }

    @Override
    public synchronized void clear() {
        logs.clear();
        persistToDisk();
    }

    private void loadFromDisk() {
        if (storagePath == null || !Files.exists(storagePath)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(storagePath, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length < 9) {
                    continue;
                }
                String empId = parts[2].isEmpty() ? null : unescape(parts[2]);
                String ruleId = parts[8].isEmpty() ? null : unescape(parts[8]);
                logs.add(new AccessAttemptLog(
                        unescape(parts[0]),
                        unescape(parts[1]),
                        empId,
                        unescape(parts[3]),
                        LocalDateTime.parse(parts[4]),
                        AccessDecision.valueOf(parts[5]),
                        AccessReasonCode.valueOf(parts[6]),
                        unescape(parts[7]),
                        ruleId));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load access logs from " + storagePath, ex);
        }
    }

    private void persistToDisk() {
        if (storagePath == null) {
            return;
        }
        try {
            if (storagePath.getParent() != null) {
                Files.createDirectories(storagePath.getParent());
            }
            List<String> lines = new ArrayList<>(logs.size());
            for (AccessAttemptLog log : logs) {
                lines.add(String.join("\t",
                        escape(log.getLogId()),
                        escape(log.getCardId()),
                        escape(log.getEmployeeId()),
                        escape(log.getAreaId()),
                        log.getAttemptTime().toString(),
                        log.getDecision().name(),
                        log.getReasonCode().name(),
                        escape(log.getReasonDetail()),
                        escape(log.getMatchedRuleId())));
            }
            Files.write(storagePath, lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist access logs to " + storagePath, ex);
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaping) {
                if (ch == 't') {
                    out.append('\t');
                } else if (ch == 'n') {
                    out.append('\n');
                } else {
                    out.append(ch);
                }
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else {
                out.append(ch);
            }
        }
        if (escaping) {
            out.append('\\');
        }
        return out.toString();
    }
}
