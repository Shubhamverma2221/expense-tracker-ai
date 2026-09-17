package com.expensetracker.controller;

import com.expensetracker.service.ChatBotService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ChatController {

    private final ChatBotService chatBotService;

    public ChatController(ChatBotService chatBotService) {
        this.chatBotService = chatBotService;
    }

    @GetMapping("/chat")
    public String chatPage(Model model) {
        model.addAttribute("title", "AI Financial Copilot");
        model.addAttribute("user_text", "");
        model.addAttribute("response", null);
        return "chat";
    }

    @PostMapping("/chat")
    public String handleChat(@RequestParam(name = "message", defaultValue = "") String message,
                             HttpSession session,
                             Model model) {
        Long uid = (Long) session.getAttribute("user_id");
        String curr = session.getAttribute("user_currency") != null ? String.valueOf(session.getAttribute("user_currency")) : "₹";

        String trimmed = message.trim();
        String response = null;
        if (!trimmed.isBlank()) {
            response = chatBotService.respond(trimmed, uid, curr);
        }

        model.addAttribute("title", "AI Financial Copilot");
        model.addAttribute("user_text", trimmed);
        model.addAttribute("response", response);

        return "chat";
    }
}
