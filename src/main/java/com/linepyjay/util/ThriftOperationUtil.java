package com.linepyjay.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linepyjay.model.LineOperation;
import com.linepyjay.util.ThriftTransport.TokenRefreshCallback;

import org.apache.thrift.TBaseHelper;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.linepyjay.util.ThriftTransport.*;

/**
 * Thrift utility for fetching and parsing LINE operations.
 */
public class ThriftOperationUtil {

    private static final Logger log = LoggerFactory.getLogger(ThriftOperationUtil.class);

    private static void rawDump(org.apache.thrift.protocol.TProtocol proto, int depth) throws Exception {
        String indent = "  ".repeat(depth);
        proto.readStructBegin();
        org.apache.thrift.protocol.TField f;
        while ((f = proto.readFieldBegin()).type != TType.STOP) {
            if (f.type == TType.STRUCT) {
                rawDump(proto, depth + 1);
            } else if (f.type == TType.I64) {
            } else if (f.type == TType.I32) {
            } else if (f.type == TType.STRING) {
            } else if (f.type == TType.SET) {
                var s = proto.readSetBegin();
                proto.readSetEnd();
            } else if (f.type == TType.LIST) {
                var l = proto.readListBegin();
                proto.readListEnd();
            } else {
                org.apache.thrift.protocol.TProtocolUtil.skip(proto, f.type);
            }
            proto.readFieldEnd();
        }
        proto.readStructEnd();
    }

    private static long readZigzagVarint(byte[] bytes, int offset) {
        long result = 0;
        int shift = 0;
        while (offset < bytes.length) {
            byte b = bytes[offset++];
            result |= (long) (b & 0x7F) << shift;
            shift += 7;
            if ((b & 0x80) == 0)
                break;
        }
        // zigzag decode
        return (result >>> 1) ^ -(result & 1);
    }

