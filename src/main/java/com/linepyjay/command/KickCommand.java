package com.linepyjay.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linepyjay.model.LineOperation.LineMessage;
import com.linepyjay.service.LineClientService;
import org.springframework.stereotype.Component;

import java.util.List;

/** Maps commands/kick.py */
@Component
public class KickCommand {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void execute(LineClientService line, LineMessage msg, String text) {
        try {
            String meta = msg.getContentMetadata() != null
                    ? msg.getContentMetadata().get("MENTION") : null;
            if (meta == null) {
                line.sendMessage(msg.getTo(), "Mention dulu");
                return;
            }
            JsonNode mentionees = objectMapper.readTree(meta).path("MENTIONEES");
            if (!mentionees.isEmpty()) {
                String memberMid = mentionees.get(0).path("M").asText();
                line.kickoutFromGroup(msg.getTo(), List.of(memberMid));
            } else {
                line.sendMessage(msg.getTo(), "Bener ga?");
            }
        } catch (Exception e) {
            line.sendMessage(msg.getTo(), "Error: " + e.getMessage());
        }
    }
}
