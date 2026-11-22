package com.nhom11.smartcard.host.provider;

import com.nhom11.smartcard.host.model.AttendanceEventType;
import com.nhom11.smartcard.host.model.CardStatus;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Provider;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;

/**
 * CardGateway implementation communicating over TCP socket with the Java Card Simulator (jcsl).
 */
public class ApduSimulatorCardGateway implements CardGateway, AutoCloseable {

    public static final byte[] APPLET_AID = {
        (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44, (byte) 0x55
    };

    public static final int CLA_PROPRIETARY = 0x80;
    public static final int INS_VERIFY_PIN = 0x20;
    public static final int INS_SET_CARD_STATUS = 0x30;
    public static final int INS_SET_AREA_MASK = 0x32;
    public static final int INS_GET_CARD_STATUS = 0x34;
    public static final int INS_ATTENDANCE_PUNCH = 0x40;
    public static final int INS_GET_LAST_ATTENDANCE = 0x42;
    public static final int INS_CHECK_AREA_ACCESS = 0x50;

    private static final String SOCKET_PROVIDER_CLASS = "com.oracle.smartcardio.socket.SocketCardTerminalProvider";
    private static final String SOCKET_FACTORY_TYPE = "SocketCardTerminalFactoryType";

    private final Path explicitSocketJar;
    private URLClassLoader ownedLoader;
    private Card card;
    private CardChannel channel;
    private String connectedEndpoint;
    private String lastApduSummary = "Chưa kết nối tới Simulator jcsl.";

    public ApduSimulatorCardGateway() {
        this(null);
    }

    public ApduSimulatorCardGateway(Path explicitSocketJar) {
        this.explicitSocketJar = explicitSocketJar;
    }

    /**
     * Connects to the simulator over TCP, powers up the card, and selects the employee card applet.
     *
     * @param host simulator hostname or IP address
     * @param port simulator port (default 9025)
     * @throws IllegalStateException if socketprovider.jar is missing, simulator refuses connection,
     *     or the applet AID 1122334455 is not installed
     */
    public synchronized void connect(String host, int port) {
        disconnect();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host simulator không được để trống.");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Cổng simulator phải nằm trong khoảng 1..65535.");
        }

