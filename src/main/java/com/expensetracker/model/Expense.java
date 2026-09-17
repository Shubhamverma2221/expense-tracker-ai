package com.expensetracker.model;

public class Expense {
    private Long id;
    private Long userId;
    private String date;
    private Double amount;
    private String description;
    private String category;
    private String paymentMethod;
    private String notes;
    private String receiptPath;
    private String createdAt;

    public Expense() {}

    public Expense(Long id, Long userId, String date, Double amount, String description,
                   String category, String paymentMethod, String notes, String receiptPath, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.date = date;
        this.amount = amount;
        this.description = description;
        this.category = category;
        this.paymentMethod = paymentMethod != null ? paymentMethod : "UPI";
        this.notes = notes != null ? notes : "";
        this.receiptPath = receiptPath != null ? receiptPath : "";
        this.createdAt = createdAt;
    }

    public Object get(String key) {
        if (key == null) return null;
        return switch (key) {
            case "id" -> id;
            case "user_id" -> userId;
            case "date" -> date;
            case "amount" -> amount;
            case "description" -> description;
            case "category" -> category;
            case "payment_method" -> paymentMethod;
            case "notes" -> notes;
            case "receipt_path" -> receiptPath;
            case "created_at" -> createdAt;
            default -> null;
        };
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getReceiptPath() { return receiptPath; }
    public void setReceiptPath(String receiptPath) { this.receiptPath = receiptPath; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
