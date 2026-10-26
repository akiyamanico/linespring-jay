package com.linepyjay.util;

import org.apache.thrift.protocol.TType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;

import static com.linepyjay.util.ThriftTransport.*;

/**
 * Thrift utility for sending LINE messages.
 */
public class ThriftMessageUtil {

    private static final Logger log = LoggerFactory.getLogger(ThriftMessageUtil.class);

    /**
     * Sends a message via Thrift Compact protocol.
     */
    public static void sendMessage(String apiUrl, Map<String, String> headers,
                                    String myMid, String to, String text,
                                    Map<String, String> contentMetadata, int contentType,
                                    TokenRefreshCallback tokenCallback) {
        try {
            int seq = 0;
            byte[] req = serializeThrift("sendMessage", proto -> {
                writeI32Field(proto, "seq", (short) 1, seq);
                proto.writeFieldBegin(new org.apache.thrift.protocol.TField("message", TType.STRUCT, (short) 2));
                proto.writeStructBegin(new org.apache.thrift.protocol.TStruct(""));
                writeStrField(proto, "from",        (short) 1,  myMid != null ? myMid : "");
                writeStrField(proto, "to",          (short) 2,  to);
                // toType field 3: 0=user, 1=room, 2=group, determined from first char of MID
                int toType = switch (to != null && !to.isEmpty() ? to.charAt(0) : 'u') {
                    case 'r' -> 1;
                    case 'c' -> 2;
                    case 's' -> 3;
                    default  -> 0; // 'u' = user
                };
                writeI32Field(proto, "toType", (short) 3, toType);
                if (text != null) {
                    writeStrField(proto, "text",    (short) 10, text);
                }
                writeI32Field(proto, "contentType", (short) 15, contentType);
                if (contentMetadata != null && !contentMetadata.isEmpty()) {
                    proto.writeFieldBegin(new org.apache.thrift.protocol.TField("contentMetadata", TType.MAP, (short) 18));
                    proto.writeMapBegin(new org.apache.thrift.protocol.TMap(TType.STRING, TType.STRING, contentMetadata.size()));
                    for (var entry : contentMetadata.entrySet()) {
                        proto.writeString(entry.getKey());
                        proto.writeString(entry.getValue());
                    }
                    proto.writeMapEnd();
                    proto.writeFieldEnd();
                }
                proto.writeFieldStop(); proto.writeStructEnd();
                proto.writeFieldEnd();
            });
            byte[] respBytes = thriftPost(apiUrl, req, headers, tokenCallback);
            log.info("[SEND] req={} bytes hex:{} resp={} bytes hex:{}",
                req.length, HexFormat.of().formatHex(req),
                respBytes.length,
                HexFormat.of().formatHex(Arrays.copyOf(respBytes, Math.min(80, respBytes.length))));
        } catch (Exception e) { log.error("sendMessage failed: {}", e.getMessage(), e); }
    }
}