    /**
     * Calls fetchOperations(localRev, count) via Thrift Compact, returns op list.
     */
    public static List<LineOperation> fetchOperations(String url, Map<String, String> headers,
            long localRev, int count, long globalRev, long individualRev,
            long[] outRevs, TokenRefreshCallback tokenCallback) throws Exception {
        byte[] req = serializeThrift("sync", proto -> {
            proto.writeFieldBegin(new org.apache.thrift.protocol.TField(
                    "request", TType.STRUCT, (short) 1));
            proto.writeStructBegin(new org.apache.thrift.protocol.TStruct("SyncRequest"));
            writeI64Field(proto, "lastRevision", (short) 1, localRev);
            writeI32Field(proto, "count", (short) 2, count);
            writeI64Field(proto, "lastGlobalRevision", (short) 3, globalRev); // track this
            writeI64Field(proto, "lastIndividualRevision", (short) 4, individualRev); // track this
            proto.writeFieldStop();
            proto.writeStructEnd();
            proto.writeFieldEnd();
        });

        byte[] respBytes = thriftPost(url, req, headers, tokenCallback);
if (respBytes.length == 0) return Collections.emptyList();
if (respBytes.length == 21) {
    byte[] body = Arrays.copyOfRange(respBytes, 8, respBytes.length);
    long nextRev = readZigzagVarint(body, 7);
    LineOperation sentinel = new LineOperation();
    sentinel.setRevision(nextRev);
    sentinel.setType(0);
    outRevs[0] = nextRev;
    return Collections.singletonList(sentinel);
}
        if (respBytes.length == 0)
            return Collections.emptyList();
        TCompactProtocol proto = deserializeThrift(respBytes);
        List<LineOperation> ops = new ArrayList<>();

        proto.readStructBegin();
        org.apache.thrift.protocol.TField field;
        while ((field = proto.readFieldBegin()).type != TType.STOP) {
            if (field.id == 0 && field.type == TType.STRUCT) {
                // success result wrapper
                proto.readStructBegin();
                org.apache.thrift.protocol.TField f1;

                while ((f1 = proto.readFieldBegin()).type != TType.STOP) {
if (f1.id == 1 && f1.type == TType.STRUCT) {
    // fullSyncResponse
    proto.readStructBegin();
    org.apache.thrift.protocol.TField f2;
    while ((f2 = proto.readFieldBegin()).type != TType.STOP) {
        if (f2.id == 1 && f2.type == TType.LIST) {
            // operations list (currently empty during full sync)
            org.apache.thrift.protocol.TList list = proto.readListBegin();
            for (int i = 0; i < list.size; i++) {
                try {
                    LineOperation op = parseThriftOperation(proto);
                    if (op != null) ops.add(op);
                } catch (Exception e) {
                    log.warn("[parseOp] LIST op[{}] failed: {}", i, e.getMessage());
                }
            }
            proto.readListEnd();

        } else if (f2.id == 3 && f2.type == TType.STRUCT) {
            // contains lastRevision at field id=2
            proto.readStructBegin();
            org.apache.thrift.protocol.TField f3;
            while ((f3 = proto.readFieldBegin()).type != TType.STOP) {
                if (f3.id == 2 && f3.type == TType.I64) {
                    long nextRev = proto.readI64();
                    outRevs[0] = nextRev; // use as lastRevision
                    LineOperation sentinel = new LineOperation();
                    sentinel.setRevision(nextRev);
                    sentinel.setType(0);
                    ops.add(sentinel);
                } else {
                    org.apache.thrift.protocol.TProtocolUtil.skip(proto, f3.type);
                }
                proto.readFieldEnd();
            }
            proto.readStructEnd();

        } else if (f2.id == 4 && f2.type == TType.STRUCT) {
            // contains globalRev/individualRev at field id=2
            proto.readStructBegin();
            org.apache.thrift.protocol.TField f3;
            while ((f3 = proto.readFieldBegin()).type != TType.STOP) {
                if (f3.id == 2 && f3.type == TType.I64) {
                    long rev = proto.readI64();
                    outRevs[1] = rev;
                } else {
                    org.apache.thrift.protocol.TProtocolUtil.skip(proto, f3.type);
                }
                proto.readFieldEnd();
            }
            proto.readStructEnd();

        } else {
            org.apache.thrift.protocol.TProtocolUtil.skip(proto, f2.type);
        }
        proto.readFieldEnd();
    }
    proto.readStructEnd();
}
                    else if (f1.id == 2 && f1.type == TType.STRUCT) {
                        // operationResponse
                        proto.readStructBegin();
                        org.apache.thrift.protocol.TField f2;
                        while ((f2 = proto.readFieldBegin()).type != TType.STOP) {

                            if (f2.id == 1 && f2.type == TType.SET) {
                                // SET of operations — elemType comes from wire
                                org.apache.thrift.protocol.TSet set = proto.readSetBegin();
                                for (int i = 0; i < set.size; i++) {
                                    try {
                                        LineOperation op = parseThriftOperation(proto);
                                        if (op != null)
                                            ops.add(op);
                                    } catch (Exception e) {
                                        log.warn("[parseOp] SET op[{}] failed: {}", i, e.getMessage());
                                    }
                                }
                                proto.readSetEnd();

                            } else if (f2.id == 2 && f2.type == TType.I64) {
                                outRevs[0] = proto.readI64(); // globalRev — MUST store, not discard
                            } else if (f2.id == 3 && f2.type == TType.I64) {
                                outRevs[1] = proto.readI64(); // individualRev — MUST store, not discard
                            } else {
                                org.apache.thrift.protocol.TProtocolUtil.skip(proto, f2.type);
                            }
                            proto.readFieldEnd();
                        }
                        proto.readStructEnd();

                    } else {
                        org.apache.thrift.protocol.TProtocolUtil.skip(proto, f1.type);
                    }
                    proto.readFieldEnd();
                }
                proto.readStructEnd();

            } else if (field.id == 1 && field.type == TType.STRING) {
                log.error("[sync] exception: {}", proto.readString());
            } else if (field.id == 2 && field.type == TType.I32) {
                log.error("[sync] exception type: {}", proto.readI32());
            } else {
                org.apache.thrift.protocol.TProtocolUtil.skip(proto, field.type);
            }
            proto.readFieldEnd();
        }
        proto.readStructEnd();
        return ops;
    }

