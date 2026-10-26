package com.linepyjay.command;

import com.linepyjay.service.LineClientService;
import org.springframework.stereotype.Component;

/** Maps commands/grouplink_on.py */
@Component
public class GroupLinkOnCommand {
    public void execute(LineClientService line, String chatId) {
        try {
            line.updateGroupChatLink(chatId, true);
            line.sendMessage(chatId, "Group link enabled.");
        } catch (Exception e) {
            line.sendMessage(chatId, "Error enabling group link: " + e.getMessage());
        }
    }
}
