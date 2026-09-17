package com.expensetracker.controller;

import com.expensetracker.config.FlashHelper;
import com.expensetracker.repository.ExpenseRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PublicPagesController {

    private final ExpenseRepository repository;

    public PublicPagesController(ExpenseRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/about")
    public String aboutPage(Model model) {
        model.addAttribute("title", "About & Portfolio");
        return "about";
    }

    @GetMapping("/features")
    public String featuresPage(Model model) {
        model.addAttribute("title", "Features Tour");
        return "features";
    }

    @GetMapping("/contact")
    public String contactPage(Model model) {
        model.addAttribute("title", "Contact & Support");
        return "contact";
    }

    @PostMapping("/contact")
    public String handleContact(@RequestParam(name = "name", defaultValue = "") String name,
                                @RequestParam(name = "email", defaultValue = "") String email,
                                @RequestParam(name = "subject", defaultValue = "") String subject,
                                @RequestParam(name = "message", defaultValue = "") String message,
                                HttpSession session) {
        if (!name.isBlank() && !email.isBlank() && !message.isBlank()) {
            repository.saveContactMessage(name.trim(), email.trim(), subject.trim(), message.trim());
            FlashHelper.flash(session, "Thank you for reaching out! Your message has been received.", "success");
            return "redirect:/contact";
        } else {
            FlashHelper.flash(session, "Please complete all required contact fields.", "danger");
            return "redirect:/contact";
        }
    }

    @GetMapping("/privacy")
    public String privacyPage(Model model) {
        model.addAttribute("title", "Privacy Policy");
        return "privacy";
    }

    @GetMapping("/terms")
    public String termsPage(Model model) {
        model.addAttribute("title", "Terms & Conditions");
        return "terms";
    }

    @GetMapping(value = {"/ping", "/health"}, produces = "application/json")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> ping() {
        return java.util.Map.of(
                "status", "UP",
                "service", "ExpenseAI",
                "timestamp", System.currentTimeMillis()
        );
    }
}
