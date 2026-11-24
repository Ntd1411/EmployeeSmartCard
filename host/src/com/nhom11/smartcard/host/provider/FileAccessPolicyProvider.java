package com.nhom11.smartcard.host.provider;

import com.nhom11.smartcard.host.model.AccessRule;
import com.nhom11.smartcard.host.model.Area;
import com.nhom11.smartcard.host.model.TemporaryGrant;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Persistent host-side demo policy store. Not a card security boundary. */
public final class FileAccessPolicyProvider implements AccessPolicyProvider {
    private static final String HEADER = "EMPLOYEE_ACCESS_POLICY_V1";
    private final Path file;
    private final EmployeeProvider employees;
    private final Runnable requireAdmin;
    private final List<Area> areas;
    private List<AccessRule> rules;
    private List<TemporaryGrant> grants;

    /** Seed once only when no file exists. A broken file fails closed and is never overwritten. */
    public FileAccessPolicyProvider(Path file, EmployeeProvider employees, InMemoryAccessPolicyProvider seed) throws IOException {
        this(file, employees, seed, () -> { });
    }

    public FileAccessPolicyProvider(Path file, EmployeeProvider employees, InMemoryAccessPolicyProvider seed, Runnable requireAdmin) throws IOException {
        this.file = Objects.requireNonNull(file).toAbsolutePath();
        this.employees = Objects.requireNonNull(employees);
        this.requireAdmin = Objects.requireNonNull(requireAdmin);
        this.areas = List.copyOf(seed.findAllAreas());
        List<AccessRule> initialRules = new ArrayList<>();
        List<TemporaryGrant> initialGrants = new ArrayList<>();
        for (Area area : areas) {
            initialRules.addAll(seed.findPermanentRules(area.getAreaId()));
            for (var employee : employees.findAll()) {
                initialGrants.addAll(seed.findTemporaryGrants(employee.getEmployeeId(), area.getAreaId()));
            }
        }
        if (Files.notExists(this.file)) {
            validateAll(initialRules, initialGrants);
            persist(initialRules, initialGrants);
            rules = initialRules;
            grants = initialGrants;
        } else {
            load();
        }
    }

    @Override public Optional<Area> findAreaById(String id) {
        return areas.stream().filter(a -> a.getAreaId().equals(id)).findFirst();
    }
    @Override public List<Area> findAllAreas() { return areas; }
    @Override public List<AccessRule> findPermanentRules(String id) {
        return rules.stream().filter(r -> r.getAreaId().equals(id)).toList();
    }
    @Override public List<TemporaryGrant> findTemporaryGrants(String employeeId, String areaId) {
        return grants.stream().filter(g -> g.getEmployeeId().equals(employeeId) && g.getAreaId().equals(areaId)).toList();
    }
    public List<AccessRule> listRules() { return Collections.unmodifiableList(new ArrayList<>(rules)); }
    public List<TemporaryGrant> listGrants() { return Collections.unmodifiableList(new ArrayList<>(grants)); }

    /** Inserts or replaces by immutable ID. File is replaced before the live snapshot changes. */
    public synchronized void saveRule(AccessRule rule) throws IOException {
        requireAdmin.run();
        Objects.requireNonNull(rule);
        List<AccessRule> next = new ArrayList<>(rules);
        next.removeIf(r -> r.getRuleId().equals(rule.getRuleId()));
        next.add(rule);
        validateAll(next, grants);
        persist(next, grants);
        rules = next;
    }
    public synchronized void saveGrant(TemporaryGrant grant) throws IOException {
        requireAdmin.run();
        Objects.requireNonNull(grant);
        for (TemporaryGrant old : grants) {
            if (old.getGrantId().equals(grant.getGrantId()) && old.isRevoked() && !grant.isRevoked()) {
                throw new IllegalArgumentException("Quyền đã thu hồi không thể kích hoạt lại; hãy cấp quyền mới.");
            }
        }
        List<TemporaryGrant> next = new ArrayList<>(grants);
        next.removeIf(g -> g.getGrantId().equals(grant.getGrantId()));
        next.add(grant);
        validateAll(rules, next);
        persist(rules, next);
        grants = next;
    }
    private void validateAll(List<AccessRule> rs, List<TemporaryGrant> gs) {
        Set<String> ids = new HashSet<>();
        for (AccessRule r : rs) {
            if (r.getRuleId().isBlank() || !ids.add(r.getRuleId()) || findAreaById(r.getAreaId()).isEmpty()
                    || (r.getDepartment() == null && r.getPosition() == null)) {
                throw new IllegalArgumentException("Quy tắc không hợp lệ hoặc trùng mã: " + r.getRuleId());
            }
        }
        ids.clear();
        for (TemporaryGrant g : gs) {
            if (g.getGrantId().isBlank() || !ids.add(g.getGrantId()) || findAreaById(g.getAreaId()).isEmpty()
                    || !employees.exists(g.getEmployeeId())) {
                throw new IllegalArgumentException("Quyền tạm không hợp lệ hoặc trùng mã: " + g.getGrantId());
            }
        }
    }
    private static String enc(String s) {
        return Base64.getEncoder().encodeToString((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
    }
    private static String dec(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }
    private static boolean parseBoolean(String s) {
        if (!s.equals("true") && !s.equals("false")) throw new IllegalArgumentException("boolean invalid");
        return Boolean.parseBoolean(s);
    }
    private void persist(List<AccessRule> rs, List<TemporaryGrant> gs) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (AccessRule r : rs) {
            lines.add(String.join("\t", "R", enc(r.getRuleId()), enc(r.getAreaId()), enc(r.getDepartment()),
                    enc(r.getPosition()), r.getMatchMode().name(), Boolean.toString(r.isEnabled())));
        }
        for (TemporaryGrant g : gs) {
            lines.add(String.join("\t", "G", enc(g.getGrantId()), enc(g.getEmployeeId()), enc(g.getAreaId()),
                    g.getStartTime().toString(), g.getEndTime().toString(), Boolean.toString(g.isRevoked()), enc(g.getReason())));
        }
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, "access-policy-", ".tmp");
        try {
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
    private void load() throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<AccessRule> rs = new ArrayList<>();
        List<TemporaryGrant> gs = new ArrayList<>();
        try {
            if (lines.isEmpty() || !HEADER.equals(lines.get(0))) throw new IllegalArgumentException("header invalid");
            for (int i = 1; i < lines.size(); i++) {
                String[] p = lines.get(i).split("\t", -1);
                if (p.length == 7 && p[0].equals("R")) {
                    rs.add(new AccessRule(dec(p[1]), dec(p[2]), dec(p[3]), dec(p[4]),
                            AccessRule.MatchMode.valueOf(p[5]), parseBoolean(p[6])));
                } else if (p.length == 8 && p[0].equals("G")) {
                    gs.add(new TemporaryGrant(dec(p[1]), dec(p[2]), dec(p[3]), LocalDateTime.parse(p[4]),
                            LocalDateTime.parse(p[5]), parseBoolean(p[6]), dec(p[7])));
                } else {
                    throw new IllegalArgumentException("row " + (i + 1) + " invalid");
                }
            }
            validateAll(rs, gs);
        } catch (RuntimeException e) {
            throw new IOException("Không đọc được file quyền (không ghi đè): " + file, e);
        }
        rules = rs;
        grants = gs;
    }
}
