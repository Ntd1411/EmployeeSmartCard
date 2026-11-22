package com.nhom11.smartcard.host.service;

import com.nhom11.smartcard.host.model.AccessAttemptLog;
import com.nhom11.smartcard.host.model.AccessDecision;
import com.nhom11.smartcard.host.model.AccessReasonCode;
import com.nhom11.smartcard.host.model.AccessRule;
import com.nhom11.smartcard.host.model.Area;
import com.nhom11.smartcard.host.model.CardStatus;
import com.nhom11.smartcard.host.model.Employee;
import com.nhom11.smartcard.host.model.TemporaryGrant;
import com.nhom11.smartcard.host.model.VirtualCard;
import com.nhom11.smartcard.host.provider.AccessPolicyProvider;
import com.nhom11.smartcard.host.provider.CardGateway;
import com.nhom11.smartcard.host.provider.EmployeeProvider;
import com.nhom11.smartcard.host.provider.VirtualCardProvider;
import com.nhom11.smartcard.host.repository.AccessLogRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service implementing US12: deterministic 7-step area access simulation and attempt logging.
 */
public class AccessControlService {

    private final EmployeeProvider employeeProvider;
    private final VirtualCardProvider cardProvider;
    private final AccessPolicyProvider policyProvider;
    private final AccessLogRepository accessLogRepository;
    private final CardGateway cardGateway;

    public AccessControlService(
            EmployeeProvider employeeProvider,
            VirtualCardProvider cardProvider,
            AccessPolicyProvider policyProvider,
            AccessLogRepository accessLogRepository,
            CardGateway cardGateway) {
        this.employeeProvider = Objects.requireNonNull(employeeProvider, "employeeProvider");
        this.cardProvider = Objects.requireNonNull(cardProvider, "cardProvider");
        this.policyProvider = Objects.requireNonNull(policyProvider, "policyProvider");
        this.accessLogRepository = Objects.requireNonNull(accessLogRepository, "accessLogRepository");
        this.cardGateway = Objects.requireNonNull(cardGateway, "cardGateway");
    }

