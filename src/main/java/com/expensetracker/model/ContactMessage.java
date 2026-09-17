package com.expensetracker.model;

public class ContactMessage {
    private Long id;
    private String name;
    private String email;
    private String subject;
    private String message;
    private String status;
    private String createdAt;

    public ContactMessage() {}

    public ContactMessage(Long id, String name, String email, String subject, String message, String status, String createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.subject = subject;
        this.message = message;
        this.status = status != null ? status : "unread";
        this.createdAt = createdAt;
    }

    public Object get(String key) {
        if (key == null) return null;
        return switch (key) {
            case "id" -> id;
            case "name" -> name;
            case "email" -> email;
            case "subject" -> subject;
            case "message" -> message;
            case "status" -> status;
            case "created_at" -> createdAt;
            default -> null;
        };
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
