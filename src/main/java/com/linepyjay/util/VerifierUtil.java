package com.linepyjay.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Utility for polling the LINE /Q endpoint to confirm a verifier during password login.
 */
public class VerifierUtil {

    private static final Logger log = LoggerFactory.getLogger(VerifierUtil.class);

    /**
     * Polls the certificate endpoint until a confirmed verifier is returned.
     */
    public static String fetchVerifierFromQ(String certUrl, String verifier,
                                             String userAgent, String systemType) throws Exception {
        log.info("[fetchVerifierFromQ] polling {} with verifier={}", certUrl,
            verifier.substring(0, Math.min(20, verifier.length())) + "...");
        ObjectMapper objectMapper = new ObjectMapper();

        for (int attempt = 0; attempt < 60; attempt++) {
            java.net.HttpURLConnection conn =
                (java.net.HttpURLConnection) new URL(certUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(120_000);
            conn.setRequestProperty("accept",             "application/x-thrift");
            conn.setRequestProperty("user-agent",         userAgent);
            conn.setRequestProperty("x-line-application", systemType);
            conn.setRequestProperty("x-line-access",      verifier);
            conn.setRequestProperty("x-lal",              "ja_JP");
            conn.setRequestProperty("x-lpv",              "1");
            conn.setRequestProperty("x-lhm",              "GET");
            conn.setRequestProperty("accept-encoding",    "gzip");

            int status = conn.getResponseCode();
            String body = "";
            java.io.InputStream is = status == 200 ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            log.debug("[fetchVerifierFromQ] attempt={} status={} body={}", attempt, status, body);

            if (!body.isBlank()) {
                JsonNode root = objectMapper.readTree(body);
                String confirmed = root.path("result").path("verifier").asText(null);
                if (confirmed != null && !confirmed.isBlank()) return confirmed;
            }
            if (attempt < 59) Thread.sleep(2_000);
        }
        throw new RuntimeException("/Q returned no verifier after 60 attempts (user did not confirm?)");
    }
}
