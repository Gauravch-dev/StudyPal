package com.example.studypal;

/**
 * Model used by Firestore -> app.
 * Includes docId so we can update/delete the exact Firestore document.
 */
public class StudyPlan {
    private String docId;      // firestore document id (not stored in doc fields)
    private String subject;
    private String topics;
    private String date;
    private String status;     // "pending" or "completed"
    private String userEmail;  // owner

    // Required empty constructor for Firebase
    public StudyPlan() { }

    public StudyPlan(String docId, String subject, String topics, String date, String status, String userEmail) {
        this.docId = docId;
        this.subject = subject;
        this.topics = topics;
        this.date = date;
        this.status = status;
        this.userEmail = userEmail;
    }

    // getters / setters
    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }

    public String getSubject() { return subject != null ? subject : ""; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getTopics() { return topics != null ? topics : ""; }
    public void setTopics(String topics) { this.topics = topics; }

    public String getDate() { return date != null ? date : ""; }
    public void setDate(String date) { this.date = date; }

    public String getStatus() { return status != null ? status : "pending"; }
    public void setStatus(String status) { this.status = status; }

    public String getUserEmail() { return userEmail != null ? userEmail : ""; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public boolean isCompleted() { return "completed".equalsIgnoreCase(getStatus()) || "Completed".equalsIgnoreCase(getStatus()); }
}
