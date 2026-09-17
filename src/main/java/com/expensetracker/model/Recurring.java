package com.expensetracker.model;

public class Recurring {
    private Long id;
    private Long userId;
    private String description;
    private Double amount;
    private String category;
    private String frequency;
    private String nextDueDate;
    private Boolean isActive;

    public Recurring() {}

    public Recurring(Long id, Long userId, String description, Double amount, String category,
                     String frequency, String nextDueDate, Boolean isActive) {
        this.id = id;
        this.userId = userId;
        this.description = description;
        this.amount = amount;
        this.category = category;
        this.frequency = frequency != null ? frequency : "Monthly";
        this.nextDueDate = nextDueDate;
        this.isActive = isActive != null ? isActive : true;
    }

    public Object get(String key) {
        if (key == null) return null;
        return switch (key) {
            case "id" -> id;
            case "user_id" -> userId;
            case "description" -> description;
            case "amount" -> amount;
            case "category" -> category;
            case "frequency" -> frequency;
            case "next_due_date" -> nextDueDate;
            case "is_active" -> isActive;
            default -> null;
        };
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public String getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(String nextDueDate) { this.nextDueDate = nextDueDate; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
