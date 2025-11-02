package com.example.studypal;

public class StudyPlan {
    private int id;
    private String subject;
    private String topics;
    private String date;
    private boolean completed;

    public StudyPlan() {}

    public StudyPlan(String subject, String topics, String date, boolean completed) {
        this.subject = subject;
        this.topics = topics;
        this.date = date;
        this.completed = completed;
    }

    public StudyPlan(int id, String subject, String topics, String date, boolean completed) {
        this.id = id;
        this.subject = subject;
        this.topics = topics;
        this.date = date;
        this.completed = completed;
    }

    public int getId() { return id; }
    public String getSubject() { return subject != null ? subject : ""; }
    public String getTopics() { return topics != null ? topics : ""; }
    public String getDate() { return date != null ? date : ""; }
    public boolean isCompleted() { return completed; }

    public void setId(int id) { this.id = id; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setTopics(String topics) { this.topics = topics; }
    public void setDate(String date) { this.date = date; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
