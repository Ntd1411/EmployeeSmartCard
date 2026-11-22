package com.nhom11.smartcard.host.provider;

import com.nhom11.smartcard.host.model.AttendanceEventType;
import com.nhom11.smartcard.host.model.CardStatus;
import com.nhom11.smartcard.host.model.VirtualCard;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Provider;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    public static final String ISD_AID_STR = "aid:A000000151000000";
    public static final String PACKAGE_AID_STR = "aid:112233445511";
    public static final String APPLET_CLASS_AID_STR = "aid:1122334455";

    public static final int CLA_PROPRIETARY = 0x80;
    public static final int INS_VERIFY_PIN = 0x20;
    public static final int INS_SET_CARD_STATUS = 0x30;
    public static final int INS_SET_AREA_MASK = 0x32;
    public static final int INS_GET_CARD_STATUS = 0x34;
    public static final int INS_ATTENDANCE_PUNCH = 0x40;
    public static final int INS_GET_LAST_ATTENDANCE = 0x42;
    public static final int INS_CHECK_AREA_ACCESS = 0x50;
    public static final long DEFAULT_CONNECT_TIMEOUT_MS = 4000L;
    public static final long DEFAULT_DEPLOY_TIMEOUT_MS = 15000L;

    private static final String SOCKET_PROVIDER_CLASS = "com.oracle.smartcardio.socket.SocketCardTerminalProvider";
    private static final String SOCKET_FACTORY_TYPE = "SocketCardTerminalFactoryType";
    private static final String AM_SERVICE_FACTORY_CLASS = "com.oracle.javacard.ams.AMServiceFactory";
    private static final String AM_SERVICE_PROVIDER_IMPL_CLASS = "com.oracle.javacard.ams.impl.AMServiceProviderImpl";
    private static final String AM_SERVICE_CLASS = "com.oracle.javacard.ams.AMService";
    private static final String AM_SESSION_CLASS = "com.oracle.javacard.ams.AMSession";
    private static final String CAP_FILE_CLASS = "com.oracle.javacard.ams.config.CAPFile";
    private static final String SCRIPTABLE_CLASS = "com.oracle.javacard.ams.script.Scriptable";

    private final Path explicitSocketJar;
    private final VirtualCardProvider cardProvider;
    private URLClassLoader ownedLoader;
    private Card card;
    private CardChannel channel;
    private String connectedEndpoint;
    private boolean multiCardMode;
    private String activeCardId;
    private byte[] activeAid;
    private byte[] cachedPinBytes;
    private String lastApduSummary = "Chưa kết nối tới Simulator jcsl.";

    public ApduSimulatorCardGateway() {
        this((VirtualCardProvider) null, null);
    }

    public ApduSimulatorCardGateway(Path explicitSocketJar) {
        this(null, explicitSocketJar);
    }

    public ApduSimulatorCardGateway(VirtualCardProvider cardProvider) {
        this(cardProvider, null);
    }

    public ApduSimulatorCardGateway(VirtualCardProvider cardProvider, Path explicitSocketJar) {
        this.cardProvider = cardProvider;
        this.explicitSocketJar = explicitSocketJar;
    }

    /**
     * Connects to the simulator over TCP using the default timeout, powers up the card,
     * and selects the employee card applet.
     *
     * @param host simulator hostname or IP address
     * @param port simulator port (default 9025)
     * @throws IllegalStateException if socketprovider.jar is missing, simulator times out or refuses connection,
     *     or the applet AID 1122334455 is not installed
     */
    public synchronized void connect(String host, int port) {
        connect(host, port, DEFAULT_CONNECT_TIMEOUT_MS);
    }

    /**
     * Connects to the simulator over TCP with an interruptible timeout, powers up the card,
     * and selects the employee card applet.
     *
     * @param host simulator hostname or IP address
     * @param port simulator port (default 9025)
     * @param timeoutMs connection and ATR handshake timeout in milliseconds
     * @throws IllegalStateException if socketprovider.jar is missing, simulator times out or refuses connection,
     *     or the applet AID 1122334455 is not installed
     */
    public synchronized void connect(String host, int port, long timeoutMs) {
        disconnect();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host simulator không được để trống.");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Cổng simulator phải nằm trong khoảng 1..65535.");
        }
        if (timeoutMs <= 0L) {
            throw new IllegalArgumentException("Timeout kết nối phải > 0 ms.");
        }

        final String trimmedHost = host.trim();
        final long waitPresentMs = Math.min(Math.max(timeoutMs / 2L, 200L), 1000L);
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jcsl-connect");
            t.setDaemon(true);
            return t;
        });

        try {
            Future<ConnectSession> future = executor.submit(() -> {
                LoaderAndFactory loaded = openSocketTerminalFactory(trimmedHost, port, explicitSocketJar, null);
                Card connectedCard = null;
                try {
                    List<CardTerminal> terminals = loaded.factory.terminals().list();
                    if (terminals.isEmpty()) {
                        throw new IllegalStateException("Không tìm thấy SocketCardTerminal cho " + trimmedHost + ":" + port);
                    }
                    CardTerminal terminal = terminals.get(0);
                    if (!terminal.waitForCardPresent(waitPresentMs)) {
                        throw new IllegalStateException(
                                "Không nhận được phản hồi từ simulator tại " + trimmedHost + ":" + port);
                    }

                    connectedCard = terminal.connect("T=1");
                    CardChannel basicChannel = connectedCard.getBasicChannel();

                    // Probe CARD-001 multi-card instance first, then fall back to single-card legacy AID.
                    byte[] multiFirstAid = resolveAppletAid("CARD-001");
                    ResponseAPDU multiResp = basicChannel.transmit(
                            new CommandAPDU(0x00, 0xA4, 0x04, 0x00, multiFirstAid, 256));
                    if (multiResp.getSW() == SW_NO_ERROR) {
                        String trace = formatApduSummary(0x00, 0xA4, 0x04, 0x00, multiFirstAid,
                                multiResp.getSW(), multiResp.getData());
                        return new ConnectSession(loaded, connectedCard, basicChannel, trace, true, "CARD-001", multiFirstAid);
                    }

                    ResponseAPDU selectResp = basicChannel.transmit(
                            new CommandAPDU(0x00, 0xA4, 0x04, 0x00, APPLET_AID, 256));
                    String selectTrace = formatApduSummary(0x00, 0xA4, 0x04, 0x00, APPLET_AID,
                            selectResp.getSW(), selectResp.getData());

                    if (selectResp.getSW() != SW_NO_ERROR) {
                        if (selectResp.getSW() == 0x6A82) {
                            throw new IllegalStateException(
                                    "Kết nối tới jcsl thành công nhưng chưa có Applet nào được cài. Hãy bấm 'Cài 5 thẻ lên jcsl' hoặc load/install trong APDU Console.");
                        }
                        throw new IllegalStateException(
                                "Không thể select applet trên jcsl (SW="
                                        + String.format("0x%04X", selectResp.getSW()) + ").");
                    }

                    return new ConnectSession(loaded, connectedCard, basicChannel, selectTrace, false, null, APPLET_AID);
                } catch (Exception ex) {
                    if (connectedCard != null) {
                        try {
                            connectedCard.disconnect(false);
                        } catch (CardException ignored) {
                            // Best-effort cleanup on failed handshake.
                        }
                    }
                    closeLoaderQuietly(loaded.ownedLoader);
                    throw ex;
                }
            });

            ConnectSession session = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            this.ownedLoader = session.loaded.ownedLoader;
            this.card = session.card;
            this.channel = session.channel;
            this.connectedEndpoint = trimmedHost + ":" + port;
            this.multiCardMode = session.multiCardMode;
            this.activeCardId = session.activeCardId;
            this.activeAid = session.activeAid == null ? null : session.activeAid.clone();
            this.lastApduSummary = session.selectTrace;
        } catch (TimeoutException ex) {
            lastApduSummary = "Hết thời gian chờ kết nối tới jcsl (" + trimmedHost + ":" + port + ").";
            throw new IllegalStateException(
                    "Hết thời gian chờ kết nối tới jcsl (" + trimmedHost + ":" + port + ").\n"
                    + "Lưu ý: jcsl chỉ cho phép 1 kết nối cùng lúc — hãy gõ 'disconnect;' trong APDU Console của Eclipse rồi thử lại.",
                    ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kết nối tới simulator bị gián đoạn.", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof IllegalStateException) {
                throw (IllegalStateException) cause;
            }
            throw new IllegalStateException(
                    "Không thể kết nối tới simulator " + trimmedHost + ":" + port + " — " + cause.getMessage(),
                    cause);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Loads the compiled CAP once and installs one independent applet instance per virtual card on jcsl,
     * initializes LOCKED/REVOKED states on-chip, and leaves an active session open in multi-card mode.
     *
     * @param host simulator hostname or IP
     * @param port simulator TCP port
     * @param timeoutMs overall deployment timeout in milliseconds
     * @param cards list of virtual cards to install (typically CARD-001..CARD-005)
     * @param defaultPinChars PIN to cache and verify on the initial active card (nullable)
     * @return number of installed card instances
     */
    public synchronized int deployMultiCards(
            String host,
            int port,
            long timeoutMs,
            List<VirtualCard> cards,
            char[] defaultPinChars) {
        disconnect();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host simulator không được để trống.");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Cổng simulator phải nằm trong khoảng 1..65535.");
        }
        if (cards == null || cards.isEmpty()) {
            throw new IllegalArgumentException("Danh sách thẻ cài đặt không được rỗng.");
        }

        final Path capPath = detectCapFile().orElseThrow(() -> new IllegalStateException(
                "Không tìm thấy smartcard.cap. Hãy build project EmployeeSmartCard trong Eclipse trước."));
        final Path propsPath = detectClientConfigProperties().orElseThrow(() -> new IllegalStateException(
                "Không tìm thấy client.config.properties trong thư mục Simulator."));
        final Path amJarPath = detectAmServiceJar().orElseThrow(() -> new IllegalStateException(
                "Không tìm thấy amservice.jar trong thư mục Simulator."));
        final Path socketJarPath = detectSocketProviderJar(explicitSocketJar).orElseThrow(() -> new IllegalStateException(
                "Không tìm thấy socketprovider.jar trong thư mục Simulator."));

        final String trimmedHost = host.trim();
        final long effectiveTimeout = Math.max(timeoutMs, 10000L);
        final long waitPresentMs = Math.min(Math.max(effectiveTimeout / 4L, 300L), 1000L);
        final byte[] pinCopy = toAsciiPinBytes(defaultPinChars);
        final List<VirtualCard> cardSnapshot = new ArrayList<>(cards);

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jcsl-deploy");
            t.setDaemon(true);
            return t;
        });

        try {
            Future<ConnectSession> future = executor.submit(() -> {
                URLClassLoader combinedLoader = new URLClassLoader(
                        new URL[] { socketJarPath.toUri().toURL(), amJarPath.toUri().toURL() },
                        ApduSimulatorCardGateway.class.getClassLoader());
                Card connectedCard = null;
                ClassLoader prevContextLoader = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(combinedLoader);

                try {
                    LoaderAndFactory loaded = openSocketTerminalFactory(
                            trimmedHost, port, explicitSocketJar, combinedLoader);

                    List<CardTerminal> terminals = loaded.factory.terminals().list();
                    if (terminals.isEmpty()) {
                        throw new IllegalStateException("Không tìm thấy SocketCardTerminal cho " + trimmedHost + ":" + port);
                    }
                    CardTerminal terminal = terminals.get(0);
                    if (!terminal.waitForCardPresent(waitPresentMs)) {
                        throw new IllegalStateException(
                                "Không nhận được phản hồi từ simulator tại " + trimmedHost + ":" + port);
                    }

                    connectedCard = terminal.connect("T=1");
                    CardChannel basicChannel = connectedCard.getBasicChannel();

                    Properties props = new Properties();
                    try (InputStream in = Files.newInputStream(propsPath)) {
                        props.load(in);
                    }

                    Object ams = createAmService(combinedLoader);
                    Class<?> amsInterface = Class.forName(AM_SERVICE_CLASS, true, combinedLoader);
                    amsInterface.getMethod("setProperties", Properties.class).invoke(ams, props);

                    Method openSessionMethod = amsInterface.getMethod("openSession", String.class);
                    Class<?> amSessionClass = Class.forName(AM_SESSION_CLASS, true, combinedLoader);
                    Method loadMethod = amSessionClass.getMethod("load", String.class, byte[].class);
                    Method installMethod = amSessionClass.getMethod(
                            "install", String.class, String.class, String.class, Object[].class);
                    Method uninstallMethod = amSessionClass.getMethod("uninstall", String[].class);
                    Method unloadMethod = amSessionClass.getMethod("unload", String.class);
                    Method closeMethod = amSessionClass.getMethod("close");
                    Class<?> scriptableClass = Class.forName(SCRIPTABLE_CLASS, true, combinedLoader);
                    Method runMethod = scriptableClass.getMethod("run", Object.class);

                    // Best-effort uninstall of legacy single AID and any existing card instance AIDs.
                    List<String> installedAids = new ArrayList<>();
                    if (isAidSelectable(basicChannel, APPLET_AID)) {
                        installedAids.add("aid:" + formatAidHex(APPLET_AID));
                    }
                    for (VirtualCard vc : cardSnapshot) {
                        byte[] cardAid = resolveAppletAid(vc.getCardId());
                        String aidStr = "aid:" + formatAidHex(cardAid);
                        if (!installedAids.contains(aidStr) && isAidSelectable(basicChannel, cardAid)) {
                            installedAids.add(aidStr);
                        }
                    }
                    if (!installedAids.isEmpty()) {
                        try {
                            Object cleanup = openSessionMethod.invoke(ams, ISD_AID_STR);
                            uninstallMethod.invoke(cleanup, (Object) installedAids.toArray(new String[0]));
                            closeMethod.invoke(cleanup);
                            runMethod.invoke(cleanup, basicChannel);
                        } catch (Exception ignored) {
                            // Continue to unload attempt even if individual uninstall fails.
                        }
                    }
                    try {
                        Object pkgCleanup = openSessionMethod.invoke(ams, ISD_AID_STR);
                        unloadMethod.invoke(pkgCleanup, PACKAGE_AID_STR);
                        closeMethod.invoke(pkgCleanup);
                        runMethod.invoke(pkgCleanup, basicChannel);
                    } catch (Exception ignored) {
                        // Package may not have been loaded yet.
                    }

                    // Load CAP once and install one independent applet instance per card.
                    Class<?> capFileClass = Class.forName(CAP_FILE_CLASS, true, combinedLoader);
                    Object capObj = capFileClass.getMethod("from", String.class)
                            .invoke(null, capPath.toAbsolutePath().toString());
                    byte[] capBytes = (byte[]) capFileClass.getMethod("getBytes").invoke(capObj);

                    Object deploySession = openSessionMethod.invoke(ams, ISD_AID_STR);
                    loadMethod.invoke(deploySession, PACKAGE_AID_STR, capBytes);
                    for (VirtualCard vc : cardSnapshot) {
                        String instanceAidUri = "aid:" + formatAidHex(resolveAppletAid(vc.getCardId()));
                        installMethod.invoke(
                                deploySession,
                                PACKAGE_AID_STR,
                                APPLET_CLASS_AID_STR,
                                instanceAidUri,
                                new Object[0]);
                    }
                    closeMethod.invoke(deploySession);
                    runMethod.invoke(deploySession, basicChannel);

                    // Mirror initial LOCKED / REVOKED lifecycle state onto corresponding chip instances.
                    VirtualCard initialSelectedCard = null;
                    for (VirtualCard vc : cardSnapshot) {
                        byte[] cardAid = resolveAppletAid(vc.getCardId());
                        if (vc.getStatus() == CardStatus.LOCKED || vc.getStatus() == CardStatus.REVOKED) {
                            ResponseAPDU sel = basicChannel.transmit(new CommandAPDU(0x00, 0xA4, 0x04, 0x00, cardAid, 256));
                            if (sel.getSW() == SW_NO_ERROR) {
                                int statusParam = vc.getStatus() == CardStatus.LOCKED ? 0x01 : 0x02;
                                basicChannel.transmit(new CommandAPDU(
                                        CLA_PROPRIETARY, INS_SET_CARD_STATUS, statusParam, 0x00));
                            }
                        } else if (initialSelectedCard == null && vc.isPinVerified()) {
                            initialSelectedCard = vc;
                        }
                    }

                    if (initialSelectedCard == null) {
                        initialSelectedCard = cardSnapshot.get(0);
                    }
                    byte[] initialAid = resolveAppletAid(initialSelectedCard.getCardId());
                    ResponseAPDU finalSel = basicChannel.transmit(
                            new CommandAPDU(0x00, 0xA4, 0x04, 0x00, initialAid, 256));
                    if (finalSel.getSW() != SW_NO_ERROR) {
                        throw new IllegalStateException(
                                "Cài đặt xong nhưng không select được thẻ đầu tiên (SW="
                                        + String.format("0x%04X", finalSel.getSW()) + ").");
                    }
                    String trace = formatApduSummary(0x00, 0xA4, 0x04, 0x00, initialAid,
                            finalSel.getSW(), finalSel.getData());

                    if (pinCopy != null && initialSelectedCard.getStatus() == CardStatus.ACTIVE
                            && initialSelectedCard.isPinVerified()) {
                        ResponseAPDU pinResp = basicChannel.transmit(
                                new CommandAPDU(CLA_PROPRIETARY, INS_VERIFY_PIN, 0x00, 0x00, pinCopy));
                        trace = formatApduSummary(CLA_PROPRIETARY, INS_VERIFY_PIN, 0x00, 0x00,
                                pinCopy, pinResp.getSW(), pinResp.getData());
                    }

                    return new ConnectSession(
                            new LoaderAndFactory(combinedLoader, loaded.factory),
                            connectedCard,
                            basicChannel,
                            trace,
                            true,
                            initialSelectedCard.getCardId(),
                            initialAid);
                } catch (Exception ex) {
                    if (connectedCard != null) {
                        try {
                            connectedCard.disconnect(false);
                        } catch (CardException ignored) {
                            // Best-effort cleanup.
                        }
                    }
                    closeLoaderQuietly(combinedLoader);
                    if (ex instanceof InvocationTargetException && ex.getCause() != null) {
                        Throwable target = ex.getCause();
                        throw new IllegalStateException("Lỗi cài nhiều thẻ lên jcsl: " + target.getMessage(), target);
                    }
                    throw ex;
                } finally {
                    Thread.currentThread().setContextClassLoader(prevContextLoader);
                }
            });

            ConnectSession session = future.get(effectiveTimeout, TimeUnit.MILLISECONDS);
            this.ownedLoader = session.loaded.ownedLoader;
            this.card = session.card;
            this.channel = session.channel;
            this.connectedEndpoint = trimmedHost + ":" + port;
            this.multiCardMode = session.multiCardMode;
            this.activeCardId = session.activeCardId;
            this.activeAid = session.activeAid == null ? null : session.activeAid.clone();
            this.cachedPinBytes = pinCopy;
            this.lastApduSummary = session.selectTrace;
            return cardSnapshot.size();
        } catch (TimeoutException ex) {
            zeroArray(pinCopy);
            lastApduSummary = "Hết thời gian chờ cài 5 thẻ lên jcsl (" + trimmedHost + ":" + port + ").";
            throw new IllegalStateException(
                    "Hết thời gian chờ cài 5 thẻ lên jcsl (" + trimmedHost + ":" + port + ").\n"
                    + "Hãy đảm bảo Simulator đang bật và đã gõ 'disconnect;' bên APDU Console.",
                    ex);
        } catch (InterruptedException ex) {
            zeroArray(pinCopy);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Quá trình cài nhiều thẻ bị gián đoạn.", ex);
        } catch (ExecutionException ex) {
            zeroArray(pinCopy);
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof IllegalStateException) {
                throw (IllegalStateException) cause;
            }
            throw new IllegalStateException("Không thể cài nhiều thẻ lên jcsl: " + cause.getMessage(), cause);
        } finally {
            executor.shutdownNow();
        }
    }

    public synchronized void disconnect() {
        zeroArray(cachedPinBytes);
        cachedPinBytes = null;
        multiCardMode = false;
        activeCardId = null;
        activeAid = null;

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

    public synchronized boolean isMultiCardMode() {
        return multiCardMode;
    }

    public synchronized String getActiveCardId() {
        return activeCardId;
    }

    public synchronized String getActiveAidHex() {
        return activeAid == null ? "-" : formatAidHex(activeAid);
    }

    public synchronized String getConnectedEndpoint() {
        return connectedEndpoint;
    }

    public synchronized String getLastApduSummary() {
        return lastApduSummary;
    }

    /**
     * Runs a dummy photo round trip against the single-card simulator instance only.
     * BEGIN replaces any previous photo; raw payloads are not added to the APDU trace.
     */
    public synchronized boolean verifyPhotoRoundTrip(byte[] bytes) throws CardException {
        ensureConnected();
        if (multiCardMode || activeAid == null || !Arrays.equals(activeAid, APPLET_AID)) {
            throw new IllegalStateException("Bài thử chỉ hỗ trợ instance simulator đơn " + formatAidHex(APPLET_AID) + ".");
        }
        return PhotoTransfer.roundTrip(channel::transmit, bytes);
    }

    /**
     * Verifies PIN against the currently active applet on jcsl.
     *
     * @param pinChars cardholder PIN characters
     * @return gateway result reflecting ISO7816 SW
     */
    public synchronized CardGatewayResult verifyPin(char[] pinChars) {
        return verifyPin(activeCardId, pinChars);
    }

    /**
     * Verifies PIN against the specified card's applet on jcsl without logging plaintext PIN bytes.
     *
     * @param cardId target virtual card ID (nullable in single-card mode)
     * @param pinChars cardholder PIN characters
     * @return gateway result reflecting ISO7816 SW
     */
    public synchronized CardGatewayResult verifyPin(String cardId, char[] pinChars) {
        if (!isConnected()) {
            return CardGatewayResult.failure(SW_CONDITIONS_NOT_SATISFIED, "Chưa kết nối tới Simulator jcsl.");
        }
        if (pinChars == null || pinChars.length == 0 || pinChars.length > 8) {
            return CardGatewayResult.failure(0x6700, "Độ dài PIN phải từ 1 đến 8 ký tự.");
        }

        byte[] pinBytes = new byte[pinChars.length];
        try {
            ensureCardSelected(cardId, false);
            for (int i = 0; i < pinChars.length; i++) {
                pinBytes[i] = (byte) (pinChars[i] & 0x7F);
            }
            ResponseAPDU resp = transmit(CLA_PROPRIETARY, INS_VERIFY_PIN, 0x00, 0x00, pinBytes, 0);
            int sw = resp.getSW();
            if (sw == SW_NO_ERROR) {
                zeroArray(cachedPinBytes);
                cachedPinBytes = pinBytes.clone();
                return CardGatewayResult.ok("Xác thực PIN trên jcsl thành công (SW=0x9000).");
            }
            return CardGatewayResult.failure(sw, describeStatusWord(sw));
        } catch (CardException ex) {
            return CardGatewayResult.failure(SW_CONDITIONS_NOT_SATISFIED, "Lỗi truyền APDU: " + ex.getMessage());
        } finally {
            Arrays.fill(pinBytes, (byte) 0);
        }
    }

    public synchronized CardGatewayResult setCardStatus(CardStatus status) {
        return setCardStatus(activeCardId, status);
    }

    /**
     * Updates the lifecycle status on the target virtual card's applet instance.
     *
     * @param cardId target virtual card ID (nullable in single-card mode)
     * @param status target lifecycle status
     * @return gateway result
     */
    public synchronized CardGatewayResult setCardStatus(String cardId, CardStatus status) {
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
            ensureCardSelected(cardId, false);
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

    public synchronized CardGatewayResult setAreaMask(int mask32) {
        return setAreaMask(activeCardId, mask32);
    }

    /**
     * Updates the 32-bit area bitmask stored inside the target card's applet on jcsl.
     *
     * @param cardId target virtual card ID (nullable in single-card mode)
     * @param mask32 bitmask where bit (areaCode - 1) represents area permission
     * @return gateway result
     */
    public synchronized CardGatewayResult setAreaMask(String cardId, int mask32) {
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
            ensureCardSelected(cardId, false);
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

    public synchronized CardChipStatus readCardStatus() {
        return readCardStatus(activeCardId);
    }

    /**
     * Reads chip lifecycle status, PIN validation flag, punch counter, and shift state (INS=0x34).
     *
     * @param cardId target virtual card ID (nullable in single-card mode)
     * @return decoded chip status
     */
    public synchronized CardChipStatus readCardStatus(String cardId) {
        ensureConnected();
        try {
            ensureCardSelected(cardId, false);
            ResponseAPDU resp = transmit(CLA_PROPRIETARY, INS_GET_CARD_STATUS, 0x00, 0x00, null, 4);
            if (resp.getSW() != SW_NO_ERROR) {
                throw new IllegalStateException(describeStatusWord(resp.getSW()));
            }
            return decodeCardStatus(resp.getData());
        } catch (CardException ex) {
            throw new IllegalStateException("Lỗi đọc trạng thái thẻ trên jcsl: " + ex.getMessage(), ex);
        }
    }

    public synchronized LastCardAttendance readLastAttendance() {
        return readLastAttendance(activeCardId);
    }

    /**
     * Reads the most recent attendance event stored inside the target card's circular buffer (INS=0x42).
     *
     * @param cardId target virtual card ID (nullable in single-card mode)
     * @return decoded last attendance summary
     */
    public synchronized LastCardAttendance readLastAttendance(String cardId) {
        ensureConnected();
        try {
            ensureCardSelected(cardId, false);
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
            ensureCardSelected(cardId, true);
            ResponseAPDU resp = transmit(CLA_PROPRIETARY, INS_ATTENDANCE_PUNCH, p1, p2, payload, 2);
            int sw = resp.getSW();
            if (sw == SW_NO_ERROR) {
                byte[] out = resp.getData();
                String info = "Ghi nhận trên jcsl thành công";
                if (activeCardId != null) {
                    info += " [" + activeCardId + "]";
                }
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
            ensureCardSelected(cardId, true);
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

    public static Optional<Path> detectSimulatorHome() {
        String[] candidates = {
            System.getProperty("jc.simulator.home"),
            System.getenv("JC_HOME_SIMULATOR"),
            System.getenv("JAVA_CARD_SIMULATOR_HOME")
        };
        for (String root : candidates) {
            if (root != null && !root.isBlank()) {
                Path p = Path.of(root.trim());
                if (Files.isDirectory(p)) {
                    return Optional.of(p);
                }
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

    public static Optional<Path> detectAmServiceJar() {
        return detectSimulatorHome()
                .map(home -> home.resolve("client").resolve("AMService").resolve("amservice.jar"))
                .filter(Files::isRegularFile);
    }

    public static Optional<Path> detectClientConfigProperties() {
        return detectSimulatorHome()
                .map(home -> home.resolve("samples").resolve("client.config.properties"))
                .filter(Files::isRegularFile);
    }

    public static Optional<Path> detectCapFile() {
        Path[] candidates = {
            Path.of("deliverables", "EmployeeSmartCard", "com", "nhom11", "smartcard", "javacard", "smartcard.cap"),
            Path.of("..", "deliverables", "EmployeeSmartCard", "com", "nhom11", "smartcard", "javacard", "smartcard.cap")
        };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return Optional.of(p.normalize());
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves distinct instance AID (1122334455xx) for CARD-NNN IDs, falling back to APPLET_AID.
     *
     * @param cardId virtual card identifier
     * @return AID bytes for SELECT and deployment
     */
    public static byte[] resolveAppletAid(String cardId) {
        if (cardId != null) {
            String upper = cardId.trim().toUpperCase(Locale.ROOT);
            if (upper.startsWith("CARD-")) {
                String suffix = upper.substring(5);
                try {
                    int num = Integer.parseInt(suffix);
                    if (num >= 1 && num <= 255) {
                        return new byte[] {
                            (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) (num & 0xFF)
                        };
                    }
                } catch (NumberFormatException ignored) {
                    // Fall back to default applet AID.
                }
            }
        }
        return APPLET_AID.clone();
    }

    public static String formatAidHex(byte[] aid) {
        return aid == null ? "" : toHex(aid);
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
        return detectSimulatorHome()
                .map(home -> home.resolve("client").resolve("COMService").resolve("socketprovider.jar"))
                .filter(Files::isRegularFile);
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
        LoaderAndFactory opened = openSocketTerminalFactory(host, port, explicitJarOrNull, null);
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

    private void ensureCardSelected(String cardId, boolean allowAutoVerifyPin) throws CardException {
        if (!multiCardMode || cardId == null || cardId.isBlank()) {
            return;
        }
        String normalizedCardId = cardId.trim();
        byte[] targetAid = resolveAppletAid(normalizedCardId);
        if (activeAid != null && Arrays.equals(targetAid, activeAid)) {
            return;
        }

        CommandAPDU selectCmd = new CommandAPDU(0x00, 0xA4, 0x04, 0x00, targetAid, 256);
        ResponseAPDU selectResp = channel.transmit(selectCmd);
        lastApduSummary = formatApduSummary(0x00, 0xA4, 0x04, 0x00, targetAid,
                selectResp.getSW(), selectResp.getData());
        if (selectResp.getSW() != SW_NO_ERROR) {
            throw new CardException("Chưa cài thẻ " + normalizedCardId + " (AID "
                    + formatAidHex(targetAid) + ") trên jcsl (SW="
                    + String.format("0x%04X", selectResp.getSW()) + ")");
        }

        activeAid = targetAid;
        activeCardId = normalizedCardId;

        if (allowAutoVerifyPin && cachedPinBytes != null && shouldAutoVerifyPin(normalizedCardId)) {
            ResponseAPDU pinResp = transmit(CLA_PROPRIETARY, INS_VERIFY_PIN, 0x00, 0x00, cachedPinBytes, 0);
            if (pinResp.getSW() != SW_NO_ERROR && pinResp.getSW() != SW_CARD_LOCKED_OR_REVOKED) {
                lastApduSummary = formatApduSummary(CLA_PROPRIETARY, INS_VERIFY_PIN, 0x00, 0x00,
                        cachedPinBytes, pinResp.getSW(), pinResp.getData());
            }
        }
    }

    private boolean shouldAutoVerifyPin(String cardId) {
        if (cardProvider == null) {
            return true;
        }
        Optional<VirtualCard> cardOpt = cardProvider.findByCardId(cardId);
        return cardOpt.map(vc -> vc.getStatus() == CardStatus.ACTIVE && vc.isPinVerified()).orElse(false);
    }

    private static Object createAmService(ClassLoader loader) throws Exception {
        try {
            Class<?> amsFactoryClass = Class.forName(AM_SERVICE_FACTORY_CLASS, true, loader);
            return amsFactoryClass.getMethod("getInstance", String.class).invoke(null, "GP2.2");
        } catch (InvocationTargetException ex) {
            // URLClassLoader does not activate JPMS ModuleLayer ServiceLoader registrations;
            // fall back directly to the public provider implementation class inside amservice.jar.
            Class<?> providerImplClass = Class.forName(AM_SERVICE_PROVIDER_IMPL_CLASS, true, loader);
            Object provider = providerImplClass.getDeclaredConstructor().newInstance();
            return providerImplClass.getMethod("getInstance", String.class).invoke(provider, "GP2.2");
        }
    }

    private static boolean isAidSelectable(CardChannel basicChannel, byte[] aid) {
        try {
            ResponseAPDU resp = basicChannel.transmit(new CommandAPDU(0x00, 0xA4, 0x04, 0x00, aid, 256));
            return resp.getSW() == SW_NO_ERROR;
        } catch (CardException ex) {
            return false;
        }
    }

    private static byte[] toAsciiPinBytes(char[] pinChars) {
        if (pinChars == null || pinChars.length == 0 || pinChars.length > 8) {
            return null;
        }
        byte[] bytes = new byte[pinChars.length];
        for (int i = 0; i < pinChars.length; i++) {
            bytes[i] = (byte) (pinChars[i] & 0x7F);
        }
        return bytes;
    }

    private static void zeroArray(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }

    private static LoaderAndFactory openSocketTerminalFactory(
            String host,
            int port,
            Path explicitJarOrNull,
            URLClassLoader existingLoaderOrNull) {
        URLClassLoader newLoader = null;
        try {
            Class<?> providerClass;
            if (existingLoaderOrNull != null) {
                providerClass = Class.forName(SOCKET_PROVIDER_CLASS, true, existingLoaderOrNull);
            } else {
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
            }

            Provider provider = (Provider) providerClass.getDeclaredConstructor().newInstance();
            TerminalFactory factory = TerminalFactory.getInstance(
                    SOCKET_FACTORY_TYPE,
                    List.of(new InetSocketAddress(host, port)),
                    provider);
            return new LoaderAndFactory(newLoader, factory);
        } catch (Exception ex) {
            closeLoaderQuietly(newLoader);
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

    private static void closeLoaderQuietly(URLClassLoader loader) {
        if (loader != null) {
            try {
                loader.close();
            } catch (IOException ignored) {
                // Best-effort cleanup.
            }
        }
    }

    private static final class ConnectSession {
        final LoaderAndFactory loaded;
        final Card card;
        final CardChannel channel;
        final String selectTrace;
        final boolean multiCardMode;
        final String activeCardId;
        final byte[] activeAid;

        ConnectSession(
                LoaderAndFactory loaded,
                Card card,
                CardChannel channel,
                String selectTrace,
                boolean multiCardMode,
                String activeCardId,
                byte[] activeAid) {
            this.loaded = loaded;
            this.card = card;
            this.channel = channel;
            this.selectTrace = selectTrace;
            this.multiCardMode = multiCardMode;
            this.activeCardId = activeCardId;
            this.activeAid = activeAid;
        }
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