        try {
            LoaderAndFactory loaded = openSocketTerminalFactory(host.trim(), port, explicitSocketJar);
            this.ownedLoader = loaded.ownedLoader;

            List<CardTerminal> terminals = loaded.factory.terminals().list();
            if (terminals.isEmpty()) {
                throw new IllegalStateException("Không tìm thấy SocketCardTerminal cho " + host + ":" + port);
            }
            CardTerminal terminal = terminals.get(0);
            if (!terminal.waitForCardPresent(5000L)) {
                throw new IllegalStateException("Không nhận được phản hồi từ simulator tại " + host + ":" + port);
            }

            Card connectedCard = terminal.connect("T=1");
            CardChannel basicChannel = connectedCard.getBasicChannel();

            CommandAPDU selectCmd = new CommandAPDU(0x00, 0xA4, 0x04, 0x00, APPLET_AID, 256);
            ResponseAPDU selectResp = basicChannel.transmit(selectCmd);
            lastApduSummary = formatApduSummary(0x00, 0xA4, 0x04, 0x00, APPLET_AID,
                    selectResp.getSW(), selectResp.getData());

            if (selectResp.getSW() != SW_NO_ERROR) {
                try {
                    connectedCard.disconnect(false);
                } catch (CardException ignored) {
                    // Best-effort cleanup on failed select.
                }
                if (selectResp.getSW() == 0x6A82) {
                    throw new IllegalStateException(
                            "Kết nối tới jcsl thành công nhưng Applet AID 1122334455 chưa được load/install trên Simulator.");
                }
                throw new IllegalStateException(
                        "Không thể select applet trên jcsl (SW=" + String.format("0x%04X", selectResp.getSW()) + ").");
            }

            this.card = connectedCard;
            this.channel = basicChannel;
            this.connectedEndpoint = host.trim() + ":" + port;
        } catch (Exception ex) {
            disconnect();
            if (ex instanceof IllegalStateException) {
                throw (IllegalStateException) ex;
            }
            throw new IllegalStateException("Không thể kết nối tới simulator " + host + ":" + port + " — " + ex.getMessage(), ex);
        }
    }

    public synchronized void disconnect() {
        if (card != null) {
            try {
                card.disconnect(false);
            } catch (CardException ignored) {
                // Ignore disconnect errors on shutdown.
            } finally {
                card = null;
                channel = null;
                connectedEndpoint = null;
            }
        }
        if (ownedLoader != null) {
            try {
                ownedLoader.close();
            } catch (IOException ignored) {
                // Ignore loader close failure.
            } finally {
                ownedLoader = null;
            }
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    public synchronized boolean isConnected() {
        return card != null && channel != null;
    }

    public synchronized String getConnectedEndpoint() {
        return connectedEndpoint;
    }

    public synchronized String getLastApduSummary() {
        return lastApduSummary;
    }

    /**
     * Verifies PIN against the applet on jcsl without logging plaintext PIN bytes.
     *
     * @param pinChars cardholder PIN characters
     * @return gateway result reflecting ISO7816 SW
     */
    public synchronized CardGatewayResult verifyPin(char[] pinChars) {
        if (!isConnected()) {
            return CardGatewayResult.failure(SW_CONDITIONS_NOT_SATISFIED, "Chưa kết nối tới Simulator jcsl.");
        }
        if (pinChars == null || pinChars.length == 0 || pinChars.length > 8) {
            return CardGatewayResult.failure(0x6700, "Độ dài PIN phải từ 1 đến 8 ký tự.");
        }

        byte[] pinBytes = new byte[pinChars.length];
        try {
            for (int i = 0; i < pinChars.length; i++) {
                pinBytes[i] = (byte) (pinChars[i] & 0x7F);
            }
            ResponseAPDU resp = transmit(CLA_PROPRIETARY, INS_VERIFY_PIN, 0x00, 0x00, pinBytes, 0);
            int sw = resp.getSW();
            if (sw == SW_NO_ERROR) {
                return CardGatewayResult.ok("Xác thực PIN trên jcsl thành công (SW=0x9000).");
            }
            return CardGatewayResult.failure(sw, describeStatusWord(sw));
        } catch (CardException ex) {
            return CardGatewayResult.failure(SW_CONDITIONS_NOT_SATISFIED, "Lỗi truyền APDU: " + ex.getMessage());
        } finally {
            Arrays.fill(pinBytes, (byte) 0);
        }
    }

    /**
     * Updates the card lifecycle status inside the running applet on jcsl.
     *
     * @param status target lifecycle status
     * @return gateway result
     */
    public synchronized CardGatewayResult setCardStatus(CardStatus status) {
        if (!isConnected()) {
            return CardGatewayResult.failure(SW_CONDITIONS_NOT_SATISFIED, "Chưa kết nối tới Simulator jcsl.");
        }
        int p1;
        switch (status) {
            case ACTIVE:
                p1 = 0x00;
                break;
            case LOCKED:
                p1 = 0x01;
                break;
            case REVOKED:
                p1 = 0x02;
                break;
            default:
                return CardGatewayResult.failure(SW_WRONG_DATA, "Trạng thái thẻ không hợp lệ.");
        }
        try {
            ResponseAPDU resp = transmit(CLA_PROPRIETARY, INS_SET_CARD_STATUS, p1, 0x00, null, 0);
            int sw = resp.getSW();
            if (sw == SW_NO_ERROR) {
                return CardGatewayResult.ok("Đã cập nhật trạng thái thẻ trên jcsl thành " + status + " (SW=0x9000).");
            }
            return CardGatewayResult.failure(sw, describeStatusWord(sw));
        } catch (CardException ex) {
            return CardGatewayResult.failure(SW_CONDITIONS_NOT_SATISFIED, "Lỗi truyền APDU: " + ex.getMessage());
        }
    }

    /**
     * Updates the 32-bit area bitmask stored inside the applet on jcsl.
     *
     * @param mask32 bitmask where bit (areaCode - 1) represents area permission
     * @return gateway result
     */
    public synchronized CardGatewayResult setAreaMask(int mask32) {
        if (!isConnected()) {
            return CardGatewayResult.failure(SW_CONDITIONS_NOT_SATISFIED, "Chưa kết nối tới Simulator jcsl.");
        }
        byte[] payload = new byte[] {
            (byte) (mask32 & 0xFF),
            (byte) ((mask32 >>> 8) & 0xFF),
            (byte) ((mask32 >>> 16) & 0xFF),
            (byte) ((mask32 >>> 24) & 0xFF)
        };
        try {
            ResponseAPDU resp = transmit(CLA_PROPRIETARY, INS_SET_AREA_MASK, 0x00, 0x00, payload, 0);
            int sw = resp.getSW();
            if (sw == SW_NO_ERROR) {
                return CardGatewayResult.ok("Đã cập nhật bitmask khu vực trên jcsl (SW=0x9000).");
            }
            return CardGatewayResult.failure(sw, describeStatusWord(sw));
        } catch (CardException ex) {
            return CardGatewayResult.failure(SW_CONDITIONS_NOT_SATISFIED, "Lỗi truyền APDU: " + ex.getMessage());
        }
    }

    /**
     * Reads chip lifecycle status, PIN validation flag, punch counter, and shift state (INS=0x34).
     *
     * @return decoded chip status
     * @throws IllegalStateException on communication error or non-9000 SW
     */
    public synchronized CardChipStatus readCardStatus() {
        ensureConnected();
        try {
            ResponseAPDU resp = transmit(CLA_PROPRIETARY, INS_GET_CARD_STATUS, 0x00, 0x00, null, 4);
            if (resp.getSW() != SW_NO_ERROR) {
                throw new IllegalStateException(describeStatusWord(resp.getSW()));
            }
            return decodeCardStatus(resp.getData());
        } catch (CardException ex) {
            throw new IllegalStateException("Lỗi đọc trạng thái thẻ trên jcsl: " + ex.getMessage(), ex);
        }
    }

    /**
     * Reads the most recent attendance event stored inside the applet's circular buffer (INS=0x42).
     *
     * @return decoded last attendance summary
     * @throws IllegalStateException on communication error or non-9000 SW
     */
    public synchronized LastCardAttendance readLastAttendance() {
        ensureConnected();
        try {
            ResponseAPDU resp = transmit(CLA_PROPRIETARY, INS_GET_LAST_ATTENDANCE, 0x00, 0x00, null, 7);
            if (resp.getSW() != SW_NO_ERROR) {
                throw new IllegalStateException(describeStatusWord(resp.getSW()));
            }
            return decodeLastAttendance(resp.getData());
        } catch (CardException ex) {
            throw new IllegalStateException("Lỗi đọc bản ghi cuối từ jcsl: " + ex.getMessage(), ex);
        }
    }

    @Override
    public synchronized CardGatewayResult recordAttendancePunch(
            String cardId,
            AttendanceEventType eventType,
            LocalDateTime timestamp,
            int areaCode,
            boolean allowRollover,
            boolean requireSecurityCheck) {
        if (!isConnected()) {
            return CardGatewayResult.failure(SW_CONDITIONS_NOT_SATISFIED, "Chưa kết nối tới Simulator jcsl.");
        }
        if (eventType == null || timestamp == null || areaCode < 1 || areaCode > 32) {
            return CardGatewayResult.failure(SW_WRONG_DATA, "Dữ liệu chấm công gửi xuống thẻ không hợp lệ.");
        }

        int p1 = eventType == AttendanceEventType.CHECK_IN ? 0x01 : 0x02;
        int p2 = allowRollover ? 0x01 : 0x00;
        byte[] payload = buildPunchPayload(timestamp, areaCode);

        try {
            ResponseAPDU resp = transmit(CLA_PROPRIETARY, INS_ATTENDANCE_PUNCH, p1, p2, payload, 2);
            int sw = resp.getSW();
            if (sw == SW_NO_ERROR) {
                byte[] out = resp.getData();
                String info = "Ghi nhận trên jcsl thành công";
                if (out.length >= 2) {
                    int counter = out[0] & 0xFF;
                    boolean shiftOpen = out[1] == 0x01;
                    info += " (lượt #" + counter + ", ca=" + (shiftOpen ? "MỞ" : "ĐÓNG") + ")";
                }
                return CardGatewayResult.ok(info);
            }
            return CardGatewayResult.failure(sw, describeStatusWord(sw));
        } catch (CardException ex) {
            return CardGatewayResult.failure(SW_CONDITIONS_NOT_SATISFIED, "Lỗi truyền APDU tới jcsl: " + ex.getMessage());
        }
    }

    @Override
    public synchronized CardGatewayResult checkAreaAccess(
            String cardId,
            int areaCode,
            LocalDateTime attemptTime,
            boolean requireSecurityCheck) {
        if (!isConnected()) {
            return CardGatewayResult.failure(SW_CONDITIONS_NOT_SATISFIED, "Chưa kết nối tới Simulator jcsl.");
        }
        if (attemptTime == null || areaCode < 1 || areaCode > 32) {
            return CardGatewayResult.failure(SW_WRONG_DATA, "Mã khu vực hoặc thời điểm không hợp lệ.");
        }

        byte[] payload = buildTimestampPayload(attemptTime);
        try {
            ResponseAPDU resp = transmit(CLA_PROPRIETARY, INS_CHECK_AREA_ACCESS, areaCode, 0x00, payload, 2);
            int sw = resp.getSW();
            if (sw == SW_NO_ERROR) {
                byte[] out = resp.getData();
                boolean chipBitAllowed = out.length >= 1 && out[0] == 0x01;
                return CardGatewayResult.allowed(
                        SW_NO_ERROR,
                        "jcsl xác nhận thẻ ACTIVE & PIN hợp lệ (bitmask thẻ: "
                                + (chipBitAllowed ? "ALLOW" : "DENY") + ")");
            }
            return CardGatewayResult.failure(sw, describeStatusWord(sw));
        } catch (CardException ex) {
            return CardGatewayResult.failure(SW_CONDITIONS_NOT_SATISFIED, "Lỗi truyền APDU tới jcsl: " + ex.getMessage());
        }
    }

    /**
     * Locates Oracle's socketprovider.jar from explicit hint, system property, environment variables,
     * or user home installation directory.
     *
     * @return resolved jar path when present
     */
    public static Optional<Path> detectSocketProviderJar() {
        return detectSocketProviderJar(null);
    }

    /**
     * Locates socketprovider.jar with an optional explicit path override.
     *
     * @param explicitJar optional jar path override
     * @return resolved jar path when present
     */
    public static Optional<Path> detectSocketProviderJar(Path explicitJar) {
        if (explicitJar != null && Files.isRegularFile(explicitJar)) {
            return Optional.of(explicitJar);
        }

        String[] candidates = {
            System.getProperty("jc.simulator.home"),
            System.getenv("JC_HOME_SIMULATOR"),
            System.getenv("JAVA_CARD_SIMULATOR_HOME")
        };
        for (String root : candidates) {
            Optional<Path> found = resolveFromSimulatorHome(root);
            if (found.isPresent()) {
                return found;
            }
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            Path homePath = Path.of(userHome);
            if (Files.isDirectory(homePath)) {
                try (Stream<Path> dirs = Files.list(homePath)) {
                    Optional<Path> matchedDir = dirs
                            .filter(Files::isDirectory)
                            .filter(p -> p.getFileName().toString().startsWith("java_card_devkit_simulator"))
                            .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                            .map(p -> p.resolve("client").resolve("COMService").resolve("socketprovider.jar"))
                            .filter(Files::isRegularFile)
                            .findFirst();
                    if (matchedDir.isPresent()) {
                        return matchedDir;
                    }
                } catch (IOException ignored) {
                    // Ignore unreadable user home entries.
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Instantiates a {@link TerminalFactory} backed by {@code SocketCardTerminalProvider}.
     *
     * @param host simulator hostname or IP
     * @param port simulator TCP port
     * @param explicitJarOrNull optional socketprovider.jar path override
     * @return configured TerminalFactory instance
     */
    public static TerminalFactory createSocketTerminalFactory(String host, int port, Path explicitJarOrNull) {
        LoaderAndFactory opened = openSocketTerminalFactory(host, port, explicitJarOrNull);
        // Caller owns only the returned TerminalFactory here; keep classloader alive with no extra leak in tests.
        return opened.factory;
    }

    public static byte[] buildPunchPayload(LocalDateTime timestamp, int areaCode) {
        if (timestamp == null || areaCode < 1 || areaCode > 32) {
            throw new IllegalArgumentException("Invalid timestamp or areaCode");
        }
        byte[] tsBytes = buildTimestampPayload(timestamp);
        return new byte[] {
            tsBytes[0], tsBytes[1], tsBytes[2], tsBytes[3], (byte) (areaCode & 0xFF)
        };
    }

    public static byte[] buildTimestampPayload(LocalDateTime timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
        long epochSec = timestamp.atZone(ZoneId.systemDefault()).toEpochSecond();
        if (epochSec == 0L) {
            epochSec = 1L;
        }
        return new byte[] {
            (byte) ((epochSec >>> 24) & 0xFF),
            (byte) ((epochSec >>> 16) & 0xFF),
            (byte) ((epochSec >>> 8) & 0xFF),
            (byte) (epochSec & 0xFF)
        };
    }

    public static CardChipStatus decodeCardStatus(byte[] data) {
        if (data == null || data.length < 4) {
            throw new IllegalArgumentException("Expected 4 bytes for card status");
        }
        CardStatus status;
        switch (data[0]) {
            case 0x00:
                status = CardStatus.ACTIVE;
                break;
            case 0x01:
                status = CardStatus.LOCKED;
                break;
            case 0x02:
                status = CardStatus.REVOKED;
                break;
            default:
                throw new IllegalArgumentException("Unknown card status byte: " + data[0]);
        }
        boolean pinValidated = data[1] == 0x01;
        int punchCounter = data[2] & 0xFF;
        boolean shiftOpen = data[3] == 0x01;
        return new CardChipStatus(status, pinValidated, punchCounter, shiftOpen);
    }

    public static LastCardAttendance decodeLastAttendance(byte[] data) {
        if (data == null || data.length < 7) {
            throw new IllegalArgumentException("Expected 7 bytes for last attendance");
        }
        byte eventByte = data[0];
        boolean shiftOpen = data[6] == 0x01;
        if (eventByte == 0x00) {
            return new LastCardAttendance(null, null, 0, shiftOpen);
        }
        AttendanceEventType eventType;
        if (eventByte == 0x01) {
            eventType = AttendanceEventType.CHECK_IN;
        } else if (eventByte == 0x02) {
            eventType = AttendanceEventType.CHECK_OUT;
        } else {
            throw new IllegalArgumentException("Unknown event type byte: " + eventByte);
        }
        long epochSec = ((data[1] & 0xFFL) << 24)
                | ((data[2] & 0xFFL) << 16)
                | ((data[3] & 0xFFL) << 8)
                | (data[4] & 0xFFL);
        LocalDateTime ts = LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSec), ZoneId.systemDefault());
        int areaCode = data[5] & 0xFF;
        return new LastCardAttendance(eventType, ts, areaCode, shiftOpen);
    }

    /**
     * Formats an APDU exchange summary while always redacting sensitive PIN payloads (INS=0x20).
     *
     * @param cla instruction class
     * @param ins instruction code
     * @param p1 parameter 1
     * @param p2 parameter 2
     * @param data command payload (nullable)
     * @param sw status word
     * @param respData response payload (nullable)
     * @return redacted human-readable APDU trace
     */
    public static String formatApduSummary(
            int cla,
            int ins,
            int p1,
            int p2,
            byte[] data,
            int sw,
            byte[] respData) {
        String cmdPayload;
        if ((ins & 0xFF) == INS_VERIFY_PIN) {
            cmdPayload = "[REDACTED]";
        } else if (data == null || data.length == 0) {
            cmdPayload = "[]";
        } else {
            cmdPayload = String.format("%02X [%s]", data.length & 0xFF, toHex(data));
        }
        String respHex = respData == null || respData.length == 0 ? "" : toHex(respData);
        return String.format("C-APDU %02X %02X %02X %02X %s -> R-APDU [%s] SW=%04X",
                cla & 0xFF, ins & 0xFF, p1 & 0xFF, p2 & 0xFF, cmdPayload, respHex, sw & 0xFFFF);
    }

    public static String describeStatusWord(int sw) {
        int code = sw & 0xFFFF;
        if (code == SW_NO_ERROR) {
            return "Thành công (SW=0x9000)";
        }
        if (code == SW_SECURITY_STATUS_NOT_SATISFIED) {
            return "Thẻ chưa xác thực mã PIN (SW=0x6982)";
        }
        if (code == SW_CARD_LOCKED_OR_REVOKED) {
            return "Thẻ đang bị khóa hoặc đã thu hồi / khóa PIN (SW=0x6983)";
        }
        if (code == SW_CONDITIONS_NOT_SATISFIED) {
            return "Điều kiện ca/trạng thái trên thẻ không hợp lệ (SW=0x6985)";
        }
        if (code == SW_WRONG_DATA) {
            return "Dữ liệu gửi xuống thẻ không hợp lệ (SW=0x6A80)";
        }
        if ((code & 0xFFF0) == 0x63C0) {
            int remaining = code & 0x000F;
            return "Sai mã PIN, còn " + remaining + " lần thử (SW=" + String.format("0x%04X", code) + ")";
        }
        return "Thẻ trả về lỗi SW=" + String.format("0x%04X", code);
    }

    private ResponseAPDU transmit(int cla, int ins, int p1, int p2, byte[] data, int ne) throws CardException {
        CommandAPDU cmd;
        if (data != null && data.length > 0 && ne > 0) {
            cmd = new CommandAPDU(cla, ins, p1, p2, data, ne);
        } else if (data != null && data.length > 0) {
            cmd = new CommandAPDU(cla, ins, p1, p2, data);
        } else if (ne > 0) {
            cmd = new CommandAPDU(cla, ins, p1, p2, ne);
        } else {
            cmd = new CommandAPDU(cla, ins, p1, p2);
        }
        ResponseAPDU resp = channel.transmit(cmd);
        lastApduSummary = formatApduSummary(cla, ins, p1, p2, data, resp.getSW(), resp.getData());
        return resp;
    }

    private void ensureConnected() {
        if (!isConnected()) {
            throw new IllegalStateException("Chưa kết nối tới Simulator jcsl.");
        }
    }

    private static Optional<Path> resolveFromSimulatorHome(String home) {
        if (home == null || home.isBlank()) {
            return Optional.empty();
        }
        Path candidate = Path.of(home.trim()).resolve("client").resolve("COMService").resolve("socketprovider.jar");
        return Files.isRegularFile(candidate) ? Optional.of(candidate) : Optional.empty();
    }

    private static LoaderAndFactory openSocketTerminalFactory(String host, int port, Path explicitJarOrNull) {
        URLClassLoader newLoader = null;
        try {
            Class<?> providerClass;
            try {
                providerClass = Class.forName(SOCKET_PROVIDER_CLASS);
            } catch (ClassNotFoundException missingOnClasspath) {
                Path jarPath = detectSocketProviderJar(explicitJarOrNull).orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy socketprovider.jar của Java Card Simulator."));
                newLoader = new URLClassLoader(
                        new URL[] { jarPath.toUri().toURL() },
                        ApduSimulatorCardGateway.class.getClassLoader());
                providerClass = Class.forName(SOCKET_PROVIDER_CLASS, true, newLoader);
            }

            Provider provider = (Provider) providerClass.getDeclaredConstructor().newInstance();
            TerminalFactory factory = TerminalFactory.getInstance(
                    SOCKET_FACTORY_TYPE,
                    List.of(new InetSocketAddress(host, port)),
                    provider);
            return new LoaderAndFactory(newLoader, factory);
        } catch (Exception ex) {
            if (newLoader != null) {
                try {
                    newLoader.close();
                } catch (IOException ignored) {
                    // Best-effort cleanup.
                }
            }
            if (ex instanceof IllegalStateException) {
                throw (IllegalStateException) ex;
            }
            throw new IllegalStateException("Không thể khởi tạo SocketCardTerminalProvider: " + ex.getMessage(), ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private static final class LoaderAndFactory {
        final URLClassLoader ownedLoader;
        final TerminalFactory factory;

        LoaderAndFactory(URLClassLoader ownedLoader, TerminalFactory factory) {
            this.ownedLoader = ownedLoader;
            this.factory = factory;
        }
    }

    /**
     * Decoded status snapshot from INS=0x34.
     */
    public static final class CardChipStatus {

        private final CardStatus status;
        private final boolean pinValidated;
        private final int punchCounter;
        private final boolean shiftOpen;

        public CardChipStatus(CardStatus status, boolean pinValidated, int punchCounter, boolean shiftOpen) {
            this.status = status;
            this.pinValidated = pinValidated;
            this.punchCounter = punchCounter;
            this.shiftOpen = shiftOpen;
        }

        public CardStatus getStatus() {
            return status;
        }

        public boolean isPinValidated() {
            return pinValidated;
        }

        public int getPunchCounter() {
            return punchCounter;
        }

        public boolean isShiftOpen() {
            return shiftOpen;
        }
    }

    /**
     * Decoded last attendance entry from INS=0x42.
     */
    public static final class LastCardAttendance {

        private final AttendanceEventType eventType;
        private final LocalDateTime timestamp;
        private final int areaCode;
        private final boolean shiftOpen;

        public LastCardAttendance(
                AttendanceEventType eventType,
                LocalDateTime timestamp,
                int areaCode,
                boolean shiftOpen) {
            this.eventType = eventType;
            this.timestamp = timestamp;
            this.areaCode = areaCode;
            this.shiftOpen = shiftOpen;
        }

        public Optional<AttendanceEventType> getEventType() {
            return Optional.ofNullable(eventType);
        }

        public Optional<LocalDateTime> getTimestamp() {
            return Optional.ofNullable(timestamp);
        }

        public int getAreaCode() {
            return areaCode;
        }

        public boolean isShiftOpen() {
            return shiftOpen;
        }
    }
}
