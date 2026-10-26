package com.linepyjay.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linepyjay.model.LineOperation.LineMessage;
import com.linepyjay.service.LineClientService;
import com.linepyjay.service.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Detects when the bot is @mentioned and sends a WhatsApp notification.
 * Maps: tools/mention.py
 */
@Component
public class MentionCommand {

    // private static final Logger log = LoggerFactory.getLogger(MentionCommand.class);

    // private final WhatsAppService whatsApp;
    // private final ObjectMapper    objectMapper = new ObjectMapper();

    // public MentionCommand(WhatsAppService whatsApp) {
    //     this.whatsApp = whatsApp;
    // }

    // public void process(LineClientService line, LineMessage msg, String text) {
    //     if (msg.getContentType() != 0) return;
    //     if (!text.contains("@")) return;

    //     String mentionMeta = msg.getContentMetadata() != null
    //             ? msg.getContentMetadata().get("MENTION") : null;
    //     if (mentionMeta == null) return;

    //     try {
    //         JsonNode mentionees = objectMapper.readTree(mentionMeta).path("MENTIONEES");
    //         for (JsonNode m : mentionees) {
    //             String mentionedMid = m.path("M").asText("");
    //             if (!mentionedMid.equals(line.getMyMid())) continue;

    //             // Bot was mentioned — notify via WhatsApp
    //             String whatsappNumber = whatsApp.loadNumber();
    //             if (whatsappNumber == null) break;

    //             String groupName   = line.getGroupName(msg.getTo());
    //             String senderName  = line.getContactName(msg.getFrom());
    //             whatsApp.sendWhatsAppMessage(
    //                 "You were mentioned in group: '" + groupName + "'" +
    //                 " with message: '" + text + "'" +
    //                 " by: '" + senderName + "'",
    //                 whatsappNumber
    //             );
    //             break;
    //         }
    //     } catch (Exception e) {
    //         log.debug("Mention processing error: {}", e.getMessage());
    //     }
    // }
}