    /**
     * Evaluates access for a card, area, and attempt timestamp and persists an {@link AccessAttemptLog}.
     *
     * @param cardId virtual card ID
     * @param areaId target area ID
     * @param attemptTime simulated attempt timestamp (defaults to now when null)
     * @param requireSecurityCheck whether PIN and simulated AES/RSA flags must be verified
     * @return recorded access attempt log
     */
    public AccessAttemptLog evaluateAccess(
            String cardId,
            String areaId,
            LocalDateTime attemptTime,
            boolean requireSecurityCheck) {
        String normalizedCardId = cardId == null ? "" : cardId.trim();
        String normalizedAreaId = areaId == null ? "" : areaId.trim();
        LocalDateTime effectiveTime = attemptTime == null
                ? LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
                : attemptTime;

        // Step 1: Card existence
        Optional<VirtualCard> cardOpt = cardProvider.findByCardId(normalizedCardId);
        if (cardOpt.isEmpty()) {
            return recordLog(normalizedCardId, null, normalizedAreaId, effectiveTime,
                    AccessDecision.DENY, AccessReasonCode.CARD_NOT_FOUND,
                    "Không tìm thấy thẻ ảo trong hệ thống.", null);
        }
        VirtualCard card = cardOpt.get();
        String empId = card.getEmployeeId();

        // Step 2: Card lifecycle status (takes absolute precedence over permissions)
        if (card.getStatus() == CardStatus.LOCKED) {
            return recordLog(normalizedCardId, empId, normalizedAreaId, effectiveTime,
                    AccessDecision.DENY, AccessReasonCode.CARD_LOCKED,
                    "Thẻ đang bị khóa (LOCKED), từ chối ra vào khu vực.", null);
        }
        if (card.getStatus() == CardStatus.REVOKED) {
            return recordLog(normalizedCardId, empId, normalizedAreaId, effectiveTime,
                    AccessDecision.DENY, AccessReasonCode.CARD_REVOKED,
                    "Thẻ đã bị thu hồi (REVOKED), từ chối ra vào khu vực.", null);
        }

        // Step 3: PIN and simulated cryptographic checks
        if (requireSecurityCheck) {
            if (!card.isPinVerified()) {
                return recordLog(normalizedCardId, empId, normalizedAreaId, effectiveTime,
                        AccessDecision.DENY, AccessReasonCode.PIN_NOT_VERIFIED,
                        "Thẻ chưa xác thực mã PIN.", null);
            }
            if (!card.isCryptoVerified()) {
                return recordLog(normalizedCardId, empId, normalizedAreaId, effectiveTime,
                        AccessDecision.DENY, AccessReasonCode.CRYPTO_INVALID,
                        "Dữ liệu thẻ không vượt qua xác minh AES/RSA mô phỏng.", null);
            }
        }

        // Step 4: Employee and Area existence
        Optional<Employee> employeeOpt = employeeProvider.findById(empId);
        if (employeeOpt.isEmpty() || !employeeOpt.get().isActive()) {
            return recordLog(normalizedCardId, empId, normalizedAreaId, effectiveTime,
                    AccessDecision.DENY, AccessReasonCode.EMPLOYEE_NOT_FOUND,
                    "Hồ sơ nhân viên không tồn tại hoặc đã ngừng làm việc.", null);
        }
        Employee employee = employeeOpt.get();

        Optional<Area> areaOpt = policyProvider.findAreaById(normalizedAreaId);
        if (areaOpt.isEmpty()) {
            return recordLog(normalizedCardId, empId, normalizedAreaId, effectiveTime,
                    AccessDecision.DENY, AccessReasonCode.AREA_NOT_FOUND,
                    "Khu vực không tồn tại trong hệ thống.", null);
        }
        Area area = areaOpt.get();

        // Cross-check card-side gateway eligibility
        CardGateway.CardGatewayResult gwResult = cardGateway.checkAreaAccess(
                normalizedCardId, area.getAreaCode(), effectiveTime, requireSecurityCheck);
        if (!gwResult.isSuccess() || !gwResult.isCardAllowed()) {
            AccessReasonCode mappedReason;
            if (gwResult.getStatusWord() == CardGateway.SW_SECURITY_STATUS_NOT_SATISFIED) {
                mappedReason = AccessReasonCode.PIN_NOT_VERIFIED;
            } else {
                mappedReason = AccessReasonCode.CARD_LOCKED;
            }
            return recordLog(normalizedCardId, empId, normalizedAreaId, effectiveTime,
                    AccessDecision.DENY, mappedReason,
                    "Kiểm tra phía thẻ từ chối: " + gwResult.getMessage(), null);
        }

        // Step 5: Permanent department/position access rules (US01)
        List<AccessRule> permanentRules = policyProvider.findPermanentRules(area.getAreaId());
        for (AccessRule rule : permanentRules) {
            if (rule.matches(employee)) {
                return recordLog(normalizedCardId, empId, area.getAreaId(), effectiveTime,
                        AccessDecision.ALLOW, AccessReasonCode.PERMANENT_ROLE_MATCH,
                        "Cho phép ra vào " + area.getAreaName() + " theo quy tắc cố định (" + rule.getRuleId() + ").",
                        rule.getRuleId());
            }
        }

        // Step 6: Temporary grants (US02)
        List<TemporaryGrant> grants = policyProvider.findTemporaryGrants(empId, area.getAreaId());
        boolean hasExpiredGrant = false;
        boolean hasFutureGrant = false;

        for (TemporaryGrant grant : grants) {
            if (grant.isActiveAt(effectiveTime)) {
                return recordLog(normalizedCardId, empId, area.getAreaId(), effectiveTime,
                        AccessDecision.ALLOW, AccessReasonCode.TEMPORARY_GRANT_VALID,
                        "Cho phép ra vào " + area.getAreaName() + " theo quyền tạm thời (" + grant.getGrantId() + ").",
                        grant.getGrantId());
            }
            if (!grant.isRevoked()) {
                if (effectiveTime.isAfter(grant.getEndTime())) {
                    hasExpiredGrant = true;
                } else if (effectiveTime.isBefore(grant.getStartTime())) {
                    hasFutureGrant = true;
                }
            }
        }

        // Step 7: Deny with informative reason
        if (hasFutureGrant) {
            return recordLog(normalizedCardId, empId, area.getAreaId(), effectiveTime,
                    AccessDecision.DENY, AccessReasonCode.TEMP_ACCESS_NOT_YET_ACTIVE,
                    "Quyền truy cập tạm thời vào " + area.getAreaName() + " chưa tới thời gian hiệu lực.", null);
        }
        if (hasExpiredGrant) {
            return recordLog(normalizedCardId, empId, area.getAreaId(), effectiveTime,
                    AccessDecision.DENY, AccessReasonCode.TEMP_ACCESS_EXPIRED,
                    "Quyền truy cập tạm thời vào " + area.getAreaName() + " đã hết hạn.", null);
        }

        return recordLog(normalizedCardId, empId, area.getAreaId(), effectiveTime,
                AccessDecision.DENY, AccessReasonCode.NO_PERMISSION,
                "Nhân viên không có quyền truy cập vào " + area.getAreaName() + ".", null);
    }

    public List<AccessAttemptLog> listAllLogs() {
        return accessLogRepository.findAll();
    }

    private AccessAttemptLog recordLog(
            String cardId,
            String employeeId,
            String areaId,
            LocalDateTime attemptTime,
            AccessDecision decision,
            AccessReasonCode reasonCode,
            String reasonDetail,
            String matchedRuleId) {
        String logId = "LOG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return accessLogRepository.save(new AccessAttemptLog(
                logId,
                cardId,
                employeeId,
                areaId,
                attemptTime,
                decision,
                reasonCode,
                reasonDetail,
                matchedRuleId));
    }
}
