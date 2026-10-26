package com.linepyjay.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
public class QuizService {

    private static final List<Map<String, String>> QUESTIONS = List.of(
        Map.of("question", "kenapa saya jelek?",                         "answer", "ya jelek"),
        Map.of("question", "kenapa saya goblok",                         "answer", "ya goblok"),
        Map.of("question", "ga tau mau isi apa lagi yang penting jalan", "answer", "mager")
    );

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private volatile Map<String, String> currentQuiz = null;
    private ScheduledFuture<?> quizTimer;

    public void startQuiz(LineClientService line, String chatId) {
        if (QUESTIONS.isEmpty()) {
            line.sendMessage(chatId, "ga cukup pertanyaan, tambahin dulu!");
            return;
        }
        Map<String, String> qData = QUESTIONS.get(new Random().nextInt(QUESTIONS.size()));
        line.sendMessage(chatId, "pertanyaannya : " + qData.get("question"));

        currentQuiz = new HashMap<>(Map.of(
            "correct_answer", qData.get("answer"),
            "chat_id",        chatId
        ));

        if (quizTimer != null) quizTimer.cancel(false);
        quizTimer = scheduler.schedule(() -> stopQuiz(line, chatId), 180, TimeUnit.SECONDS);
    }

    public void checkAnswer(LineClientService line, String text) {
        if (currentQuiz == null) return;
        if (text.trim().equalsIgnoreCase(currentQuiz.get("correct_answer"))) {
            line.sendMessage(currentQuiz.get("chat_id"), "pinter! 🎉 lanjut!");
            startQuiz(line, currentQuiz.get("chat_id"));
        }
    }

    public void stopQuiz(LineClientService line, String chatId) {
        if (quizTimer != null) { quizTimer.cancel(false); quizTimer = null; }
        currentQuiz = null;
    }

    public boolean isActive() { return currentQuiz != null; }
}
