package com.expensetracker.config;

import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.service.CategorizerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;
import java.util.*;

@ControllerAdvice
public class GlobalModelAdvice {

    private final ExpenseRepository repository;
    private final CategorizerService categorizerService;

    public GlobalModelAdvice(ExpenseRepository repository, CategorizerService categorizerService) {
        this.repository = repository;
        this.categorizerService = categorizerService;
    }

    public static class PythonicMap extends HashMap<String, Object> {
        public PythonicMap() {
            super();
        }

        public PythonicMap(Map<?, ?> m) {
            super();
            if (m != null) {
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    Object val = e.getValue();
                    if (val instanceof Map<?, ?> subMap) {
                        put(String.valueOf(e.getKey()), new PythonicMap(subMap));
                    } else {
                        put(String.valueOf(e.getKey()), val);
                    }
                }
            }
        }

        public Object get(Object key, Object defaultVal) {
            Object v = super.get(key);
            return v != null ? v : defaultVal;
        }
    }

    @ModelAttribute("session")
    public Map<String, Object> injectSession(HttpSession httpSession) {
        PythonicMap map = new PythonicMap();
        if (httpSession != null) {
            Enumeration<String> names = httpSession.getAttributeNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                map.put(name, httpSession.getAttribute(name));
            }
        }
        return map;
    }

    @ModelAttribute("user_avatar")
    public String injectUserAvatar(HttpSession session) {
        if (session != null && session.getAttribute("user_name") != null) {
            String name = String.valueOf(session.getAttribute("user_name")).trim();
            if (!name.isEmpty()) {
                return name.substring(0, 1).toUpperCase();
            }
        }
        return "U";
    }

    @ModelAttribute("user_first_name")
    public String injectUserFirstName(HttpSession session) {
        if (session != null && session.getAttribute("user_name") != null) {
            String name = String.valueOf(session.getAttribute("user_name")).trim();
            if (!name.isEmpty()) {
                return name.split(" ")[0];
            }
        }
        return "User";
    }

    @ModelAttribute("request")
    public Map<String, Object> injectRequest(HttpServletRequest request) {
        Map<String, Object> reqMap = new HashMap<>();
        String uri = request.getRequestURI();
        String endpoint = switch (uri) {
            case "/dashboard" -> "dashboard";
            case "/list", "/expenses" -> "list_expenses";
            case "/budgets" -> "budgets_page";
            case "/decision-studio" -> "decision_studio_page";
            case "/analytics", "/summary" -> "analytics_page";
            case "/predict" -> "predict_page";
            case "/chat" -> "chat_page";
            case "/admin" -> "admin_page";
            case "/profile" -> "profile_page";
            case "/settings" -> "settings_page";
            case "/notifications" -> "notifications_page";
            case "/add" -> "add";
            case "/about" -> "about_page";
            case "/features" -> "features_page";
            case "/contact" -> "contact_page";
            case "/privacy" -> "privacy_page";
            case "/terms" -> "terms_page";
            case "/login" -> "login";
            case "/signup" -> "signup";
            case "/landing", "/" -> "landing";
            default -> uri.replaceFirst("^/", "");
        };
        reqMap.put("endpoint", endpoint);
        reqMap.put("path", uri);
        Map<String, String> args = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                args.put(k, v[0]);
            }
        });
        reqMap.put("args", args);
        return reqMap;
    }

    @ModelAttribute("cat_meta")
    public Map<String, Object> injectCatMeta() {
        return new PythonicMap(categorizerService.getAllMetadata());
    }

    @ModelAttribute("today_date")
    public String injectTodayDate() {
        return LocalDate.now().toString();
    }

    @ModelAttribute("currency")
    public String injectCurrency(HttpSession session) {
        if (session != null && session.getAttribute("user_currency") != null) {
            return String.valueOf(session.getAttribute("user_currency"));
        }
        return "₹";
    }

    @ModelAttribute("unread_notifs_count")
    public int injectUnreadNotifsCount(HttpSession session) {
        if (session != null && session.getAttribute("user_id") != null) {
            try {
                Long uid = Long.valueOf(String.valueOf(session.getAttribute("user_id")));
                return repository.countUnreadNotifications(uid);
            } catch (Exception ignored) {}
        }
        return 0;
    }
}
