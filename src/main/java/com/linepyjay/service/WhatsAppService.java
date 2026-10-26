package com.linepyjay.service;

import com.linepyjay.config.LineConfig;
import com.linepyjay.model.VerificationEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WhatsApp notification and verification service.
 * Maps: social_tools/whatsappMention.py
 */
@Service
public class WhatsAppService {

    private static final Logger log   = LoggerFactory.getLogger(WhatsAppService.class);
    private static final String API_URL = "http://api.textmebot.com/send.php";

    private final LineConfig config;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // sender_id → (whatsappNumber, verificationCode)
    private final ConcurrentHashMap<String, VerificationEntry> pendingVerifications = new ConcurrentHashMap<>();

    public WhatsAppService(LineConfig config) {
        this.config = config;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void startVerification(LineClientService line, String chatId, String senderId, String number, int code) {
        pendingVerifications.put(senderId, new VerificationEntry(number, code));
        String msg = "Your verification code is: " + code + ". Please reply with this code to confirm.";
        sendWhatsAppMessage(msg, number);
        line.sendMessage(chatId, "A verification code has been sent to your WhatsApp number. Please check and reply with the code.");
    }

    public boolean hasPendingVerification(String senderId) {
        return pendingVerifications.containsKey(senderId);
    }

    public void verifyCode(LineClientService line, String chatId, String senderId, String text) {
        VerificationEntry entry = pendingVerifications.get(senderId);
        if (entry == null) return;
        if (text.trim().equals(String.valueOf(entry.getCode()))) {
            saveNumber(entry.getWhatsappNumber());
            line.sendMessage(chatId, "Your WhatsApp number has been verified and saved.");
            pendingVerifications.remove(senderId);
        } else {
            line.sendMessage(chatId, "Invalid verification code. Please try again.");
        }
    }

    public boolean sendWhatsAppMessage(String message, String number) {
        try {
            String form = "recipient=" + encode(number)
                    + "&apikey=" + encode(config.getWhatsappApiKey())
                    + "&text=" + encode(message);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            log.error("sendWhatsAppMessage failed: {}", e.getMessage());
            return false;
        }
    }

    public String loadNumber() {
        try {
            Path p = Path.of(config.getWhatsappNumberFile());
            if (Files.exists(p)) return Files.readString(p).strip();
        } catch (IOException e) {
            log.warn("Could not load WhatsApp number: {}", e.getMessage());
        }
        return null;
    }

    private void saveNumber(String number) {
        try {
            Files.writeString(Path.of(config.getWhatsappNumberFile()), number);
        } catch (IOException e) {
            log.error("Could not save WhatsApp number: {}", e.getMessage());
        }
    }

    private String encode(String s) throws Exception {
        return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8");
    }
}
