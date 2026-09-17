package com.expensetracker.config;

import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import io.pebbletemplates.pebble.loader.Loader;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pebbletemplates.pebble.extension.escaper.SafeString;

import java.io.Reader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Configuration
public class PebbleConfig {

    public static final String FLASH_MESSAGES_KEY = "FLASH_MESSAGES";

    @Bean
    public Loader<?> pebbleLoader() {
        ClasspathLoader loader = new ClasspathLoader() {
            @Override
            public Reader getReader(String templateName) {
                if (templateName == null) return null;
                String clean = templateName.replace('\\', '/');
                while (clean.startsWith("classpath:")) {
                    clean = clean.substring("classpath:".length());
                }
                while (clean.startsWith("/")) {
                    clean = clean.substring(1);
                }
                if (clean.startsWith("templates/")) {
                    clean = clean.substring("templates/".length());
                }
                if (clean.endsWith(".html.html")) {
                    clean = clean.substring(0, clean.length() - 5);
                }
                if (!clean.endsWith(".html")) {
                    clean = clean + ".html";
                }
                return super.getReader(clean);
            }
        };
        loader.setPrefix("templates/");
        loader.setSuffix("");
        return loader;
    }

    @Bean
    public AbstractExtension expenseTrackerPebbleExtension() {
        return new AbstractExtension() {
            @Override
            public Map<String, Filter> getFilters() {
                Map<String, Filter> filters = new HashMap<>();

                // Format filter: supports {{ "%.2f" | format(val) }} or {{ val | format("%.2f") }}
                filters.put("format", new Filter() {
                    @Override
                    public List<String> getArgumentNames() {
                        return List.of("arg");
                    }

                    @Override
                    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
                        Object arg = args.get("arg");
                        try {
                            if (input instanceof String str && str.contains("%")) {
                                return String.format(str, arg);
                            } else if (arg instanceof String pattern && pattern.contains("%")) {
                                return String.format(pattern, input);
                            } else if (input instanceof Number num) {
                                return String.format(Locale.US, "%.2f", num.doubleValue());
                            }
                        } catch (Exception ignored) {}
                        return input != null ? input.toString() : "";
                    }
                });

                // Top category filter for maps
                filters.put("top_category", new Filter() {
                    @Override
                    public List<String> getArgumentNames() {
                        return List.of("defaultVal");
                    }

                    @Override
                    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
                        String defaultVal = args.containsKey("defaultVal") ? String.valueOf(args.get("defaultVal")) : "None";
                        if (input instanceof Map<?, ?> map && !map.isEmpty()) {
                            return map.entrySet().stream()
                                    .filter(e -> e.getValue() instanceof Number)
                                    .max(Comparator.comparingDouble(e -> ((Number) e.getValue()).doubleValue()))
                                    .map(e -> String.valueOf(e.getKey()))
                                    .orElse(defaultVal);
                        }
                        return defaultVal;
                    }
                });

                // Slice filter for strings and lists
                filters.put("slice", new Filter() {
                    @Override
                    public List<String> getArgumentNames() {
                        return Arrays.asList("start", "end");
                    }

                    @Override
                    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
                        if (input == null) return "";
                        int start = 0;
                        if (args.get("start") != null) {
                            start = ((Number) args.get("start")).intValue();
                        }

                        if (input instanceof String str) {
                            int len = str.length();
                            int s = Math.max(0, Math.min(start, len));
                            int e = len;
                            if (args.get("end") != null) {
                                e = Math.min(len, Math.max(s, ((Number) args.get("end")).intValue()));
                            }
                            return str.substring(s, e);
                        } else if (input instanceof List<?> list) {
                            int len = list.size();
                            int s = Math.max(0, Math.min(start, len));
                            int e = len;
                            if (args.get("end") != null) {
                                e = Math.min(len, Math.max(s, ((Number) args.get("end")).intValue()));
                            }
                            return list.subList(s, e);
                        }
                        return input;
                    }
                });

