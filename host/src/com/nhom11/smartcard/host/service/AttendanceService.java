package com.nhom11.smartcard.host.service;

import com.nhom11.smartcard.host.model.AttendanceEventType;
import com.nhom11.smartcard.host.model.AttendanceRecord;
import com.nhom11.smartcard.host.model.CardStatus;
import com.nhom11.smartcard.host.model.Employee;
import com.nhom11.smartcard.host.model.VirtualCard;
import com.nhom11.smartcard.host.provider.CardGateway;
import com.nhom11.smartcard.host.provider.EmployeeProvider;
import com.nhom11.smartcard.host.provider.VirtualCardProvider;
import com.nhom11.smartcard.host.repository.AttendanceRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service implementing US10: shift check-in and check-out validation and persistence.
 */
public class AttendanceService {

    private static final long DEBOUNCE_SECONDS = 60L;
    private static final int DEFAULT_ATTENDANCE_TERMINAL_AREA = 1;
    private static final DateTimeFormatter ID_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicLong RECORD_SEQ = new AtomicLong(0L);

    private final EmployeeProvider employeeProvider;
    private final VirtualCardProvider cardProvider;
    private final AttendanceRepository attendanceRepository;
    private final CardGateway cardGateway;

    public AttendanceService(
            EmployeeProvider employeeProvider,
            VirtualCardProvider cardProvider,
            AttendanceRepository attendanceRepository,
            CardGateway cardGateway) {
        this.employeeProvider = Objects.requireNonNull(employeeProvider, "employeeProvider");
        this.cardProvider = Objects.requireNonNull(cardProvider, "cardProvider");
        this.attendanceRepository = Objects.requireNonNull(attendanceRepository, "attendanceRepository");
        this.cardGateway = Objects.requireNonNull(cardGateway, "cardGateway");
    }

    /**
     * Validates and records a check-in or check-out punch for a virtual card.
     *
     * @param cardId virtual card ID
     * @param eventType CHECK_IN or CHECK_OUT
     * @param timestamp punch timestamp (defaults to now when null)
     * @param requireSecurityCheck whether PIN and simulated AES/RSA must be verified
     * @param allowOrphanForTesting allows recording unpaired punches for US11 edge-case testing
     * @return punch processing result
     */
    public PunchResult punch(
            String cardId,
            AttendanceEventType eventType,
            LocalDateTime timestamp,
            boolean requireSecurityCheck,
            boolean allowOrphanForTesting) {
        if (cardId == null || cardId.isBlank()) {
            return PunchResult.rejected("Vui lòng nhập hoặc chọn mã thẻ ảo.", CardGateway.SW_WRONG_DATA);
        }
        if (eventType == null) {
            return PunchResult.rejected("Loại chấm công không hợp lệ.", CardGateway.SW_WRONG_DATA);
        }
        LocalDateTime effectiveTime = timestamp == null ? LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS) : timestamp;

        Optional<VirtualCard> cardOpt = cardProvider.findByCardId(cardId.trim());
        if (cardOpt.isEmpty()) {
            return PunchResult.rejected("Không tìm thấy thẻ ảo: " + cardId, CardGateway.SW_WRONG_DATA);
        }
        VirtualCard card = cardOpt.get();

        if (card.getStatus() == CardStatus.LOCKED) {
            return PunchResult.rejected("Thẻ đang bị khóa (LOCKED), không thể chấm công.", CardGateway.SW_CARD_LOCKED_OR_REVOKED);
        }
        if (card.getStatus() == CardStatus.REVOKED) {
            return PunchResult.rejected("Thẻ đã bị thu hồi (REVOKED), không thể chấm công.", CardGateway.SW_CARD_LOCKED_OR_REVOKED);
        }

        if (requireSecurityCheck) {
            if (!card.isPinVerified()) {
                return PunchResult.rejected("Thẻ chưa xác thực mã PIN.", CardGateway.SW_SECURITY_STATUS_NOT_SATISFIED);
            }
            if (!card.isCryptoVerified()) {
                return PunchResult.rejected("Xác thực mật mã thẻ không hợp lệ.", CardGateway.SW_SECURITY_STATUS_NOT_SATISFIED);
            }
        }

