package com.linepyjay.util;

import org.apache.thrift.TConfiguration;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TMemoryBuffer;
import org.apache.thrift.transport.TMemoryInputTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Core Thrift serialization/deserialization and HTTP transport utilities.
 */
public class ThriftTransport {

    private static final Logger log = LoggerFactory.getLogger(ThriftTransport.class);

    /**
     * Functional interface for writing Thrift struct fields.
     */
    @FunctionalInterface
    public interface ThriftWriter {
        void write(org.apache.thrift.protocol.TProtocol p) throws Exception;
    }

    /**
     * Callback interface for token refresh notifications from thriftPost.
     */
    @FunctionalInterface
    public interface TokenRefreshCallback {
        void onTokenRefreshed(String newToken);
    }

    // =========================================================================
    // COMPACT PROTOCOL
    // =========================================================================

    /**
     * Builds a LINE-compatible Thrift Compact request.
     * Format: [0x82, 0x21, 0x00, nameLen, ...nameBytes, ...structBody, 0x00]
     */
    public static byte[] serializeThrift(String methodName, ThriftWriter w) throws Exception {
        TMemoryBuffer buf = new TMemoryBuffer(512);
        TCompactProtocol proto = new TCompactProtocol(buf);
        proto.writeStructBegin(new org.apache.thrift.protocol.TStruct(""));
        w.write(proto);
        proto.writeFieldStop();
        proto.writeStructEnd();
        byte[] body = Arrays.copyOf(buf.getArray(), buf.length());

        byte[] nameBytes = methodName.getBytes(StandardCharsets.UTF_8);
        int totalLen = 3 + 1 + nameBytes.length + body.length + 1;
        byte[] msg = new byte[totalLen];
        msg[0] = (byte) 0x82;
        msg[1] = (byte) 0x21;
        msg[2] = (byte) 0x00;
        msg[3] = (byte) nameBytes.length;
        System.arraycopy(nameBytes, 0, msg, 4, nameBytes.length);
        System.arraycopy(body, 0, msg, 4 + nameBytes.length, body.length);
        msg[totalLen - 1] = 0x00;
        return msg;
    }

    /**
     * Skips the compact response header then returns a protocol over the struct
     * body.
     */
public static TCompactProtocol deserializeThrift(byte[] bytes) {
    try {
        if (bytes.length < 4) {
            throw new RuntimeException(
                "Thrift response too short (" + bytes.length + " bytes)");
        }

        int nameLen = bytes[3] & 0xFF;
        int offset = 4 + nameLen;
        byte[] body = Arrays.copyOfRange(bytes, offset, bytes.length);

        // ✅ Create configuration directly (NOT Builder)
        TConfiguration config = new TConfiguration();

        // Set custom limits
        config.setMaxMessageSize(Integer.MAX_VALUE);   // or custom size
        config.setMaxFrameSize(Integer.MAX_VALUE);     // or custom size

        // Pass config into transport
        TMemoryInputTransport transport =
                new TMemoryInputTransport(config, body, 0, body.length);

        return new TCompactProtocol(transport);

    } catch (RuntimeException e) {
        throw e;
    } catch (Exception e) {
        throw new RuntimeException("Deserialize failed", e);
    }
}

    // =========================================================================
    // BINARY PROTOCOL
    // =========================================================================