    public static LineOperation parseThriftOperation(org.apache.thrift.protocol.TProtocol proto) throws Exception {
        LineOperation op = new LineOperation();
        proto.readStructBegin();
        org.apache.thrift.protocol.TField f;
        while ((f = proto.readFieldBegin()).type != TType.STOP) {
            switch (f.id) {
                case 1 -> {
                    if (f.type == TType.I64) op.setRevision(proto.readI64());
                    else skipType(proto, f.type);
                }
                case 3 -> {
                    if (f.type == TType.I32) op.setType(proto.readI32());
                    else skipType(proto, f.type);
                }
                case 20 -> {
                    if (f.type == TType.STRUCT) {
                        proto.readStructBegin();
                        LineOperation.LineMessage msg = new LineOperation.LineMessage();
                        org.apache.thrift.protocol.TField mf;
                        while ((mf = proto.readFieldBegin()).type != TType.STOP) {
                            switch (mf.id) {
                                case 1 -> { if (mf.type == TType.STRING) msg.setFrom(proto.readString()); else skipType(proto, mf.type); }
                                case 2 -> { if (mf.type == TType.STRING) msg.setTo(proto.readString()); else skipType(proto, mf.type); }
                                case 4 -> { if (mf.type == TType.STRING) msg.setId(proto.readString()); else skipType(proto, mf.type); }
                                case 10 -> { if (mf.type == TType.STRING) msg.setText(proto.readString()); else skipType(proto, mf.type); }
                                case 15 -> { if (mf.type == TType.I32) msg.setContentType(proto.readI32()); else skipType(proto, mf.type); }
                                case 18 -> {
                                    if (mf.type == TType.MAP) {
                                        org.apache.thrift.protocol.TMap m = proto.readMapBegin();
                                        Map<String, String> meta = new HashMap<>();
                                        for (int i = 0; i < m.size; i++) {
                                            String k = proto.readString();
                                            String v = proto.readString();
                                            meta.put(k, v);
                                        }
                                        proto.readMapEnd();
                                        msg.setContentMetadata(meta);
                                    } else {
                                        skipType(proto, mf.type);
                                    }
                                }
                                default -> skipType(proto, mf.type);
                            }
                            proto.readFieldEnd();
                        }
                        proto.readStructEnd();
                        op.setMessage(msg);
                    } else {
                        skipType(proto, f.type);
                    }
                }
                default -> skipType(proto, f.type);
            }
            proto.readFieldEnd();
        }
        proto.readStructEnd();
        return op;
    }

private static void skipType(org.apache.thrift.protocol.TProtocol proto, byte type) throws Exception {
    switch (type) {
        case TType.BOOL   -> proto.readBool();
        case TType.BYTE   -> proto.readByte();
        case TType.I16    -> proto.readI16();
        case TType.I32    -> proto.readI32();
        case TType.I64    -> proto.readI64();
        case TType.DOUBLE -> proto.readDouble();
        case TType.STRING -> proto.readString();
        case TType.STRUCT -> {
            proto.readStructBegin();
            org.apache.thrift.protocol.TField sf;
            while ((sf = proto.readFieldBegin()).type != TType.STOP) {
                skipType(proto, sf.type);
                proto.readFieldEnd();
            }
            proto.readStructEnd();
        }
        case TType.MAP -> {
            org.apache.thrift.protocol.TMap m = proto.readMapBegin();
            for (int i = 0; i < m.size; i++) {
                skipType(proto, m.keyType);
                skipType(proto, m.valueType);
            }
            proto.readMapEnd();
        }
        case TType.SET -> {
            org.apache.thrift.protocol.TSet s = proto.readSetBegin();
            for (int i = 0; i < s.size; i++) skipType(proto, s.elemType);
            proto.readSetEnd();
        }
        case TType.LIST -> {
            org.apache.thrift.protocol.TList l = proto.readListBegin();
            for (int i = 0; i < l.size; i++) skipType(proto, l.elemType);
            proto.readListEnd();
        }
        default -> throw new Exception("don't know what type: " + type);
    }
}

    /**
     * Fetches operations with count=1 to determine the last revision number.
     */
    public static long fetchLastRevision(String url, Map<String, String> headers,
            TokenRefreshCallback tokenCallback) {
        try {
            long[] outRevs = { 0L, 0L };
            List<LineOperation> ops = fetchOperations(url, headers, 0, 1, 0L, 0L, outRevs, tokenCallback);
            if (!ops.isEmpty())
                return ops.get(ops.size() - 1).getRevision();
            return 0L;
        } catch (Exception e) {
            log.warn("[fetchLastRevision] {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Parses operations from a JSON string (REST API format).
     */
    public static List<LineOperation> parseOperations(ObjectMapper objectMapper, String json) {
        List<LineOperation> ops = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.isArray() ? root : root.path("operations");
            for (JsonNode on : arr) {
                LineOperation op = new LineOperation();
                op.setType(on.path("type").asInt());
                op.setRevision(on.path("revision").asLong());
                JsonNode mn = on.path("message");
                if (!mn.isMissingNode()) {
                    LineOperation.LineMessage msg = new LineOperation.LineMessage();
                    msg.setId(mn.path("id").asText(""));
                    msg.setTo(mn.path("to").asText(""));
                    msg.setFrom(mn.path("from").asText(""));
                    msg.setText(mn.path("text").asText(null));
                    msg.setContentType(mn.path("contentType").asInt(0));
                    Map<String, String> meta = new HashMap<>();
                    mn.path("contentMetadata").fields()
                            .forEachRemaining(e -> meta.put(e.getKey(), e.getValue().asText()));
                    msg.setContentMetadata(meta);
                    op.setMessage(msg);
                }
                ops.add(op);
            }
        } catch (Exception e) {
            log.debug("parseOperations: {}", e.getMessage());
        }
        return ops;
    }
}
