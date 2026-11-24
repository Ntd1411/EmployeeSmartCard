package com.nhom11.smartcard.host;

import com.nhom11.smartcard.host.model.CardStatus;
import com.nhom11.smartcard.host.model.VirtualCard;
import com.nhom11.smartcard.host.provider.InMemoryVirtualCardProvider;
import com.nhom11.smartcard.host.service.CardLifecycleService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

public final class CardLifecycleServiceTest {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private static VirtualCard card(String id) {
        return new VirtualCard(id, "E1", CardStatus.ACTIVE, true, true, LocalDateTime.now());
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("card-lifecycle-test");
        Path file = dir.resolve("states.tsv");
        InMemoryVirtualCardProvider cards = new InMemoryVirtualCardProvider();
        VirtualCard one = card("C1"), two = card("C2");
        cards.put(one); cards.put(two);
        boolean[] admin = {false};
        Runnable guard = () -> { if (!admin[0]) throw new SecurityException("admin required"); };
        CardLifecycleService service = new CardLifecycleService(file, cards, guard);
        try { service.changeStatus("C1", CardStatus.LOCKED); throw new AssertionError("guard missing"); }
        catch (SecurityException expected) { check(one.getStatus() == CardStatus.ACTIVE && Files.notExists(file), "unauthorized change"); }
        admin[0] = true;
        service.changeStatus("C1", CardStatus.LOCKED);
        check(one.getStatus() == CardStatus.LOCKED && !one.isPinVerified(), "lock resets PIN");
        check(two.getStatus() == CardStatus.ACTIVE, "other card unchanged");
        one.setStatus(CardStatus.ACTIVE);
        new CardLifecycleService(file, cards, guard);
        check(one.getStatus() == CardStatus.LOCKED, "reload persisted lock");
        service.changeStatus("C1", CardStatus.ACTIVE);
        check(one.getStatus() == CardStatus.ACTIVE && !one.isPinVerified(), "unlock requires PIN again");
        service.changeStatus("C1", CardStatus.REVOKED);
        try { service.changeStatus("C1", CardStatus.ACTIVE); throw new AssertionError("revocation reversed"); }
        catch (IllegalArgumentException expected) { check(one.getStatus() == CardStatus.REVOKED, "irreversible revoke"); }
        one.setStatus(CardStatus.ACTIVE);
        service.applySavedStates();
        check(one.getStatus() == CardStatus.REVOKED, "seed reload cannot undo revocation");
        try { service.changeStatus("missing", CardStatus.LOCKED); throw new AssertionError("unknown card"); }
        catch (IllegalArgumentException expected) { check(true, "unknown card rejected"); }
        Path invalid = dir.resolve("invalid.tsv"); Files.writeString(invalid, "bad");
        try { new CardLifecycleService(invalid, cards, guard); throw new AssertionError("invalid accepted"); }
        catch (java.io.IOException expected) { check(Files.readString(invalid).equals("bad"), "bad file not overwritten"); }
        Path badTarget = dir.resolve("blocked"); Files.writeString(badTarget, "x");
        CardLifecycleService failing = new CardLifecycleService(badTarget.resolve("state"), cards, guard);
        try { failing.changeStatus("C2", CardStatus.LOCKED); throw new AssertionError("write succeeded"); }
        catch (java.io.IOException expected) { check(two.getStatus() == CardStatus.ACTIVE, "failed write leaves card unchanged"); }
        System.out.println("CardLifecycleServiceTest: " + checks + " checks passed.");
    }
}
