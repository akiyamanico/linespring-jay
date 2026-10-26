package com.linepyjay.command;

import com.linepyjay.service.LineClientService;
import org.springframework.stereotype.Component;

import java.util.List;

/** Maps commands/kickall.py */
@Component
public class KickAllCommand {
    public void execute(LineClientService line, String chatId) {
        try {
            List<String> members = line.getGroupMemberMids(chatId);
            if (members.isEmpty()) {
                line.sendMessage(chatId, "No members found.");
                return;
            }
            line.sendMessage(chatId, "Kicking " + members.size() + " members...");
            for (String mid : members) {
                line.kickoutFromGroup(chatId, List.of(mid));
                Thread.sleep(300); // brief delay to avoid rate limiting
            }
        } catch (Exception e) {
            line.sendMessage(chatId, "Error during kickall: " + e.getMessage());
        }
    }
}
