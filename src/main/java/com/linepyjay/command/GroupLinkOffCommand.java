package com.linepyjay.command;

import com.linepyjay.service.LineClientService;
import org.springframework.stereotype.Component;

/** Maps commands/grouplink_off.py */
@Component
public class GroupLinkOffCommand {
    public void execute(LineClientService line, String chatId) {
        try {
            line.updateGroupChatLink(chatId, false);
            line.sendMessage(chatId, "Group link disabled.");
        } catch (Exception e) {
            line.sendMessage(chatId, "Error disabling group link: " + e.getMessage());
        }
    }
}
