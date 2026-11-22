package com.nhom11.smartcard.host.provider;

import com.nhom11.smartcard.host.model.AccessRule;
import com.nhom11.smartcard.host.model.Area;
import com.nhom11.smartcard.host.model.TemporaryGrant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of {@link AccessPolicyProvider} for standalone development and testing.
 */
public class InMemoryAccessPolicyProvider implements AccessPolicyProvider {

    private final Map<String, Area> areasById = new LinkedHashMap<>();
    private final List<AccessRule> rules = new ArrayList<>();
    private final List<TemporaryGrant> grants = new ArrayList<>();

    public void putArea(Area area) {
        areasById.put(area.getAreaId(), area);
    }

    public void addRule(AccessRule rule) {
        rules.add(rule);
    }

    public void addGrant(TemporaryGrant grant) {
        grants.add(grant);
    }

    public void clear() {
        areasById.clear();
        rules.clear();
        grants.clear();
    }

    @Override
    public Optional<Area> findAreaById(String areaId) {
        if (areaId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(areasById.get(areaId));
    }

    @Override
    public List<Area> findAllAreas() {
        return Collections.unmodifiableList(new ArrayList<>(areasById.values()));
    }

    @Override
    public List<AccessRule> findPermanentRules(String areaId) {
        if (areaId == null) {
            return Collections.emptyList();
        }
        List<AccessRule> matched = new ArrayList<>();
        for (AccessRule rule : rules) {
            if (areaId.equals(rule.getAreaId())) {
                matched.add(rule);
            }
        }
        return matched;
    }

    @Override
    public List<TemporaryGrant> findTemporaryGrants(String employeeId, String areaId) {
        if (employeeId == null || areaId == null) {
            return Collections.emptyList();
        }
        List<TemporaryGrant> matched = new ArrayList<>();
        for (TemporaryGrant grant : grants) {
            if (employeeId.equals(grant.getEmployeeId()) && areaId.equals(grant.getAreaId())) {
                matched.add(grant);
            }
        }
        return matched;
    }
}
