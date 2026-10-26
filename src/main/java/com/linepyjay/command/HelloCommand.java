package com.linepyjay.command;

import com.linepyjay.service.LineClientService;
import org.springframework.stereotype.Component;

/** Maps commands/hello.py */
@Component
public class HelloCommand {
    public void execute(LineClientService line, String chatId) {
        try {
            line.sendMessage(chatId, "Hello World");
        } catch (Exception ignored) {}
    }
}
