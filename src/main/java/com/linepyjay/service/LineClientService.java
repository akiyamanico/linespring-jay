package com.linepyjay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linepyjay.config.LineConfig;
import com.linepyjay.model.LineOperation;
import com.linepyjay.util.*;

import org.apache.thrift.transport.THttpClient;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Security;
import java.util.*;
import java.util.function.Consumer;

@Service
public class LineClientService {

    private static final Logger log = LoggerFactory.getLogger(LineClientService.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private Consumer<String> onAuthTokenUpdated = token -> {
        log.info("[update:authtoken] length={}", token.length());
        try {
            Files.writeString(Paths.get("token.txt"), "authToken=" + token + "\n");
        } catch (Exception e) {
            log.warn("token.txt write failed: {}", e.getMessage());
        }
    };
    private Consumer<String> onReady = mid -> log.info("[ready] mid={}", mid);
    private Consumer<String> onQrCall = url -> {
        System.out.println("\n>>> SCAN IN LINE APP:\n" + url + "\n>>> Waiting...\n");
    };
    private Consumer<String> onPinCall = pin -> log.info("[pincall] PIN={}", pin);

    private final LineConfig config;
    private final LineE2EEService e2ee;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final String userAgent;
    private final String systemType;
    private final String endpoint;

    private long lastGlobalRev = 0;
    private long lastIndividualRev = 0;

    private volatile boolean polling = false;
    private Thread pollThread;

    private String authToken;
    private String myMid;
    private long lastRevision = 0;
    private THttpClient talkTransport;
    private THttpClient pollTransport;

    /** Token refresh callback — updates authToken and notifies listener. */
    private final ThriftTransport.TokenRefreshCallback tokenCallback = newToken -> {
        this.authToken = newToken;
        onAuthTokenUpdated.accept(newToken);
    };

    public LineClientService(LineConfig config, LineE2EEService e2ee) {
        this.config = config;
        this.e2ee = e2ee;
        this.endpoint = config.getHostDomain()
                .replace("https://", "")
                .replace("http://", "");
        this.userAgent = "Line/" + config.getAppVersion();
        this.systemType = config.getLineAppName();
    }

    public LineClientService onAuthTokenUpdated(Consumer<String> h) {
        this.onAuthTokenUpdated = h;
        return this;
    }

    public LineClientService onReady(Consumer<String> h) {
        this.onReady = h;
        return this;
    }

    public LineClientService onQrCall(Consumer<String> h) {
        this.onQrCall = h;
        return this;
    }

    public LineClientService onPinCall(Consumer<String> h) {
        this.onPinCall = h;
        return this;
    }

    public String getAuthToken() {
        return authToken;
    }

    public String getMyMid() {
        return myMid;
    }

    public long getRevision() {
        return lastRevision;
    }

    public void setRevision(long r) {
        this.lastRevision = r;
    }

    private Map<String, String> getHeader(String method) {
        return HeaderBuilder.getHeader(endpoint, userAgent, systemType, authToken, method);
    }

    private Map<String, String> getHeader() {
        return HeaderBuilder.getHeader(endpoint, userAgent, systemType, authToken);
    }

    private Map<String, String> buildQrHeaders() {
        return HeaderBuilder.buildQrHeaders(endpoint, userAgent, systemType);
    }

    private Map<String, String> buildRestHeaders() {
        return HeaderBuilder.buildRestHeaders(userAgent, systemType, authToken);
    }

    public void loginWithAuthToken(String token) {
        try {
            log.info("[loginWithAuthToken]");
            this.authToken = token;
            onAuthTokenUpdated.accept(token);
            loadSession();
            startPolling();
            onReady.accept(myMid);
        } catch (Exception e) {
            throw new RuntimeException("loginWithAuthToken failed: " + e.getMessage(), e);
        }
    }

    public void loginWithQrCode(Consumer<String> onQrReady) {
        try {
            log.info("[loginWithQrCode]");
            Consumer<String> qrHandler = onQrReady != null ? onQrReady : this.onQrCall;

            String loginQrUrl = config.getHostDomain() + config.getLoginQrPath();
            String permitNoticeUrl = config.getHostDomain() + config.getPermitNoticePath();
            Map<String, String> qrHeaders = buildQrHeaders();

            String authSessionId = ThriftQrLoginUtil.createQrSession(loginQrUrl, qrHeaders, tokenCallback);
            log.debug("authSessionId={}", authSessionId);

            String callbackUrl = ThriftQrLoginUtil.createQrCode(loginQrUrl, qrHeaders, authSessionId, tokenCallback);
            qrHandler.accept(callbackUrl);

            System.out.println("\n========================================================");
            System.out.println("QR LOGIN URL — scan this in your LINE mobile app:");
            QrCodePrinter.print(callbackUrl);
            System.out.println("========================================================\n");

            log.info("[loginWithQrCode] Waiting for QR scan (long-poll)...");
            ThriftQrLoginUtil.checkQrCodeVerified(permitNoticeUrl, qrHeaders, authSessionId);
            log.info("[loginWithQrCode] QR scanned, attempting certificate verification...");

            String savedCert = null;
            try {
                java.nio.file.Path certFile = Paths.get("certificate.txt");
                if (Files.exists(certFile)) {
                    savedCert = Files.readString(certFile).strip();
                    if (savedCert.isBlank())
                        savedCert = null;
                }
            } catch (Exception ignored) {
            }

            boolean certOk = false;
            if (savedCert != null) {
                try {
                    ThriftQrLoginUtil.verifyCertificate(loginQrUrl, qrHeaders, authSessionId, savedCert, tokenCallback);
                    certOk = true;
                    log.info("[loginWithQrCode] Certificate verified.");
                } catch (Exception e) {
                    log.info("[loginWithQrCode] Certificate verification failed ({}), falling back to pin.",
                            e.getMessage());
                }
            }

            if (!certOk) {
                String pinCode = ThriftQrLoginUtil.createPinCode(loginQrUrl, qrHeaders, authSessionId, tokenCallback);
                log.info("[loginWithQrCode] Pin code: {}", pinCode);
                System.out.println("\n>>> PIN CODE: " + pinCode + " (enter this in your LINE app)\n");
                this.onPinCall.accept(pinCode);
                log.info("[loginWithQrCode] Waiting for pin verification (long-poll)...");
                ThriftQrLoginUtil.checkPinCodeVerified(permitNoticeUrl, qrHeaders, authSessionId);
                log.info("[loginWithQrCode] Pin verified.");
            }

            Map<String, String> result = ThriftQrLoginUtil.qrCodeLogin(
                    loginQrUrl, qrHeaders, authSessionId, config.getSystemName(), tokenCallback);
            this.authToken = result.get("accessToken");
            if (this.authToken == null || this.authToken.isBlank())
                throw new RuntimeException("qrCodeLogin returned no accessToken");
            onAuthTokenUpdated.accept(authToken);

            String cert = result.get("certificate");
            if (cert != null && !cert.isBlank()) {
                Files.writeString(Paths.get("certificate.txt"), cert);
                log.info("Certificate saved → certificate.txt");
            }

            loadSession();
            startPolling();
            onReady.accept(myMid);
        } catch (Exception e) {
            throw new RuntimeException("loginWithQrCode failed: " + e.getMessage(), e);
        }
    }

    public void loginWithQrCode() {
        loginWithQrCode(null);
    }

    public void loginWithPassword(String email, String password,
            Consumer<String> onPincodeRequest) {
        try {
            log.info("[loginWithPassword] email={}", email);
            Consumer<String> pinHandler = onPincodeRequest != null ? onPincodeRequest : this.onPinCall;

            String talkUrl = config.getHostDomain() + config.getTalkPath();
            String authUrl = config.getHostDomain() + config.getAuthPath();

            Map<String, String> rsaInfo = ThriftAuthUtil.getRSAKeyInfo(talkUrl, getHeader(), 1, tokenCallback);

            String encryptedCredential = RsaEncryptUtil.rsaEncrypt(
                    rsaInfo.get("sessionKey"), email, password,
                    rsaInfo.get("nvalue"), rsaInfo.get("evalue"));

            System.out.println("[loginWithPassword] rsaInfo=" + rsaInfo);
            System.out.println("[loginWithPassword] encryptedCredential=" + encryptedCredential);
            String deviceType = systemType.split("\t")[0];

            String savedCert = "";
            try {
                java.nio.file.Path certFile = Paths.get("certificate.txt");
                if (Files.exists(certFile)) {
                    savedCert = Files.readString(certFile).strip();
                    log.info("[loginWithPassword] loaded certificate from certificate.txt ({}chars)",
                            savedCert.length());
                }
            } catch (Exception ignored) {
            }

            Map<String, String> result = ThriftAuthUtil.loginZ(
                    authUrl, getHeader(),
                    0, 1, rsaInfo.get("keynm"), encryptedCredential,
                    false, "", deviceType, "", savedCert,
                    tokenCallback);

            if (result.containsKey("authToken") && !result.get("authToken").isBlank()) {
                this.authToken = result.get("authToken");
                onAuthTokenUpdated.accept(authToken);
                String newCert = result.get("certificate");
                if (newCert != null && !newCert.isBlank()) {
                    Files.writeString(Paths.get("certificate.txt"), newCert);
                    log.info("Certificate saved → certificate.txt");
                }
            } else {
                int resultType = 0;
                try {
                    resultType = Integer.parseInt(result.getOrDefault("type", "0"));
                } catch (NumberFormatException ignored) {
                }
                System.out.println("[loginWithPassword] loginZ result=" + result + " resultType=" + resultType);

                if (resultType == 1) {
                    this.authToken = result.getOrDefault("authToken", "");
                    if (!this.authToken.isBlank()) {
                        onAuthTokenUpdated.accept(authToken);
                        return;
                    }
                }
                if (resultType != 3) {
                    throw new RuntimeException("loginZ rejected: type=" + resultType
                            + " msg=" + result.getOrDefault("verifier", "(none)"));
                }
                String verifier = result.get("verifier");
                String pinCode = result.getOrDefault("pinCode", "(check LINE app)");
                pinHandler.accept(pinCode);
                log.info("[loginWithPassword] Pin code dispatched — waiting for user approval in LINE app...");

                if (verifier == null || verifier.isBlank())
                    throw new RuntimeException("loginZ returned no verifier. result=" + result);

                String certUrl = config.getHostDomain() + config.getCertPath();
                String confirmedVerifier = VerifierUtil.fetchVerifierFromQ(certUrl, verifier, userAgent, systemType);
                log.info("[loginWithPassword] Confirmed verifier obtained.");

                Map<String, String> result2 = ThriftAuthUtil.loginZ(
                        authUrl, getHeader(),
                        1, 1, rsaInfo.get("keynm"), encryptedCredential,
                        false, "", deviceType, confirmedVerifier, savedCert,
                        tokenCallback);

                this.authToken = result2.get("authToken");
                if (this.authToken == null || this.authToken.isBlank())
                    throw new RuntimeException("loginZ (step 2) returned no authToken. result2=" + result2);
                onAuthTokenUpdated.accept(authToken);

                String newCert = result2.get("certificate");
                if (newCert != null && !newCert.isBlank()) {
                    Files.writeString(Paths.get("certificate.txt"), newCert);
                    log.info("Certificate saved → certificate.txt");
                }
            }

            loadSession();
            startPolling();
            onReady.accept(myMid);
        } catch (Exception e) {
            throw new RuntimeException("loginWithPassword failed: " + e.getMessage(), e);
        }
    }

    public void loginWithPassword(String email, String password) {
        loginWithPassword(email, password, null);
    }

    public void login() {
        String token = config.getAuthToken();
        String email = config.getLineEmail();
        String password = config.getLinePassword();
        if (token != null && !token.isBlank())
            loginWithAuthToken(token);
        else if (email != null && !email.isBlank()
                && password != null && !password.isBlank())
            loginWithPassword(email, password);
        else
            loginWithQrCode();
    }

    // =========================================================================
    // SESSION
    // =========================================================================

    private void loadSession() throws Exception {
        try {
            String apiUrl = config.getHostDomain() + config.getApiPath();
            this.myMid = ThriftProfileUtil.getProfileMid(apiUrl, getHeader(), tokenCallback);
        } catch (Exception e) {
            log.warn("[loadSession] getProfile failed: {}", e.getMessage());
            this.myMid = null;
        }
        log.info("[loadSession] myMid={}", myMid);

        Map<String, String> thriftHeaders = getHeader();
        log.info("[loadSession] authToken present={}, len={}", authToken != null && !authToken.isBlank(),
                authToken != null ? authToken.length() : 0);
        talkTransport = new THttpClient(config.getHostDomain() + config.getApiPath());
        thriftHeaders.forEach(talkTransport::setCustomHeader);
        talkTransport.open();

        pollTransport = new THttpClient(config.getHostDomain() + config.getPollPath());
        thriftHeaders.forEach(pollTransport::setCustomHeader);
        pollTransport.open();

        String pollUrl = config.getHostDomain() + config.getPollPath();
        this.lastRevision = 0;
        log.info("[loadSession] revision={}", lastRevision);
    }

    // =========================================================================
    // OPERATIONS
    // =========================================================================

    public List<LineOperation> fetchOps(int count) {
        try {
            String pollUrl = config.getHostDomain() + config.getPollPath();
            long[] outRevs = { lastGlobalRev, lastIndividualRev };
            List<LineOperation> ops = ThriftOperationUtil.fetchOperations(
                    pollUrl, getHeader(), lastRevision, count, lastGlobalRev, lastIndividualRev, outRevs,
                    tokenCallback);
            lastGlobalRev = outRevs[0];
            lastIndividualRev = outRevs[1];
            return ops;
        } catch (Exception e) {
            log.debug("fetchOps: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // =========================================================================
    // TALK API
    // =========================================================================

    public void sendMessage(String to, String text) {
        sendMessage(to, text, Collections.emptyMap(), 0);
    }

    public void sendMessage(String to, String text,
            Map<String, String> contentMetadata, int contentType) {
        String apiUrl = config.getHostDomain() + config.getApiPath();
        ThriftMessageUtil.sendMessage(apiUrl, getHeader(), myMid, to, text, contentMetadata, contentType,
                tokenCallback);
    }

    public void kickoutFromGroup(String groupId, List<String> memberMids) {
        try {
            RestApiUtil.doPost(httpClient,
                    config.getHostDomain() + config.getApiPath() + "/kickout",
                    objectMapper.writeValueAsString(
                            Map.of("chatMid", groupId, "targetUserMids", memberMids)),
                    buildRestHeaders());
        } catch (Exception e) {
            log.error("kickoutFromGroup: {}", e.getMessage());
        }
    }

    public List<String> getGroupMemberMids(String groupId) {
        try {
            List<String> mids = new ArrayList<>();
            objectMapper.readTree(
                    RestApiUtil.doGet(httpClient,
                            config.getHostDomain() + config.getApiPath() + "/group/" + groupId,
                            buildRestHeaders()))
                    .path("memberMids").forEach(m -> mids.add(m.asText()));
            return mids;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public String getGroupName(String groupId) {
        try {
            return objectMapper.readTree(
                    RestApiUtil.doGet(httpClient,
                            config.getHostDomain() + config.getApiPath() + "/group/" + groupId,
                            buildRestHeaders()))
                    .path("chatName").asText("Unknown Group");
        } catch (Exception e) {
            return "Unknown Group";
        }
    }

    public String getContactName(String mid) {
        try {
            return objectMapper.readTree(
                    RestApiUtil.doGet(httpClient,
                            config.getHostDomain() + config.getApiPath() + "/contact/" + mid,
                            buildRestHeaders()))
                    .path("displayName").asText("Unknown");
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public void updateGroupChatLink(String groupId, boolean enable) {
        try {
            RestApiUtil.doPost(httpClient,
                    config.getHostDomain() + config.getApiPath() + "/updateChat",
                    objectMapper.writeValueAsString(
                            Map.of("chatMid", groupId, "preventedJoinByTicket", !enable)),
                    buildRestHeaders());
        } catch (Exception e) {
            log.error("updateGroupChatLink: {}", e.getMessage());
        }
    }

    public void startPolling() {
        if (polling)
            return;
        polling = true;
        pollThread = new Thread(() -> {
            String pollUrl = config.getHostDomain() + config.getPollPath();
            log.info("[poll] starting poll loop, revision={}", lastRevision);
            while (polling) {
                try {
                    long[] outRevs = { lastGlobalRev, lastIndividualRev };
                    List<LineOperation> ops = ThriftOperationUtil.fetchOperations(
                            pollUrl, getHeader(), lastRevision, 100,
                            lastGlobalRev, lastIndividualRev, outRevs, tokenCallback);
                    lastGlobalRev = outRevs[0];
                    lastIndividualRev = outRevs[1];

for (LineOperation op : ops) {
    if (op.getRevision() > 0) {
        lastRevision = Math.max(lastRevision, op.getRevision());
    }
    if (op.getType() == 0) continue;
    try {
        handleOperation(op);
    } catch (Exception e) {
        log.error("[poll] handleOperation type={} failed: {}", op.getType(), e.getMessage(), e);
    }
}

                    // Advance lastRevision using whichever stream returned a newer position
                    lastRevision = Math.max(lastRevision, Math.max(lastGlobalRev, lastIndividualRev));

                    long realOps = ops.stream().filter(o -> o.getType() != 0).count();
                    log.debug("[poll] ops={} real={} globalRev={} individualRev={} lastRev={}",
                        ops.size(), realOps, lastGlobalRev, lastIndividualRev, lastRevision);
                    if (realOps == 0) {
                        Thread.sleep(500); // no real ops — throttle
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("[poll] error: {}", e.getMessage());
                    try { Thread.sleep(3000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); break;
                    }
                }
            }
            log.info("[poll] loop stopped");
        }, "line-poll-thread");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    public void stopPolling() {
        polling = false;
        if (pollThread != null)
            pollThread.interrupt();
    }

private void handleOperation(LineOperation op) {
    if (op.getType() == 0) return;
    log.debug("[op] type={} rev={}", op.getType(), op.getRevision());

    if (op.getType() == 26) { // RECEIVE_MESSAGE
        LineOperation.LineMessage msg = op.getMessage();
        if (msg == null) {
            log.warn("[MSG] op type=26 but message is null, rev={}", op.getRevision());
            return;
        }
        log.info("[MSG] ✉ from={} to={} toType={} contentType={} text={} meta={}",
            msg.getFrom(), msg.getTo(),
            (msg.getTo() != null && !msg.getTo().isEmpty() ? msg.getTo().charAt(0) : '?'),
            msg.getContentType(), msg.getText(),
            msg.getContentMetadata());
    } else if (op.getType() == 25) { // SEND_MESSAGE (echo of our own sends)
        LineOperation.LineMessage msg = op.getMessage();
        if (msg != null) {
            log.debug("[SENT_ECHO] to={} text={}", msg.getTo(), msg.getText());
        }
    } else {
        log.debug("[op] unhandled type={}", op.getType());
    }
}

}