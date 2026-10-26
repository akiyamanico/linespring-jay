package com.linepyjay.util;

import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.linepyjay.util.ThriftTransport.*;

/**
 * Thrift utility for QR code login flow operations.
 */
public class ThriftQrLoginUtil {

    private static final Logger log = LoggerFactory.getLogger(ThriftQrLoginUtil.class);

    /**
     * Creates a QR login session, returns the authSessionId.
     */
    public static String createQrSession(String loginQrUrl, Map<String, String> qrHeaders,
                                          TokenRefreshCallback tokenCallback) throws Exception {
        byte[] req = serializeThrift("createSession", proto -> {
            proto.writeFieldBegin(new org.apache.thrift.protocol.TField("request", TType.STRUCT, (short) 1));
            proto.writeStructBegin(new org.apache.thrift.protocol.TStruct(""));
            proto.writeFieldStop(); proto.writeStructEnd();
            proto.writeFieldEnd();
        });

        byte[] respBytes = thriftPost(loginQrUrl, req, qrHeaders, tokenCallback);
        TCompactProtocol proto = deserializeThrift(respBytes);

        proto.readStructBegin();
        String authSessionId = null;
        org.apache.thrift.protocol.TField field;
        while ((field = proto.readFieldBegin()).type != TType.STOP) {
            if (field.id == 0 && field.type == TType.STRUCT) {
                proto.readStructBegin();
                org.apache.thrift.protocol.TField inner;
                while ((inner = proto.readFieldBegin()).type != TType.STOP) {
                    if (inner.id == 1 && inner.type == TType.STRING) authSessionId = proto.readString();
                    else org.apache.thrift.protocol.TProtocolUtil.skip(proto, inner.type);
                    proto.readFieldEnd();
                }
                proto.readStructEnd();
            } else { org.apache.thrift.protocol.TProtocolUtil.skip(proto, field.type); }
            proto.readFieldEnd();
        }
        proto.readStructEnd();
        return authSessionId;
    }

    /**
     * Creates a QR code for the given session, returns the callback URL.
     */
    public static String createQrCode(String loginQrUrl, Map<String, String> qrHeaders,
                                       String authSessionId,
                                       TokenRefreshCallback tokenCallback) throws Exception {
        byte[] req = serializeThrift("createQrCode", proto -> {
            proto.writeFieldBegin(new org.apache.thrift.protocol.TField("request", TType.STRUCT, (short) 1));
            proto.writeStructBegin(new org.apache.thrift.protocol.TStruct(""));
            writeStrField(proto, "authSessionId", (short) 1, authSessionId);
            proto.writeFieldStop(); proto.writeStructEnd();
            proto.writeFieldEnd();
        });

        byte[] respBytes = thriftPost(loginQrUrl, req, qrHeaders, tokenCallback);
        TCompactProtocol proto = deserializeThrift(respBytes);

        proto.readStructBegin();
        String callbackUrl = null;
        org.apache.thrift.protocol.TField field;
        while ((field = proto.readFieldBegin()).type != TType.STOP) {
            if (field.id == 0 && field.type == TType.STRUCT) {
                proto.readStructBegin();
                org.apache.thrift.protocol.TField inner;
                while ((inner = proto.readFieldBegin()).type != TType.STOP) {
                    if (inner.id == 1 && inner.type == TType.STRING) callbackUrl = proto.readString();
                    else org.apache.thrift.protocol.TProtocolUtil.skip(proto, inner.type);
                    proto.readFieldEnd();
                }
                proto.readStructEnd();
            } else { org.apache.thrift.protocol.TProtocolUtil.skip(proto, field.type); }
            proto.readFieldEnd();
        }
        proto.readStructEnd();
        return callbackUrl;
    }

