package com.linepyjay.service;

import com.linepyjay.command.*;
import com.linepyjay.model.LineOperation;
import com.linepyjay.model.LineOperation.LineMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * Central message dispatcher — maps main.py :: message_handler().
 * Routes each incoming message to the appropriate command.
 */
@Service
public class MessageHandlerService {

    private static final Logger log = LoggerFactory.getLogger(MessageHandlerService.class);

    private final LineClientService    line;
    private final QuizService          quiz;
    private final AiImageService       aiImage;
    private final WhatsAppService      whatsApp;
    private final MediaService         media;

    // Commands
    private final HelloCommand         hello;
    private final HelpCommand          help;
    private final KickCommand          kick;
    private final KickAllCommand       kickAll;
    private final GroupLinkOnCommand   groupLinkOn;
    private final GroupLinkOffCommand  groupLinkOff;
    private final ResponseTimeCommand  responseTime;
    private final GroupBroadcastCommand groupBroadcast;
    private final MentionCommand       mention;

    public MessageHandlerService(
            LineClientService line, QuizService quiz, AiImageService aiImage,
            WhatsAppService whatsApp, MediaService media,
            HelloCommand hello, HelpCommand help, KickCommand kick,
            KickAllCommand kickAll, GroupLinkOnCommand groupLinkOn,
            GroupLinkOffCommand groupLinkOff, ResponseTimeCommand responseTime,
            GroupBroadcastCommand groupBroadcast, MentionCommand mention) {
        this.line = line; this.quiz = quiz; this.aiImage = aiImage;
        this.whatsApp = whatsApp; this.media = media;
        this.hello = hello; this.help = help; this.kick = kick;
        this.kickAll = kickAll; this.groupLinkOn = groupLinkOn;
        this.groupLinkOff = groupLinkOff; this.responseTime = responseTime;
        this.groupBroadcast = groupBroadcast; this.mention = mention;
    }

    public void handle(LineOperation op) {
        LineMessage msg = op.getMessage();
        if (msg == null) return;

        String text     = msg.getText();
        if (text == null) return;

        String chatId   = msg.getTo();
        String senderId = msg.getFrom();

        try {
            dispatch(msg, text, chatId, senderId);
        } catch (Exception e) {
            log.error("Error handling message '{}': {}", text, e.getMessage());
        }
    }

    private void dispatch(LineMessage msg, String text, String chatId, String senderId) {
        // ── Video link ──────────────────────────────────────────────────────
        if (text.contains("instagram.com") || text.contains("youtube.com") || text.contains("youtu.be")) {
            log.info("Detected video link: {}", text);
            media.downloadAndUpload(line, chatId, text);

        // ── Basic commands ──────────────────────────────────────────────────
        } else if (text.equals("hi")) {
            hello.execute(line, chatId);

        } else if (text.equals("help")) {
            help.execute(line, chatId);

        } else if (text.startsWith("kick @")) {
            kick.execute(line, msg, text);

        } else if (text.equals("kickall")) {
            kickAll.execute(line, chatId);

        } else if (text.equals("grouplink on")) {
            groupLinkOn.execute(line, chatId);

        } else if (text.equals("grouplink off")) {
            groupLinkOff.execute(line, chatId);

        } else if (text.equals("start quiz")) {
            quiz.startQuiz(line, chatId);

        } else if (text.equals("stop quiz")) {
            quiz.stopQuiz(line, chatId);

        } else if (quiz.isActive()) {
            quiz.checkAnswer(line, text);

        // ── AI image ────────────────────────────────────────────────────────
        } else if (text.startsWith("generate image ")) {
            String prompt = text.replace("generate image ", "").strip();
            aiImage.generate(line, chatId, prompt);

        } else if (text.equals("test response")) {
            responseTime.execute(line, chatId);

        } else if (text.equals("set this groupbroadcast")) {
            groupBroadcast.setGroup(line, chatId);

        } else if (text.equals("list sider")) {
            line.sendMessage(chatId, "list sider is not fully supported on DESKTOPWIN client.");

        // ── WhatsApp verification ───────────────────────────────────────────
        } else if (text.startsWith("add whatsapp ")) {
            String newNumber = text.replace("add whatsapp ", "").strip();
            int code = new Random().nextInt(9000) + 1000;
            whatsApp.startVerification(line, chatId, senderId, newNumber, code);

        } else if (whatsApp.hasPendingVerification(senderId)) {
            whatsApp.verifyCode(line, chatId, senderId, text);
        }

        // ── Mention detection (always runs) ─────────────────────────────────
        // mention.process(line, msg, text);
    }
}
