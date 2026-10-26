package com.linepyjay.command;

import com.linepyjay.service.LineClientService;
import org.springframework.stereotype.Component;

/** Maps commands/response_time.py */
@Component
public class ResponseTimeCommand {
    public void execute(LineClientService line, String chatId) {
        try {
            long start = System.currentTimeMillis();
            line.sendMessage(chatId, "Pinging...");
            long elapsed = System.currentTimeMillis() - start;
            line.sendMessage(chatId, "Response time: " + elapsed + " ms");
        } catch (Exception e) {
            line.sendMessage(chatId, "Error measuring response time: " + e.getMessage());
        }
    }
}
