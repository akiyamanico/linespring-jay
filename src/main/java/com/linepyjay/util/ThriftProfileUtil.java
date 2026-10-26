package com.linepyjay.util;

import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;

/**
 * Thrift utility for retrieving the user's profile MID.
 */
public class ThriftProfileUtil {

    private static final Logger log = LoggerFactory.getLogger(ThriftProfileUtil.class);

    /**
     * Calls getProfile via Thrift Compact and extracts the MID string.
     */
    public static String getProfileMid(String url, Map<String, String> headers,
                                        ThriftTransport.TokenRefreshCallback tokenCallback) throws Exception {
        byte[] req = ThriftTransport.serializeThrift("getProfile", proto -> {
        });
        byte[] respBytes = ThriftTransport.thriftPost(url, req, headers, tokenCallback);
        log.info("[getProfile] resp {} bytes hex:{}",
            respBytes.length, HexFormat.of().formatHex(Arrays.copyOf(respBytes, Math.min(60, respBytes.length))));
        TCompactProtocol proto = ThriftTransport.deserializeThrift(respBytes);
        proto.readStructBegin();
        String mid = null;
        org.apache.thrift.protocol.TField field;
        while ((field = proto.readFieldBegin()).type != TType.STOP) {
            if (field.type == TType.STRUCT) {
                proto.readStructBegin();
                org.apache.thrift.protocol.TField inner;
                while ((inner = proto.readFieldBegin()).type != TType.STOP) {
                    if (inner.id == 1 && inner.type == TType.STRING) mid = proto.readString();
                    else org.apache.thrift.protocol.TProtocolUtil.skip(proto, inner.type);
                    proto.readFieldEnd();
                }
                proto.readStructEnd();
            } else { org.apache.thrift.protocol.TProtocolUtil.skip(proto, field.type); }
            proto.readFieldEnd();
        }
        proto.readStructEnd();
        return mid;
    }
}
