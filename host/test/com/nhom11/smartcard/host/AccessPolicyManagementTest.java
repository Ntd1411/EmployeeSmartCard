package com.nhom11.smartcard.host;

import com.nhom11.smartcard.host.model.*;
import com.nhom11.smartcard.host.provider.*;
import com.nhom11.smartcard.host.repository.FileAccessLogRepository;
import com.nhom11.smartcard.host.service.AccessControlService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/** Run without a simulator: java -cp <bin> com.nhom11.smartcard.host.AccessPolicyManagementTest */
public final class AccessPolicyManagementTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void deny(AccessControlService svc, String card, LocalDateTime time, AccessReasonCode reason) {
        AccessAttemptLog log = svc.evaluateAccess(card, "A", time, true);
        check(log.getDecision() == AccessDecision.DENY && log.getReasonCode() == reason, "deny " + reason + ": " + log.getReasonCode());
    }
    private static void allow(AccessControlService svc, String card, LocalDateTime time, AccessReasonCode reason) {
        AccessAttemptLog log = svc.evaluateAccess(card, "A", time, true);
        check(log.getDecision() == AccessDecision.ALLOW && log.getReasonCode() == reason, "allow " + reason + ": " + log.getReasonCode());
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("policy-test-");
        Path file = dir.resolve("policies.tsv");
        InMemoryEmployeeProvider employees = new InMemoryEmployeeProvider();
        employees.put(new Employee("E1", "An", "Kỹ thuật", "Developer", true));
        employees.put(new Employee("E2", "Nhi", "Khác", "Developer", true));
        InMemoryVirtualCardProvider cards = new InMemoryVirtualCardProvider();
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 12, 0);
        cards.put(new VirtualCard("C1", "E1", CardStatus.ACTIVE, true, true, now));
        cards.put(new VirtualCard("C2", "E2", CardStatus.ACTIVE, true, true, now));
        cards.put(new VirtualCard("LOCK", "E1", CardStatus.LOCKED, true, true, now));
        InMemoryAccessPolicyProvider seed = new InMemoryAccessPolicyProvider();
        seed.putArea(new Area("A", 1, "Area A", 1));
        FileAccessPolicyProvider policies = new FileAccessPolicyProvider(file, employees, seed);
        javax.swing.SwingUtilities.invokeAndWait(() -> new com.nhom11.smartcard.host.ui.AccessPolicyPanel(policies, employees, () -> {}));
        check(true, "policy panel constructs in headless mode");
        AccessControlService svc = new AccessControlService(employees, cards, policies,
                new FileAccessLogRepository(), new VirtualStoreCardGateway(cards));
        deny(svc, "C1", now, AccessReasonCode.NO_PERMISSION);
        policies.saveRule(new AccessRule("R1", "A", "Kỹ thuật", "Developer", AccessRule.MatchMode.DEPARTMENT_AND_POSITION, true));
        allow(svc, "C1", now, AccessReasonCode.PERMANENT_ROLE_MATCH);
        deny(svc, "C2", now, AccessReasonCode.NO_PERMISSION);
        deny(svc, "LOCK", now, AccessReasonCode.CARD_LOCKED);
        policies.saveRule(new AccessRule("R1", "A", "Kỹ thuật", "Developer", AccessRule.MatchMode.DEPARTMENT_OR_POSITION, true));
        check(policies.listRules().size() == 1, "edit must replace, not duplicate");
        allow(svc, "C2", now, AccessReasonCode.PERMANENT_ROLE_MATCH);
        policies.saveRule(new AccessRule("R1", "A", "Kỹ thuật", "Developer", AccessRule.MatchMode.DEPARTMENT_OR_POSITION, false));
        deny(svc, "C1", now, AccessReasonCode.NO_PERMISSION);
        try {
            policies.saveRule(new AccessRule("BAD", "A", " ", null, AccessRule.MatchMode.DEPARTMENT_OR_POSITION, true));
            throw new AssertionError("empty criteria accepted");
        } catch (IllegalArgumentException expected) { check(true, "blank rule rejected"); }
        LocalDateTime from = now.plusHours(1), to = now.plusHours(2);
        TemporaryGrant grant = new TemporaryGrant("G1", "E1", "A", from, to, false, "Lý do\t\nđặc biệt");
        policies.saveGrant(grant);
        deny(svc, "C1", from.minusSeconds(1), AccessReasonCode.TEMP_ACCESS_NOT_YET_ACTIVE);
        allow(svc, "C1", from, AccessReasonCode.TEMPORARY_GRANT_VALID);
        allow(svc, "C1", to, AccessReasonCode.TEMPORARY_GRANT_VALID);
        deny(svc, "C1", to.plusSeconds(1), AccessReasonCode.TEMP_ACCESS_EXPIRED);
        deny(svc, "LOCK", from, AccessReasonCode.CARD_LOCKED);
        FileAccessPolicyProvider reloaded = new FileAccessPolicyProvider(file, employees, seed);
        check(reloaded.listRules().size() == 1 && !reloaded.listRules().get(0).isEnabled(), "rule persisted");
        check(reloaded.listGrants().size() == 1 && reloaded.listGrants().get(0).getReason().equals(grant.getReason()), "grant and UTF-8 persisted");
        AccessControlService reloadedSvc = new AccessControlService(employees, cards, reloaded,
                new FileAccessLogRepository(), new VirtualStoreCardGateway(cards));
        allow(reloadedSvc, "C1", from, AccessReasonCode.TEMPORARY_GRANT_VALID);
        reloaded.saveGrant(new TemporaryGrant("G1", "E1", "A", from, to.plusHours(1), false, "đã sửa"));
        check(reloaded.listGrants().size() == 1, "editing grant replaces it");
        FileAccessPolicyProvider editedReload = new FileAccessPolicyProvider(file, employees, seed);
        check(editedReload.listGrants().get(0).getEndTime().equals(to.plusHours(1)), "edited grant persisted");
        reloaded.saveGrant(new TemporaryGrant("G1", "E1", "A", from, to, true, "revoked"));
        deny(reloadedSvc, "C1", from, AccessReasonCode.NO_PERMISSION);
        try { reloaded.saveGrant(grant); throw new AssertionError("reactivated revoked grant"); }
        catch (IllegalArgumentException expected) { check(true, "revocation irreversible"); }
        try { reloaded.saveGrant(new TemporaryGrant("G2", "MISSING", "A", from, to, false, "")); throw new AssertionError("missing employee accepted"); }
        catch (IllegalArgumentException expected) { check(true, "missing employee rejected"); }
        try { reloaded.saveRule(new AccessRule("R2", "UNKNOWN", "Kỹ thuật", null, AccessRule.MatchMode.DEPARTMENT_OR_POSITION, true)); throw new AssertionError("missing area accepted"); }
        catch (IllegalArgumentException expected) { check(true, "missing area rejected"); }
        try { new TemporaryGrant("G3", "E1", "A", to, from, false, ""); throw new AssertionError("inverted interval accepted"); }
        catch (IllegalArgumentException expected) { check(true, "invalid interval rejected"); }
        reloaded.saveRule(new AccessRule("R1", "A", "Kỹ thuật", null, AccessRule.MatchMode.DEPARTMENT_OR_POSITION, true));
        allow(reloadedSvc, "C1", to.plusSeconds(1), AccessReasonCode.PERMANENT_ROLE_MATCH);
        byte[] broken = "bad policy file\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file, broken);
        try { new FileAccessPolicyProvider(file, employees, seed); throw new AssertionError("corrupt file accepted"); }
        catch (IOException expected) { check(java.util.Arrays.equals(broken, Files.readAllBytes(file)), "corrupt file not overwritten"); }
        // Use a directory as the target to force a write failure: active in-memory snapshot must remain unchanged.
        Path badPath = dir.resolve("bad-target");
        FileAccessPolicyProvider failing = new FileAccessPolicyProvider(badPath, employees, seed);
        Files.delete(badPath); Files.createDirectory(badPath); Files.writeString(badPath.resolve("child"), "x");
        try { failing.saveRule(new AccessRule("R2", "A", "Kỹ thuật", null, AccessRule.MatchMode.DEPARTMENT_OR_POSITION, true)); throw new AssertionError("write should fail"); }
        catch (IOException expected) { check(failing.listRules().isEmpty(), "failed write does not mutate live policy"); }
        System.out.println("AccessPolicyManagementTest: " + checks + " checks passed.");
    }
}
