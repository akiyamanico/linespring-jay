package com.linepyjay.command;

import com.linepyjay.service.LineClientService;
import org.springframework.stereotype.Component;

/** Maps commands/help.py */
@Component
public class HelpCommand {
    public void execute(LineClientService line, String chatId) {
        try {
            line.sendMessage(chatId,
                "𝐤𝐢𝐜𝐤𝐚𝐥𝐥 - Kicking all members\n\n" +
                "𝐤𝐢𝐜𝐤 @𝐌𝐞𝐦𝐛𝐞𝐫 - Kicking a member\n\n" +
                "𝐬𝐭𝐚𝐫𝐭 𝐪𝐮𝐢𝐳 - Starting a quiz\n\n" +
                "𝐬𝐭𝐨𝐩 𝐪𝐮𝐢𝐳 - Stopping a quiz\n\n" +
                "𝐥𝐢𝐬𝐭 𝐬𝐢𝐝𝐞𝐫 - Seeing the sider list (limited on DESKTOPWIN)\n\n" +
                "𝐠𝐞𝐧𝐞𝐫𝐚𝐭𝐞 𝐢𝐦𝐚𝐠𝐞 *keyword* - Generating a picture with Gemini AI\n\n" +
                "𝐭𝐞𝐬𝐭 𝐫𝐞𝐬𝐩𝐨𝐧𝐬𝐞 - Testing bot response time\n\n" +
                "𝐠𝐫𝐨𝐮𝐩𝐥𝐢𝐧𝐤 𝐨𝐧/𝐨𝐟𝐟 - Enable/disable group invite link\n\n" +
                "𝐬𝐞𝐭 𝐭𝐡𝐢𝐬 𝐠𝐫𝐨𝐮𝐩𝐛𝐫𝐨𝐚𝐝𝐜𝐚𝐬𝐭 - Set this group as broadcast monitor\n\n" +
                "𝐚𝐝𝐝 𝐰𝐡𝐚𝐭𝐬𝐚𝐩𝐩 YourNumber - Register WhatsApp for mention notifications\n\n" +
                "If a media link (YouTube/Instagram) is sent, it will be downloaded and uploaded automatically."
            );
        } catch (Exception ignored) {}
    }
}
