package com.nhom11.smartcard.host.provider;

import com.nhom11.smartcard.host.model.VirtualCard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of {@link VirtualCardProvider} for standalone development and testing.
 */
public class InMemoryVirtualCardProvider implements VirtualCardProvider {

    private final Map<String, VirtualCard> cardsById = new LinkedHashMap<>();

    public void put(VirtualCard card) {
        cardsById.put(card.getCardId(), card);
    }

    public void clear() {
        cardsById.clear();
    }

    @Override
    public Optional<VirtualCard> findByCardId(String cardId) {
        if (cardId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cardsById.get(cardId));
    }

    @Override
    public Optional<VirtualCard> findByEmployeeId(String employeeId) {
        if (employeeId == null) {
            return Optional.empty();
        }
        for (VirtualCard card : cardsById.values()) {
            if (employeeId.equals(card.getEmployeeId())) {
                return Optional.of(card);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<VirtualCard> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(cardsById.values()));
    }

    @Override
    public boolean isPinVerified(String cardId) {
        return findByCardId(cardId).map(VirtualCard::isPinVerified).orElse(false);
    }

    @Override
    public boolean isCryptoVerified(String cardId) {
        return findByCardId(cardId).map(VirtualCard::isCryptoVerified).orElse(false);
    }
}
