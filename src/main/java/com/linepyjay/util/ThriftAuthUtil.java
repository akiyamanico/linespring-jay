package com.linepyjay.util;

import org.apache.thrift.protocol.TType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static com.linepyjay.util.ThriftTransport.*;

/**
 * Thrift utility for password-based authentication (getRSAKeyInfo + loginZ).
 */
public class ThriftAuthUtil {

    private static final Logger log = LoggerFactory.getLogger(ThriftAuthUtil.class);

    /**
     * Calls getRSAKeyInfo via TBinaryProtocol at the talk endpoint.
     */
    public static Map<String, String> getRSAKeyInfo(String talkUrl, Map<String, String> headers,
                                                     int provider,
                                                     TokenRefreshCallback tokenCallback) throws Exception {
        Map<String, String> hdrs = new HashMap<>(headers);
        hdrs.remove("x-line-access");

        byte[] req = serializeThriftBin("getRSAKeyInfo", proto -> {
            proto.writeFieldBegin(new org.apache.thrift.protocol.TField("request", TType.STRUCT, (short) 1));
            proto.writeStructBegin(new org.apache.thrift.protocol.TStruct(""));
            writeI32Field(proto, "provider", (short) 2, provider);
            proto.writeFieldStop(); proto.writeStructEnd();
            proto.writeFieldEnd();
        });
        System.out.println("[getRSAKeyInfo] req hex: " + HexFormat.of().formatHex(req));

        byte[] respBytes = thriftPost(talkUrl, req, hdrs, tokenCallback);
        System.out.println("[getRSAKeyInfo] resp " + respBytes.length + " bytes: "
            + HexFormat.of().formatHex(Arrays.copyOf(respBytes, Math.min(40, respBytes.length))));
        if (respBytes.length == 0)
            throw new RuntimeException("getRSAKeyInfo: 0-byte response — server rejected request");

        org.apache.thrift.protocol.TBinaryProtocol proto = deserializeThriftBin(respBytes);
        proto.readStructBegin();
        Map<String, String> rsaKey = new HashMap<>();
        org.apache.thrift.protocol.TField field;
        while ((field = proto.readFieldBegin()).type != TType.STOP) {
            if (field.id == 0 && field.type == TType.STRUCT) {
                proto.readStructBegin();
                org.apache.thrift.protocol.TField inner;
                while ((inner = proto.readFieldBegin()).type != TType.STOP) {
                    switch (inner.id) {
                        case 1 -> rsaKey.put("keynm",      proto.readString());
                        case 2 -> rsaKey.put("nvalue",     proto.readString());
                        case 3 -> rsaKey.put("evalue",     proto.readString());
                        case 4 -> rsaKey.put("sessionKey", proto.readString());
                        default -> org.apache.thrift.protocol.TProtocolUtil.skip(proto, inner.type);
                    }
                    proto.readFieldEnd();
                }
                proto.readStructEnd();
            } else { org.apache.thrift.protocol.TProtocolUtil.skip(proto, field.type); }
            proto.readFieldEnd();
        }
        proto.readStructEnd();
        System.out.println("[getRSAKeyInfo] ok: keynm=" + rsaKey.get("keynm"));
        return rsaKey;
    }

