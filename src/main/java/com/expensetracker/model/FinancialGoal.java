package com.expensetracker.model;

public class FinancialGoal {
    private Long id;
    private Long userId;
    private String title;
    private Double targetAmount;
    private Double currentAmount;
    private String targetDate;
    private String category;
    private String createdAt;

    public FinancialGoal() {}

    public FinancialGoal(Long id, Long userId, String title, Double targetAmount, Double currentAmount,
                         String targetDate, String category, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.targetAmount = targetAmount;
        this.currentAmount = currentAmount != null ? currentAmount : 0.0;
        this.targetDate = targetDate;
        this.category = category != null ? category : "General";
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Double getTargetAmount() { return targetAmount; }
    public void setTargetAmount(Double targetAmount) { this.targetAmount = targetAmount; }
    public Double getCurrentAmount() { return currentAmount; }
    public void setCurrentAmount(Double currentAmount) { this.currentAmount = currentAmount; }
    public String getTargetDate() { return targetDate; }
    public void setTargetDate(String targetDate) { this.targetDate = targetDate; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