        Optional<Employee> employeeOpt = employeeProvider.findById(card.getEmployeeId());
        if (employeeOpt.isEmpty() || !employeeOpt.get().isActive()) {
            return PunchResult.rejected("Nhân viên gắn với thẻ không tồn tại hoặc đã nghỉ việc.", CardGateway.SW_CONDITIONS_NOT_SATISFIED);
        }
        Employee employee = employeeOpt.get();

        List<AttendanceRecord> employeeRecords = attendanceRepository.findByEmployee(employee.getEmployeeId());
        AttendanceRecord latestRecord = null;
        for (AttendanceRecord r : employeeRecords) {
            if (latestRecord == null || !r.getTimestamp().isBefore(latestRecord.getTimestamp())) {
                latestRecord = r;
            }
        }
        Optional<AttendanceRecord> latestOpt = Optional.ofNullable(latestRecord);

        if (latestOpt.isPresent()) {
            AttendanceRecord latest = latestOpt.get();
            long diffSec = Math.abs(ChronoUnit.SECONDS.between(latest.getTimestamp(), effectiveTime));
            if (latest.getCardId().equals(card.getCardId())
                    && latest.getEventType() == eventType
                    && diffSec < DEBOUNCE_SECONDS) {
                return PunchResult.rejected(
                        "Thao tác bấm đúp quá nhanh (< 60 giây) cho cùng loại " + eventType.name() + ".",
                        CardGateway.SW_CONDITIONS_NOT_SATISFIED);
            }
        }

        boolean allowRollover = allowOrphanForTesting;
        String note = "";
        if (!allowOrphanForTesting) {
            if (eventType == AttendanceEventType.CHECK_IN && latestOpt.isPresent()
                    && latestOpt.get().getEventType() == AttendanceEventType.CHECK_IN) {
                AttendanceRecord latest = latestOpt.get();
                if (!latest.getTimestamp().toLocalDate().isBefore(effectiveTime.toLocalDate())) {
                    return PunchResult.rejected(
                            "Nhân viên đang có ca CHECK_IN chưa ra ca trong ngày.",
                            CardGateway.SW_CONDITIONS_NOT_SATISFIED);
                }
                allowRollover = true;
                note = "Mở ca mới qua ngày (ca trước thiếu giờ ra)";
            } else if (eventType == AttendanceEventType.CHECK_OUT
                    && (latestOpt.isEmpty() || latestOpt.get().getEventType() != AttendanceEventType.CHECK_IN)) {
                return PunchResult.rejected(
                        "Chưa có lượt vào ca (CHECK_IN) đang mở để thực hiện CHECK_OUT.",
                        CardGateway.SW_CONDITIONS_NOT_SATISFIED);
            }
        } else {
            note = "Ghi nhận chế độ kiểm thử ca thiếu dữ liệu";
        }

        CardGateway.CardGatewayResult gwResult = cardGateway.recordAttendancePunch(
                card.getCardId(),
                eventType,
                effectiveTime,
                DEFAULT_ATTENDANCE_TERMINAL_AREA,
                allowRollover,
                requireSecurityCheck);
        if (!gwResult.isSuccess()) {
            return PunchResult.rejected("Từ chối từ phía thẻ: " + gwResult.getMessage(), gwResult.getStatusWord());
        }

        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        long seq = RECORD_SEQ.incrementAndGet();
        String recordId = "ATT-" + effectiveTime.format(ID_TIME_FMT) + "-" + String.format("%04d", seq) + "-" + suffix;
        AttendanceRecord saved = attendanceRepository.save(new AttendanceRecord(
                recordId,
                employee.getEmployeeId(),
                card.getCardId(),
                eventType,
                effectiveTime,
                "HOST_UI",
                note));

        String actionLabel = eventType == AttendanceEventType.CHECK_IN ? "Vào ca (CHECK_IN)" : "Ra ca (CHECK_OUT)";
        return PunchResult.accepted(
                saved,
                actionLabel + " thành công cho " + employee.getFullName() + " lúc " + effectiveTime,
                gwResult.getStatusWord());
    }

    public List<AttendanceRecord> listAllRecords() {
        return attendanceRepository.findAll();
    }
}
