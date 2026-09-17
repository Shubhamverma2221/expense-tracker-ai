package com.expensetracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.util.*;

@Service
public class CategorizerService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<String>> rules = new LinkedHashMap<>();

    public static final Map<String, Map<String, String>> CATEGORY_METADATA = new LinkedHashMap<>();

    static {
        CATEGORY_METADATA.put("Food", Map.of("icon", "🍔", "color", "#f59e0b", "bg", "rgba(245, 158, 11, 0.15)"));
        CATEGORY_METADATA.put("Travel", Map.of("icon", "🚕", "color", "#3b82f6", "bg", "rgba(59, 130, 246, 0.15)"));
        CATEGORY_METADATA.put("Shopping", Map.of("icon", "🛍️", "color", "#ec4899", "bg", "rgba(236, 72, 153, 0.15)"));
        CATEGORY_METADATA.put("Bills", Map.of("icon", "🧾", "color", "#ef4444", "bg", "rgba(239, 68, 68, 0.15)"));
        CATEGORY_METADATA.put("Entertainment", Map.of("icon", "🎬", "color", "#8b5cf6", "bg", "rgba(139, 92, 246, 0.15)"));
        CATEGORY_METADATA.put("Health", Map.of("icon", "🩺", "color", "#10b981", "bg", "rgba(16, 185, 129, 0.15)"));
        CATEGORY_METADATA.put("Education", Map.of("icon", "📚", "color", "#06b6d4", "bg", "rgba(6, 182, 212, 0.15)"));
        CATEGORY_METADATA.put("Investments", Map.of("icon", "📈", "color", "#14b8a6", "bg", "rgba(20, 184, 166, 0.15)"));
        CATEGORY_METADATA.put("Other", Map.of("icon", "📦", "color", "#6b7280", "bg", "rgba(107, 114, 128, 0.15)"));
    }

    private static final Map<String, List<String>> DEFAULT_RULES = new LinkedHashMap<>();

    static {
        DEFAULT_RULES.put("Food", Arrays.asList(
                "food", "meal", "restaurant", "cafe", "grocer", "pizza", "burger", "coffee", "tea", "chai",
                "starbucks", "swiggy", "zomato", "blinkit", "zepto", "instamart", "lunch", "dinner", "breakfast",
                "snacks", "bakery", "mcdonalds", "kfc", "dominos", "subway", "bar", "pub", "biryani", "supermarket"
        ));
        DEFAULT_RULES.put("Travel", Arrays.asList(
                "uber", "ola", "rapido", "taxi", "cab", "bus", "train", "flight", "fuel", "petrol", "diesel",
                "metro", "toll", "parking", "auto", "railway", "airline", "indigo", "air india", "car service"
        ));
        DEFAULT_RULES.put("Shopping", Arrays.asList(
                "amazon", "flipkart", "myntra", "mall", "shop", "clothes", "electronics", "shoes", "zara",
                "h&m", "book", "gadget", "apple", "samsung", "retail", "store", "fashion", "online order"
        ));
        DEFAULT_RULES.put("Bills", Arrays.asList(
                "electric", "electricity", "water", "wifi", "internet", "broadband", "mobile", "recharge", "phone",
                "rent", "maintenance", "gas", "cylinder", "utility", "insurance", "emi", "loan", "tax", "subscription"
        ));
        DEFAULT_RULES.put("Entertainment", Arrays.asList(
                "movie", "cinema", "netflix", "spotify", "prime", "hotstar", "youtube", "game", "gaming", "steam",
                "playstation", "xbox", "concert", "theatre", "bookmyshow", "club", "party", "outing", "event"
        ));
        DEFAULT_RULES.put("Health", Arrays.asList(
                "doctor", "pharmacy", "medicine", "hospital", "clinic", "gym", "fitness", "supplement",
                "dental", "dentist", "lab", "test", "medication", "optical", "glasses", "therapy"
        ));
        DEFAULT_RULES.put("Education", Arrays.asList(
                "course", "book", "tuition", "udemy", "coursera", "college", "school", "exam", "cert", "training"
        ));
        DEFAULT_RULES.put("Investments", Arrays.asList(
                "stock", "mutual fund", "sip", "crypto", "zerodha", "groww", "gold", "deposit", "savings"
        ));
        DEFAULT_RULES.put("Other", Collections.emptyList());
    }

    @PostConstruct
    public void init() {
        loadRules();
    }

    private void loadRules() {
        File file = new File("categories.json");
        if (file.exists()) {
            try {
                Map<String, List<String>> loaded = objectMapper.readValue(file, new TypeReference<>() {});
                rules.putAll(loaded);
            } catch (Exception e) {
                System.err.println("Notice: Could not parse local categories.json, using defaults: " + e.getMessage());
            }
        }

        if (rules.isEmpty()) {
            try (InputStream is = getClass().getResourceAsStream("/categories.json")) {
                if (is != null) {
                    Map<String, List<String>> loaded = objectMapper.readValue(is, new TypeReference<>() {});
                    rules.putAll(loaded);
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        // Fill any missing categories with default rules
        for (Map.Entry<String, List<String>> entry : DEFAULT_RULES.entrySet()) {
            rules.putIfAbsent(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    public String categorize(String description) {
        if (description == null || description.isBlank()) {
            return "Other";
        }
        String text = description.toLowerCase();
        String bestCategory = "Other";
        int bestMatchCount = 0;

        for (Map.Entry<String, List<String>> entry : rules.entrySet()) {
            String category = entry.getKey();
            List<String> keywords = entry.getValue();
            int matches = 0;
            if (keywords != null) {
                for (String kw : keywords) {
                    if (kw != null && !kw.isBlank() && text.contains(kw.toLowerCase())) {
                        matches++;
                    }
                }
            }
            if (matches > bestMatchCount) {
                bestMatchCount = matches;
                bestCategory = category;
            }
        }
        return bestCategory;
    }

    public Map<String, String> getMeta(String category) {
        return CATEGORY_METADATA.getOrDefault(category,
                Map.of("icon", "🏷️", "color", "#9ca3af", "bg", "rgba(156, 163, 175, 0.15)"));
    }

    public Map<String, List<String>> getRules() {
        return rules;
    }

    public Map<String, Map<String, String>> getAllMetadata() {
        return CATEGORY_METADATA;
    }
}