    /**
     * Long-polls until the user has scanned the QR code.
     */
    public static void checkQrCodeVerified(String permitNoticeUrl, Map<String, String> qrHeaders,
                                            String authSessionId) throws Exception {
        Map<String, String> headers = new HashMap<>(qrHeaders);
        headers.put("x-lst",          "180000");
        headers.put("x-line-access",  authSessionId);

        byte[] req = serializeThrift("checkQrCodeVerified", proto -> {
            proto.writeFieldBegin(new org.apache.thrift.protocol.TField("request", TType.STRUCT, (short) 1));
            proto.writeStructBegin(new org.apache.thrift.protocol.TStruct(""));
            writeStrField(proto, "authSessionId", (short) 1, authSessionId);
            proto.writeFieldStop(); proto.writeStructEnd();
            proto.writeFieldEnd();
        });

        HttpURLConnection conn =
            (HttpURLConnection) new URL(permitNoticeUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(200_000);
        headers.forEach(conn::setRequestProperty);
        try (OutputStream os = conn.getOutputStream()) { os.write(req); }

        int status = conn.getResponseCode();
        if (status != 200) {
            java.io.InputStream err = conn.getErrorStream();
            String body = err != null ? new String(err.readAllBytes(), StandardCharsets.UTF_8) : "(no body)";
            throw new RuntimeException("checkQrCodeVerified HTTP " + status + ": " + body);
        }
        conn.getInputStream().readAllBytes();
        log.debug("checkQrCodeVerified returned OK");
    }

    /**
     * Verifies a saved certificate to bypass pin code entry.
     */
    public static void verifyCertificate(String loginQrUrl, Map<String, String> qrHeaders,
                                          String authSessionId, String cert,
                                          TokenRefreshCallback tokenCallback) throws Exception {
        byte[] req = serializeThrift("verifyCertificate", proto -> {
            proto.writeFieldBegin(new org.apache.thrift.protocol.TField("request", TType.STRUCT, (short) 1));
            proto.writeStructBegin(new org.apache.thrift.protocol.TStruct(""));
            writeStrField(proto, "authSessionId", (short) 1, authSessionId);
            if (cert != null && !cert.isBlank())
                writeStrField(proto, "certificate", (short) 2, cert);
            proto.writeFieldStop(); proto.writeStructEnd();
            proto.writeFieldEnd();
        });

        byte[] respBytes = thriftPost(loginQrUrl, req, qrHeaders, tokenCallback);
        TCompactProtocol vproto = deserializeThrift(respBytes);
        vproto.readStructBegin();
        org.apache.thrift.protocol.TField f;
        while ((f = vproto.readFieldBegin()).type != TType.STOP) { org.apache.thrift.protocol.TProtocolUtil.skip(vproto, f.type); vproto.readFieldEnd(); }
        vproto.readStructEnd();
        log.debug("verifyCertificate OK");
    }

    /**
     * Creates a pin code for the QR login session.
     */
    public static String createPinCode(String loginQrUrl, Map<String, String> qrHeaders,
                                        String authSessionId,
                                        TokenRefreshCallback tokenCallback) throws Exception {
        byte[] req = serializeThrift("createPinCode", proto -> {
            proto.writeFieldBegin(new org.apache.thrift.protocol.TField("request", TType.STRUCT, (short) 1));
            proto.writeStructBegin(new org.apache.thrift.protocol.TStruct(""));
            writeStrField(proto, "authSessionId", (short) 1, authSessionId);
            proto.writeFieldStop(); proto.writeStructEnd();
            proto.writeFieldEnd();
        });

        byte[] respBytes = thriftPost(loginQrUrl, req, qrHeaders, tokenCallback);
        TCompactProtocol proto = deserializeThrift(respBytes);

        proto.readStructBegin();
        String pinCode = null;
        org.apache.thrift.protocol.TField field;
        while ((field = proto.readFieldBegin()).type != TType.STOP) {
            if (field.id == 0 && field.type == TType.STRUCT) {
                proto.readStructBegin();
                org.apache.thrift.protocol.TField inner;
                while ((inner = proto.readFieldBegin()).type != TType.STOP) {
                    if (inner.id == 1 && inner.type == TType.STRING) pinCode = proto.readString();
                    else org.apache.thrift.protocol.TProtocolUtil.skip(proto, inner.type);
                    proto.readFieldEnd();
                }
                proto.readStructEnd();
            } else { org.apache.thrift.protocol.TProtocolUtil.skip(proto, field.type); }
            proto.readFieldEnd();
        }
        proto.readStructEnd();
        return pinCode != null ? pinCode : "";
    }

    /**
     * Long-polls until the user confirms the pin in the LINE app.
     */
    public static void checkPinCodeVerified(String permitNoticeUrl, Map<String, String> qrHeaders,
                                             String authSessionId) throws Exception {
        Map<String, String> headers = new HashMap<>(qrHeaders);
        headers.put("x-lst",         "180000");
        headers.put("x-line-access", authSessionId);

        byte[] req = serializeThrift("checkPinCodeVerified", proto -> {
            proto.writeFieldBegin(new org.apache.thrift.protocol.TField("request", TType.STRUCT, (short) 1));
            proto.writeStructBegin(new org.apache.thrift.protocol.TStruct(""));
            writeStrField(proto, "authSessionId", (short) 1, authSessionId);
            proto.writeFieldStop(); proto.writeStructEnd();
            proto.writeFieldEnd();
        });

        HttpURLConnection conn =
            (HttpURLConnection) new URL(permitNoticeUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(200_000);
        headers.forEach(conn::setRequestProperty);
        try (OutputStream os = conn.getOutputStream()) { os.write(req); }

        int status = conn.getResponseCode();
        if (status != 200) {
            java.io.InputStream err = conn.getErrorStream();
            String body = err != null ? new String(err.readAllBytes(), StandardCharsets.UTF_8) : "(no body)";
            throw new RuntimeException("checkPinCodeVerified HTTP " + status + ": " + body);
        }
        conn.getInputStream().readAllBytes();
        log.debug("checkPinCodeVerified returned OK");
    }

    /**
     * Performs the final QR code login, returns a map with accessToken, certificate, etc.
     */
    public static Map<String, String> qrCodeLogin(String loginQrUrl, Map<String, String> qrHeaders,
                                                    String authSessionId, String systemName,
                                                    TokenRefreshCallback tokenCallback) throws Exception {
        byte[] req = serializeThrift("qrCodeLogin", proto -> {
            proto.writeFieldBegin(new org.apache.thrift.protocol.TField("request", TType.STRUCT, (short) 1));
            proto.writeStructBegin(new org.apache.thrift.protocol.TStruct(""));
            writeStrField(proto,  "authSessionId",       (short) 1, authSessionId);
            writeStrField(proto,  "systemName",          (short) 2, systemName);
            writeBoolField(proto, "autoLoginIsRequired", (short) 3, true);
            proto.writeFieldStop(); proto.writeStructEnd();
            proto.writeFieldEnd();
        });

        byte[] respBytes = thriftPost(loginQrUrl, req, qrHeaders, tokenCallback);
        TCompactProtocol proto = deserializeThrift(respBytes);

        proto.readStructBegin();
        Map<String, String> result = new HashMap<>();
        org.apache.thrift.protocol.TField field;
        while ((field = proto.readFieldBegin()).type != TType.STOP) {
            if (field.id == 0 && field.type == TType.STRUCT) {
                proto.readStructBegin();
                org.apache.thrift.protocol.TField inner;
                while ((inner = proto.readFieldBegin()).type != TType.STOP) {
                    switch (inner.id) {
                        case 1 -> result.put("certificate",       proto.readString());
                        case 2 -> result.put("accessToken",       proto.readString());
                        case 3 -> result.put("lastBindTimestamp", String.valueOf(proto.readI64()));
                        default -> org.apache.thrift.protocol.TProtocolUtil.skip(proto, inner.type);
                    }
                    proto.readFieldEnd();
                }
                proto.readStructEnd();
            } else { org.apache.thrift.protocol.TProtocolUtil.skip(proto, field.type); }
            proto.readFieldEnd();
        }
        proto.readStructEnd();
        return result;
    }
}
