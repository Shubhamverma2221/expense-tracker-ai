package com.expensetracker.model;

public class Budget {
    private Long id;
    private Long userId;
    private String category;
    private Double limitAmount;
    private String month;
    private String createdAt;

    // Computed attributes for templates
    private Double spent = 0.0;
    private Double remaining = 0.0;
    private Double percentage = 0.0;
    private Boolean isExceeded = false;

    public Budget() {}

    public Budget(Long id, Long userId, String category, Double limitAmount, String month, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.category = category;
        this.limitAmount = limitAmount;
        this.month = month;
        this.createdAt = createdAt;
    }

    public Object get(String key) {
        if (key == null) return null;
        return switch (key) {
            case "id" -> id;
            case "user_id" -> userId;
            case "category" -> category;
            case "limit_amount" -> limitAmount;
            case "month" -> month;
            case "created_at" -> createdAt;
            case "spent" -> spent;
            case "remaining" -> remaining;
            case "percentage" -> percentage;
            case "is_exceeded" -> isExceeded;
            default -> null;
        };
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getLimitAmount() { return limitAmount; }
    public void setLimitAmount(Double limitAmount) { this.limitAmount = limitAmount; }

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public Double getSpent() { return spent; }
    public void setSpent(Double spent) { this.spent = spent; }

    public Double getRemaining() { return remaining; }
    public void setRemaining(Double remaining) { this.remaining = remaining; }

    public Double getPercentage() { return percentage; }
    public void setPercentage(Double percentage) { this.percentage = percentage; }

    public Boolean getIsExceeded() { return isExceeded; }
    public void setIsExceeded(Boolean isExceeded) { this.isExceeded = isExceeded; }
}
