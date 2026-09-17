package com.expensetracker.config;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;

public class FlashHelper {

    public static void flash(HttpSession session, String message, String category) {
        if (session == null) return;
        @SuppressWarnings("unchecked")
        List<List<String>> messages = (List<List<String>>) session.getAttribute(PebbleConfig.FLASH_MESSAGES_KEY);
        if (messages == null) {
            messages = new ArrayList<>();
            session.setAttribute(PebbleConfig.FLASH_MESSAGES_KEY, messages);
        }
        messages.add(List.of(category != null ? category : "info", message));
    }

    public static void flash(HttpSession session, String message) {
        flash(session, message, "info");
    }
}