    /**
     * Binary-protocol serializer matching linejs-patch genHeader[3].
     */
    public static byte[] serializeThriftBin(String methodName, ThriftWriter w) throws Exception {
        TMemoryBuffer buf = new TMemoryBuffer(512);
        org.apache.thrift.protocol.TBinaryProtocol proto = new org.apache.thrift.protocol.TBinaryProtocol(buf);
        proto.writeStructBegin(new org.apache.thrift.protocol.TStruct(""));
        w.write(proto);
        proto.writeFieldStop();
        proto.writeStructEnd();
        byte[] body = Arrays.copyOf(buf.getArray(), buf.length());

        byte[] nameBytes = methodName.getBytes(StandardCharsets.UTF_8);
        byte[] msg = new byte[4 + 4 + nameBytes.length + 4 + body.length];
        msg[0] = (byte) 0x80;
        msg[1] = (byte) 0x01;
        msg[2] = (byte) 0x00;
        msg[3] = (byte) 0x01;
        msg[4] = 0;
        msg[5] = 0;
        msg[6] = 0;
        msg[7] = (byte) nameBytes.length;
        System.arraycopy(nameBytes, 0, msg, 8, nameBytes.length);
        msg[8 + nameBytes.length] = 0;
        msg[8 + nameBytes.length + 1] = 0;
        msg[8 + nameBytes.length + 2] = 0;
        msg[8 + nameBytes.length + 3] = 0;
        System.arraycopy(body, 0, msg, 12 + nameBytes.length, body.length);
        return msg;
    }

    /**
     * Deserializes a TBinaryProtocol response.
     */
    public static org.apache.thrift.protocol.TBinaryProtocol deserializeThriftBin(byte[] bytes) {
        if (bytes.length < 8)
            throw new RuntimeException(
                    "Binary response too short (" + bytes.length + " bytes)");
        int nameLen = ((bytes[4] & 0xFF) << 24) | ((bytes[5] & 0xFF) << 16)
                | ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
        int offset = 8 + nameLen + 4;
        if (offset > bytes.length)
            offset = 8 + nameLen;
        byte[] body = Arrays.copyOfRange(bytes, offset, bytes.length);
        try {
            return new org.apache.thrift.protocol.TBinaryProtocol(
                    new TMemoryInputTransport(body));
        } catch (Exception e) {
            throw new RuntimeException("Binary deserialize failed", e);
        }
    }

    // =========================================================================
    // HTTP POST
    // =========================================================================

    /**
     * Posts a Thrift payload and returns the response bytes.
     * If the server returns an x-line-next-access header, the callback is invoked.
     */
    public static byte[] thriftPost(String url, byte[] body,
            Map<String, String> headers,
            TokenRefreshCallback tokenCallback) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(60_000);
        headers.forEach(conn::setRequestProperty);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }

        int status = conn.getResponseCode();

        String nextToken = conn.getHeaderField("x-line-next-access");
        if (nextToken != null && !nextToken.isBlank() && tokenCallback != null) {
            log.info("[update:authtoken] Refreshed via x-line-next-access");
            tokenCallback.onTokenRefreshed(nextToken);
        }

        if (status != 200) {
            InputStream err = conn.getErrorStream();
            String errBody = err != null
                    ? new String(err.readAllBytes(), StandardCharsets.UTF_8)
                    : "(no body)";
            log.error("Thrift HTTP {} body: {}", status, errBody);
            throw new RuntimeException("HTTP " + status + " from " + url + " — " + errBody);
        }

        return conn.getInputStream().readAllBytes();
    }

    // =========================================================================
    // FIELD WRITERS
    // =========================================================================

    public static void writeI32Field(org.apache.thrift.protocol.TProtocol p, String n, short id, int v)
            throws Exception {
        p.writeFieldBegin(new org.apache.thrift.protocol.TField(n, TType.I32, id));
        p.writeI32(v);
        p.writeFieldEnd();
    }

    public static void writeStrField(org.apache.thrift.protocol.TProtocol p, String n, short id, String v)
            throws Exception {
        p.writeFieldBegin(new org.apache.thrift.protocol.TField(n, TType.STRING, id));
        p.writeString(v != null ? v : "");
        p.writeFieldEnd();
    }

    public static void writeBoolField(org.apache.thrift.protocol.TProtocol p, String n, short id, boolean v)
            throws Exception {
        p.writeFieldBegin(new org.apache.thrift.protocol.TField(n, TType.BOOL, id));
        p.writeBool(v);
        p.writeFieldEnd();
    }

    public static void writeI64Field(org.apache.thrift.protocol.TProtocol p, String n, short id, long v)
            throws Exception {
        p.writeFieldBegin(new org.apache.thrift.protocol.TField(n, TType.I64, id));
        p.writeI64(v);
        p.writeFieldEnd();
    }
}
