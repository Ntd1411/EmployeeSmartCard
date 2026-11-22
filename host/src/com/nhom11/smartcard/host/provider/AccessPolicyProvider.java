package com.nhom11.smartcard.host.provider;

import com.nhom11.smartcard.host.model.AccessRule;
import com.nhom11.smartcard.host.model.Area;
import com.nhom11.smartcard.host.model.TemporaryGrant;
import java.util.List;
import java.util.Optional;

/**
 * Contract for reading areas, permanent access rules, and temporary grants (owned by US01-US02).
 */
public interface AccessPolicyProvider {

    Optional<Area> findAreaById(String areaId);

    List<Area> findAllAreas();

    List<AccessRule> findPermanentRules(String areaId);

    List<TemporaryGrant> findTemporaryGrants(String employeeId, String areaId);
}
