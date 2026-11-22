package com.nhom11.smartcard.host.provider;

import com.nhom11.smartcard.host.model.VirtualCard;
import java.util.List;
import java.util.Optional;

/**
 * Contract for reading virtual card state and verification flags (owned by US03 and US07-US09).
 */
public interface VirtualCardProvider {

    Optional<VirtualCard> findByCardId(String cardId);

    Optional<VirtualCard> findByEmployeeId(String employeeId);

    List<VirtualCard> findAll();

    boolean isPinVerified(String cardId);

    boolean isCryptoVerified(String cardId);
}
