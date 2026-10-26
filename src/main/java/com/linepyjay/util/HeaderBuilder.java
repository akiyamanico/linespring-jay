package com.linepyjay.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds HTTP headers for LINE API requests.
 */
public class HeaderBuilder {

    public static Map<String, String> getHeader(String endpoint, String userAgent,
                                                 String systemType, String authToken, String method) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Host",               endpoint);
        h.put("accept",             "application/x-thrift");
        h.put("user-agent",         userAgent);
        h.put("x-line-application", systemType);
        h.put("content-type",       "application/x-thrift");
        h.put("x-lal",              "ja_JP");
        h.put("x-lpv",              "1");
        h.put("x-lhm",              method);
        h.put("accept-encoding",    "gzip");
        if (authToken != null && !authToken.isBlank()) {
            h.put("x-line-access",  authToken);
        }
        return h;
    }

    public static Map<String, String> getHeader(String endpoint, String userAgent,
                                                 String systemType, String authToken) {
        return getHeader(endpoint, userAgent, systemType, authToken, "POST");
    }

    public static Map<String, String> buildQrHeaders(String endpoint, String userAgent,
                                                      String systemType) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Host",               endpoint);
        h.put("accept",             "application/x-thrift");
        h.put("user-agent",         userAgent);
        h.put("x-line-application", systemType);
        h.put("content-type",       "application/x-thrift");
        h.put("x-lal",              "ja_JP");
        h.put("x-lpv",              "1");
        h.put("x-lhm",              "POST");
        h.put("accept-encoding",    "gzip");
        return h;
    }

    public static Map<String, String> buildRestHeaders(String userAgent, String systemType,
                                                        String authToken) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("user-agent",         userAgent);
        h.put("x-line-application", systemType);
        h.put("x-lal",              "ja_JP");
        h.put("x-lpv",              "1");
        if (authToken != null && !authToken.isBlank()) {
            h.put("x-line-access",  authToken);
        }
        return h;
    }
}
