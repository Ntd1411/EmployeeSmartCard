package com.nhom11.smartcard.host.service;

import com.nhom11.smartcard.host.model.CardStatus;
import com.nhom11.smartcard.host.model.VirtualCard;
import com.nhom11.smartcard.host.provider.VirtualCardProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Host-side lifecycle store. It is not a card-side security boundary. */
public final class CardLifecycleService {
    private static final String HEADER = "EMPLOYEE_CARD_LIFECYCLE_V1";
    private final Path file;
    private final VirtualCardProvider cards;
    private final Runnable requireAdmin;
    private Map<String, CardStatus> saved;

    /** A damaged file fails closed and is never silently overwritten. */
    public CardLifecycleService(Path file, VirtualCardProvider cards, Runnable requireAdmin) throws IOException {
        this.file = Objects.requireNonNull(file).toAbsolutePath();
        this.cards = Objects.requireNonNull(cards);
        this.requireAdmin = Objects.requireNonNull(requireAdmin);
        this.saved = Files.exists(this.file) ? read() : new LinkedHashMap<>();
        applySavedStates();
    }

    /** Reapply durable status after a demo catalog is reloaded. */
    public synchronized void applySavedStates() {
        for (VirtualCard card : cards.findAll()) {
            CardStatus status = saved.get(card.getCardId());
            if (status != null) {
                card.setStatus(status);
                if (status != CardStatus.ACTIVE) card.setPinVerified(false);
            }
        }
    }

    /** Persist before modifying the live card; authorization is checked in the service. */
    public synchronized void changeStatus(String cardId, CardStatus target) throws IOException {
        requireAdmin.run();
        Objects.requireNonNull(target, "status");
        VirtualCard card = cards.findByCardId(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thẻ."));
        CardStatus current = card.getStatus();
        if (current == CardStatus.REVOKED && target != CardStatus.REVOKED) {
            throw new IllegalArgumentException("Thẻ đã thu hồi không thể mở lại.");
        }
        if (current == target) return;
        Map<String, CardStatus> next = new LinkedHashMap<>(saved);
        next.put(card.getCardId(), target);
        persist(next);
        saved = next;
        card.setStatus(target);
        if (target != CardStatus.ACTIVE) card.setPinVerified(false);
    }

    private Map<String, CardStatus> read() throws IOException {
        Map<String, CardStatus> result = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !HEADER.equals(lines.get(0))) throw new IllegalArgumentException("header");
            for (int i = 1; i < lines.size(); i++) {
                String[] parts = lines.get(i).split("\\t", -1);
                if (parts.length != 2) throw new IllegalArgumentException("row");
                String id = new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                CardStatus state = CardStatus.valueOf(parts[1]);
                if (id.isBlank() || result.putIfAbsent(id, state) != null) throw new IllegalArgumentException("id");
            }
        } catch (RuntimeException ex) {
            throw new IOException("Không đọc được file trạng thái thẻ; file không bị ghi đè: " + file, ex);
        }
        return result;
    }

    private void persist(Map<String, CardStatus> states) throws IOException {
        List<String> lines = new java.util.ArrayList<>();
        lines.add(HEADER);
        for (Map.Entry<String, CardStatus> entry : states.entrySet()) {
            String id = Base64.getEncoder().encodeToString(entry.getKey().getBytes(StandardCharsets.UTF_8));
            lines.add(id + "\t" + entry.getValue().name());
        }
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, "card-lifecycle-", ".tmp");
        try {
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
