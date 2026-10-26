package com.linepyjay.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linepyjay.config.LineConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * AI image generation using Google Gemini — replaces commands/ai.py (OpenAI).
 * Uses the gemini-2.0-flash-exp-image-generation model.
 */
@Service
public class AiImageService {

    private static final Logger log = LoggerFactory.getLogger(AiImageService.class);
    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final LineConfig   config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient   httpClient   = HttpClient.newHttpClient();

    public AiImageService(LineConfig config) {
        this.config = config;
    }

    /**
     * Generate an image from {@code prompt} using Gemini, then send it to LINE.
     * Maps: commands/ai.py :: generate_image()
     */
    public void generate(LineClientService line, String chatId, String prompt) {
        String apiKey = config.getGeminiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            line.sendMessage(chatId, "Gemini API key is not configured.");
            return;
        }

        try {
            String url = GEMINI_API_BASE + config.getGeminiImageModel()
                    + ":generateContent?key=" + apiKey;

            // Build request body
            Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                    "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                    "responseModalities", List.of("TEXT", "IMAGE")
                )
            );

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.warn("Gemini returned HTTP {}: {}", resp.statusCode(), resp.body());
                line.sendMessage(chatId, "Not enough credit or Gemini API error.");
                return;
            }

            // Parse response: find inlineData (base64 image)
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode parts = root.path("candidates").path(0).path("content").path("parts");

            byte[] imageBytes = null;
            String mimeType   = "image/png";

            for (JsonNode part : parts) {
                JsonNode inlineData = part.path("inlineData");
                if (!inlineData.isMissingNode()) {
                    mimeType   = inlineData.path("mimeType").asText("image/png");
                    String b64 = inlineData.path("data").asText();
                    imageBytes = Base64.getDecoder().decode(b64);
                    break;
                }
            }

            if (imageBytes == null) {
                line.sendMessage(chatId, "Gemini did not return an image.");
                return;
            }

            // log.info("Gemini generated image ({}) — {} bytes. Sending to chat {}.", mimeType, imageBytes.length, chatId);
            // line.sendImage(chatId, imageBytes);

        } catch (Exception e) {
            log.error("Gemini image generation failed: {}", e.getMessage());
            line.sendMessage(chatId, "Failed to generate image: " + e.getMessage());
        }
    }
}