    /**
     * Calls loginZ on the auth endpoint via TBinaryProtocol.
     */
    public static Map<String, String> loginZ(String authUrl, Map<String, String> headers,
                                              int loginType, int identityProvider,
                                              String identifier, String password,
                                              boolean keepLoggedIn, String accessLocation,
                                              String systemName, String verifier,
                                              String certificate,
                                              TokenRefreshCallback tokenCallback) throws Exception {
        Map<String, String> hdrs = new HashMap<>(headers);
        hdrs.remove("x-line-access");

        final String verifierFinal = verifier;
        byte[] req = serializeThriftBin("loginZ", proto -> {
            proto.writeFieldBegin(new org.apache.thrift.protocol.TField("loginRequest", TType.STRUCT, (short) 2));
            proto.writeStructBegin(new org.apache.thrift.protocol.TStruct(""));
            writeI32Field(proto,  "type",             (short)  1, loginType);
            writeI32Field(proto,  "identityProvider", (short)  2, identityProvider);
            writeStrField(proto,  "identifier",       (short)  3, identifier);
            writeStrField(proto,  "password",         (short)  4, password != null ? password : "");
            writeBoolField(proto, "keepLoggedIn",     (short)  5, keepLoggedIn);
            writeStrField(proto,  "accessLocation",   (short)  6, accessLocation != null ? accessLocation : "");
            writeStrField(proto,  "systemName",       (short)  7, systemName != null ? systemName : "");
            final String certFinal = certificate != null ? certificate : "";
            writeStrField(proto, "certificate",       (short)  8, certFinal);
            writeStrField(proto, "verifier",          (short)  9, verifierFinal != null ? verifierFinal : "");
            writeStrField(proto, "e2eeSecret",        (short) 10, "");
            writeI32Field(proto,  "e2eeVersion",      (short) 11, 1);
            writeStrField(proto,  "systemName2",      (short) 12, "System Product Name");
            proto.writeFieldStop(); proto.writeStructEnd();
            proto.writeFieldEnd();
        });
        System.out.println("[loginZ] req FULL hex: " + HexFormat.of().formatHex(req));

        byte[] respBytes = thriftPost(authUrl, req, hdrs, tokenCallback);
        System.out.println("[loginZ] resp " + respBytes.length + " bytes full hex: " + HexFormat.of().formatHex(respBytes));
        if (respBytes.length == 0)
            throw new RuntimeException("loginZ: 0-byte response — server rejected request");
        org.apache.thrift.protocol.TBinaryProtocol proto = deserializeThriftBin(respBytes);

        proto.readStructBegin();
        Map<String, String> result = new HashMap<>();
        org.apache.thrift.protocol.TField field;
        while ((field = proto.readFieldBegin()).type != TType.STOP) {
            System.out.println("[loginZ] outer field id=" + field.id + " type=" + field.type);
            if (field.id == 0 && field.type == TType.STRUCT) {
                proto.readStructBegin();
                org.apache.thrift.protocol.TField inner;
                while ((inner = proto.readFieldBegin()).type != TType.STOP) {
                    System.out.println("[loginZ]   inner field id=" + inner.id + " type=" + inner.type);
                    switch (inner.id) {
                        case 1 -> result.put("authToken",    proto.readString());
                        case 2 -> result.put("certificate",  proto.readString());
                        case 3 -> result.put("verifier",     proto.readString());
                        case 4 -> result.put("pinCode",      proto.readString());
                        case 5 -> result.put("type",         String.valueOf(proto.readI32()));
                        case 6 -> result.put("lastBind",     String.valueOf(proto.readI64()));
                        default -> org.apache.thrift.protocol.TProtocolUtil.skip(proto, inner.type);
                    }
                    proto.readFieldEnd();
                }
                proto.readStructEnd();
            } else if (field.id == 1 && field.type == TType.STRUCT) {
                proto.readStructBegin();
                int errCode = 0; String errMsg = "";
                org.apache.thrift.protocol.TField ef;
                while ((ef = proto.readFieldBegin()).type != TType.STOP) {
                    switch (ef.id) {
                        case 1 -> errCode = proto.readI32();
                        case 2 -> errMsg  = proto.readString();
                        default -> org.apache.thrift.protocol.TProtocolUtil.skip(proto, ef.type);
                    }
                    proto.readFieldEnd();
                }
                proto.readStructEnd();
                throw new RuntimeException("loginZ TalkException: code=" + errCode + " msg=" + errMsg);
            } else { org.apache.thrift.protocol.TProtocolUtil.skip(proto, field.type); }
            proto.readFieldEnd();
        }
        proto.readStructEnd();
        System.out.println("[loginZ] parsed result=" + result);
        return result;
    }
}