                // Safe filter matching Jinja2 |safe
                filters.put("safe", new Filter() {
                    @Override
                    public List<String> getArgumentNames() {
                        return Collections.emptyList();
                    }

                    @Override
                    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
                        return new SafeString(input != null ? input.toString() : "");
                    }
                });

                // Tojson filter matching Jinja2 |tojson
                filters.put("tojson", new Filter() {
                    private final ObjectMapper mapper = new ObjectMapper();

                    @Override
                    public List<String> getArgumentNames() {
                        return Collections.emptyList();
                    }

                    @Override
                    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
                        try {
                            return new SafeString(mapper.writeValueAsString(input != null ? input : Collections.emptyList()));
                        } catch (Exception e) {
                            return new SafeString("[]");
                        }
                    }
                });

                // Replace filter matching Jinja2 |replace(old, new)
                filters.put("replace", new Filter() {
                    @Override
                    public List<String> getArgumentNames() {
                        return Arrays.asList("search", "replacement");
                    }

                    @Override
                    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
                        if (input == null) return "";
                        String str = input.toString();
                        String search = args.containsKey("search") ? String.valueOf(args.get("search")) :
                                (args.containsKey("0") ? String.valueOf(args.get("0")) : "");
                        String replacement = args.containsKey("replacement") ? String.valueOf(args.get("replacement")) :
                                (args.containsKey("1") ? String.valueOf(args.get("1")) : "");
                        return str.replace(search, replacement);
                    }
                });

                java.util.function.Function<Object, Double> toNum = o -> {
                    if (o instanceof Number n) return n.doubleValue();
                    try {
                        return Double.parseDouble(String.valueOf(o));
                    } catch (Exception e) {
                        return 0.0;
                    }
                };

                // Min filter
                filters.put("min", new Filter() {
                    @Override
                    public List<String> getArgumentNames() {
                        return List.of("other");
                    }

                    @Override
                    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
                        if (input instanceof List<?> list && !list.isEmpty()) {
                            Optional<Double> opt = list.stream()
                                    .filter(Objects::nonNull)
                                    .map(toNum)
                                    .min(Double::compare);
                            if (opt.isPresent()) {
                                double d = opt.get();
                                return d == Math.floor(d) ? (long) d : d;
                            }
                            return 0L;
                        }
                        Object otherVal = args.containsKey("other") ? args.get("other") : args.get("0");
                        if (input != null && otherVal != null) {
                            double m = Math.min(toNum.apply(input), toNum.apply(otherVal));
                            return m == Math.floor(m) ? (long) m : m;
                        }
                        return input != null ? input : 0L;
                    }
                });

                // Max filter
                filters.put("max", new Filter() {
                    @Override
                    public List<String> getArgumentNames() {
                        return List.of("other");
                    }

                    @Override
                    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
                        if (input instanceof List<?> list && !list.isEmpty()) {
                            Optional<Double> opt = list.stream()
                                    .filter(Objects::nonNull)
                                    .map(toNum)
                                    .max(Double::compare);
                            if (opt.isPresent()) {
                                double d = opt.get();
                                return d == Math.floor(d) ? (long) d : d;
                            }
                            return 0L;
                        }
                        Object otherVal = args.containsKey("other") ? args.get("other") : args.get("0");
                        if (input != null && otherVal != null) {
                            double m = Math.max(toNum.apply(input), toNum.apply(otherVal));
                            return m == Math.floor(m) ? (long) m : m;
                        }
                        return input != null ? input : 0L;
                    }
                });

                return filters;
            }

            @Override
            public Map<String, Function> getFunctions() {
                Map<String, Function> functions = new HashMap<>();

                // url_for implementation matching Flask's url_for
                functions.put("url_for", new Function() {
                    @Override
                    public List<String> getArgumentNames() {
                        return null; // dynamic positional and named arguments
                    }

                    @Override
                    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
                        String endpoint = "";
                        if (args.containsKey("0")) {
                            endpoint = String.valueOf(args.get("0"));
                        } else if (args.containsKey("endpoint")) {
                            endpoint = String.valueOf(args.get("endpoint"));
                        }

                        // Static files
                        if ("static".equalsIgnoreCase(endpoint)) {
                            String filename = args.containsKey("filename") ? String.valueOf(args.get("filename")) : "";
                            if (!filename.startsWith("/")) {
                                return "/" + filename;
                            }
                            return filename;
                        }

                        // Dynamic routes
                        if ("delete_expense_route".equalsIgnoreCase(endpoint)) {
                            Object id = args.getOrDefault("expense_id", args.get("1"));
                            return "/expenses/delete/" + id;
                        }
                        if ("delete_budget_route".equalsIgnoreCase(endpoint)) {
                            Object id = args.getOrDefault("budget_id", args.get("1"));
                            return "/budgets/delete/" + id;
                        }
                        if ("delete_recurring_route".equalsIgnoreCase(endpoint)) {
                            Object id = args.getOrDefault("recurring_id", args.get("1"));
                            return "/recurring/delete/" + id;
                        }

                        // Named endpoints
                        String basePath = switch (endpoint) {
                            case "index" -> "/";
                            case "landing" -> "/landing";
                            case "dashboard" -> "/dashboard";
                            case "decision_studio_page" -> "/decision-studio";
                            case "add" -> "/add";
                            case "list_expenses", "expenses" -> "/list";
                            case "budgets_page", "budgets" -> "/budgets";
                            case "analytics_page", "summary" -> "/analytics";
                            case "predict_page" -> "/predict";
                            case "chat_page" -> "/chat";
                            case "notifications_page" -> "/notifications";
                            case "mark_notifications_read" -> "/notifications/read-all";
                            case "clear_notifications_route" -> "/notifications/clear";
                            case "profile_page" -> "/profile";
                            case "settings_page" -> "/settings";
                            case "change_password_route" -> "/settings/change-password";
                            case "delete_account_route" -> "/settings/delete-account";
                            case "export_expenses_csv" -> "/export/csv";
                            case "export_expenses_json" -> "/export/json";
                            case "admin_page" -> "/admin";
                            case "about_page" -> "/about";
                            case "features_page" -> "/features";
                            case "contact_page" -> "/contact";
                            case "privacy_page" -> "/privacy";
                            case "terms_page" -> "/terms";
                            case "login" -> "/login";
                            case "guest_login" -> "/guest-login";
                            case "signup" -> "/signup";
                            case "logout" -> "/logout";
                            default -> "/" + endpoint;
                        };

                        // Append query parameters if present
                        StringBuilder qb = new StringBuilder();
                        for (Map.Entry<String, Object> entry : args.entrySet()) {
                            String k = entry.getKey();
                            if (!k.equals("0") && !k.equals("endpoint") && entry.getValue() != null) {
                                String val = String.valueOf(entry.getValue());
                                if (!val.isBlank() && !val.equals("all")) {
                                    if (qb.isEmpty()) qb.append("?");
                                    else qb.append("&");
                                    qb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                                            .append("=")
                                            .append(URLEncoder.encode(val, StandardCharsets.UTF_8));
                                }
                            }
                        }

                        return basePath + qb;
                    }
                });

                // get_flashed_messages implementation
                functions.put("get_flashed_messages", new Function() {
                    @Override
                    public List<String> getArgumentNames() {
                        return null;
                    }

                    @Override
                    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
                        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                        if (attrs == null) return Collections.emptyList();

                        HttpServletRequest request = attrs.getRequest();
                        HttpSession session = request.getSession(false);
                        if (session == null) return Collections.emptyList();

                        @SuppressWarnings("unchecked")
                        List<List<String>> messages = (List<List<String>>) session.getAttribute(FLASH_MESSAGES_KEY);
                        if (messages != null && !messages.isEmpty()) {
                            List<List<String>> copy = new ArrayList<>(messages);
                            session.removeAttribute(FLASH_MESSAGES_KEY);
                            return copy;
                        }
                        return Collections.emptyList();
                    }
                });

                return functions;
            }
        };
    }
}
