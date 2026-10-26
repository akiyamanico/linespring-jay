package com.linepyjay.command;

import com.linepyjay.config.LineConfig;
import com.linepyjay.service.LineClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;

/** Maps commands/group_commands.py */
@Component
public class GroupBroadcastCommand {

    private static final Logger log = LoggerFactory.getLogger(GroupBroadcastCommand.class);

    private final LineConfig config;

    public GroupBroadcastCommand(LineConfig config) {
        this.config = config;
    }

    public void setGroup(LineClientService line, String chatId) {
        try {
            Files.writeString(Path.of(config.getBroadcastFile()), chatId);
            line.sendMessage(chatId, "Group ini telah diset sebagai group broadcast.");
        } catch (IOException e) {
            log.error("Could not save broadcast group: {}", e.getMessage());
        }
    }

    public String getBroadcastGroup() {
        try {
            Path p = Path.of(config.getBroadcastFile());
            if (Files.exists(p)) return Files.readString(p).strip();
        } catch (IOException e) {
            log.warn("Could not load broadcast group: {}", e.getMessage());
        }
        return null;
    }
}
