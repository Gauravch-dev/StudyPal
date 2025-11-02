package com.example.studypal;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    EditText etSubject, etTopics, etDate;
    Button btnSavePlan, btnLogout;
    RecyclerView recyclerViewPlans;

    FirebaseFirestore db;
    String userEmail;

    ProgressBar progressBar;
    TextView tvProgressPercent;
    Button btnAll, btnCompleted, btnPending;
    String currentFilter = "all";

    EditText etMessage;
    Button btnSend;
    LinearLayout chatContainer;
    ScrollView scrollChat;

    private final ArrayList<StudyPlan> allPlans = new ArrayList<>();
    private StudyPlanAdapter adapter;

    private static final String GEMINI_API_KEY = "AIzaSyD5BXyyZ_MeQKtvuu__gYIdC07YR8WI7Kw";
    private static final String GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + GEMINI_API_KEY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home); // uses home.xml that you provided

        // find views
        etSubject = findViewById(R.id.etSubject);
        etTopics = findViewById(R.id.etTopics);
        etDate = findViewById(R.id.etDate);
        btnSavePlan = findViewById(R.id.btnSavePlan);
        btnLogout = findViewById(R.id.btnLogout);
        recyclerViewPlans = findViewById(R.id.recyclerViewPlans);
        recyclerViewPlans.setLayoutManager(new LinearLayoutManager(this));

        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        chatContainer = findViewById(R.id.chatContainer);
        scrollChat = findViewById(R.id.scrollChat);

        ImageButton btnMap = findViewById(R.id.btnMap);
        btnMap.setOnClickListener(v -> startActivity(new Intent(HomeActivity.this, MapsActivity.class)));
        ImageButton btnTools = findViewById(R.id.btnTools);
        btnTools.setOnClickListener(v -> startActivity(new Intent(HomeActivity.this, MlActivity.class)));

        etDate.setOnClickListener(v -> {
            final Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    HomeActivity.this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String date = selectedDay + "/" + (selectedMonth + 1) + "/" + selectedYear;
                        etDate.setText(date);
                    },
                    year, month, day
            );
            datePickerDialog.show();
        });

        db = FirebaseFirestore.getInstance();
        userEmail = getIntent().getStringExtra("email");
        if (userEmail == null || userEmail.isEmpty()) {
            Toast.makeText(this, "User email missing — please log in again", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // insert progress + filters
        insertProgressAndFilters();

        // adapter with action callbacks: status change, delete, item click
        adapter = new StudyPlanAdapter(this, new ArrayList<>(), new StudyPlanAdapter.OnActionListener() {
            @Override
            public void onStatusChanged(String docId, boolean completed) {
                if (docId == null) return;
                String newStatus = completed ? "completed" : "pending";
                db.collection("studyPlans").document(docId).update("status", newStatus)
                        .addOnFailureListener(e -> runOnUiThread(() -> Toast.makeText(HomeActivity.this, "Status update failed", Toast.LENGTH_SHORT).show()));
            }

            @Override
            public void onDelete(String docId) {
                if (docId == null) return;
                new AlertDialog.Builder(HomeActivity.this)
                        .setTitle("Delete plan")
                        .setMessage("Are you sure you want to delete this plan?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            db.collection("studyPlans").document(docId)
                                    .delete()
                                    .addOnSuccessListener(aVoid -> runOnUiThread(() -> {
                                        // remove from local list & refresh
                                        adapter.removeByDocId(docId);
                                        // also remove from allPlans
                                        for (int i = 0; i < allPlans.size(); i++) {
                                            if (docId.equals(allPlans.get(i).getDocId())) {
                                                allPlans.remove(i);
                                                break;
                                            }
                                        }
                                        updateProgressBar();
                                        Toast.makeText(HomeActivity.this, "Deleted", Toast.LENGTH_SHORT).show();
                                    }))
                                    .addOnFailureListener(e -> runOnUiThread(() -> Toast.makeText(HomeActivity.this, "Delete failed", Toast.LENGTH_SHORT).show()));
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onItemClicked(StudyPlan plan) {
                if (plan == null) return;
                String details = "Topics: " + plan.getTopics() + "\nDate: " + plan.getDate() + "\nStatus: " + plan.getStatus();
                new AlertDialog.Builder(HomeActivity.this)
                        .setTitle(plan.getSubject())
                        .setMessage(details)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });

        recyclerViewPlans.setAdapter(adapter);

        btnSavePlan.setOnClickListener(v -> saveStudyPlan());

        btnLogout.setOnClickListener(v -> {
            Toast.makeText(HomeActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(HomeActivity.this, LoginActivity.class));
            finish();
        });

        // Firestore realtime listener
        db.collection("studyPlans")
                .whereEqualTo("userEmail", userEmail)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        runOnUiThread(() -> Toast.makeText(HomeActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show());
                        return;
                    }
                    if (value == null) return;

                    ArrayList<StudyPlan> newPlans = new ArrayList<>();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        String id = doc.getId();
                        String subject = doc.getString("subject") != null ? doc.getString("subject") : "";
                        String topics = doc.getString("topics") != null ? doc.getString("topics") : "";
                        String date = doc.getString("date") != null ? doc.getString("date") : "";
                        String status = doc.getString("status") != null ? doc.getString("status") : "pending";
                        String owner = doc.getString("userEmail") != null ? doc.getString("userEmail") : userEmail;

                        newPlans.add(new StudyPlan(id, subject, topics, date, status, owner));
                    }

                    runOnUiThread(() -> {
                        allPlans.clear();
                        allPlans.addAll(newPlans);
                        applyFilterAndRefresh();
                        updateProgressBar();
                        Toast.makeText(HomeActivity.this, "Loaded " + allPlans.size() + " plans", Toast.LENGTH_SHORT).show();
                    });
                });

        // chat send preserved
        btnSend.setOnClickListener(v -> {
            String msg = etMessage.getText().toString().trim();
            if (msg.isEmpty()) { Toast.makeText(HomeActivity.this, "Type something", Toast.LENGTH_SHORT).show(); return; }
            addChatBubble("You", msg, true);
            etMessage.setText("");
            addChatBubble("StudyPal AI", "(AI reply placeholder)", false);
        });
    }

    private void saveStudyPlan() {
        String subject = etSubject.getText().toString().trim();
        String topics = etTopics.getText().toString().trim();
        String date = etDate.getText().toString().trim();

        if (TextUtils.isEmpty(subject)) { etSubject.setError("Subject required"); return; }
        if (TextUtils.isEmpty(topics)) { etTopics.setError("Topics required"); return; }
        if (TextUtils.isEmpty(date)) { etDate.setError("Date required"); return; }

        Map<String, Object> studyPlan = new HashMap<>();
        studyPlan.put("subject", subject);
        studyPlan.put("topics", topics);
        studyPlan.put("date", date);
        studyPlan.put("userEmail", userEmail);
        studyPlan.put("status", "pending");

        db.collection("studyPlans")
                .add(studyPlan)
                .addOnSuccessListener(documentReference -> runOnUiThread(() -> {
                    Toast.makeText(HomeActivity.this, "Study Plan saved!", Toast.LENGTH_SHORT).show();
                    etSubject.setText("");
                    etTopics.setText("");
                    etDate.setText("");
                }))
                .addOnFailureListener(e -> runOnUiThread(() -> Toast.makeText(HomeActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()));
    }

    private void insertProgressAndFilters() {
        ViewParent vp = recyclerViewPlans.getParent();
        if (!(vp instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) vp;

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.setPadding(12,12,12,12);

        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        progressRow.setGravity(Gravity.CENTER_VERTICAL);

        tvProgressPercent = new TextView(this);
        tvProgressPercent.setText("Progress: 0%");
        tvProgressPercent.setTextSize(14f);
        tvProgressPercent.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
        progressBar.setMax(100);
        progressBar.setProgress(0);

        progressRow.addView(tvProgressPercent);
        progressRow.addView(progressBar);

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        filterRow.setGravity(Gravity.CENTER_VERTICAL);

        btnAll = new Button(this);
        btnAll.setText("All");
        btnCompleted = new Button(this);
        btnCompleted.setText("Completed");
        btnPending = new Button(this);
        btnPending.setText("Pending");

        filterRow.addView(btnAll);
        filterRow.addView(btnCompleted);
        filterRow.addView(btnPending);

        container.addView(progressRow);
        container.addView(filterRow);

        int index = parent.indexOfChild(recyclerViewPlans);
        if (index >= 0) parent.addView(container, index);
        else parent.addView(container);

        btnAll.setOnClickListener(v -> { currentFilter = "all"; applyFilterAndRefresh(); });
        btnCompleted.setOnClickListener(v -> { currentFilter = "completed"; applyFilterAndRefresh(); });
        btnPending.setOnClickListener(v -> { currentFilter = "pending"; applyFilterAndRefresh(); });
    }

    private void applyFilterAndRefresh() {
        ArrayList<StudyPlan> filtered = new ArrayList<>();
        for (StudyPlan p : allPlans) {
            if ("all".equals(currentFilter)) filtered.add(p);
            else if ("completed".equals(currentFilter) && p.isCompleted()) filtered.add(p);
            else if ("pending".equals(currentFilter) && !p.isCompleted()) filtered.add(p);
        }
        adapter.updateList(filtered);
    }

    private void updateProgressBar() {
        int total = allPlans.size();
        if (total == 0) {
            if (progressBar != null) progressBar.setProgress(0);
            if (tvProgressPercent != null) tvProgressPercent.setText("Progress: 0%");
            return;
        }
        int completed = 0;
        for (StudyPlan p : allPlans) if (p.isCompleted()) completed++;
        int percent = (int) ((completed * 100.0f) / total + 0.5f);
        if (progressBar != null) progressBar.setProgress(percent);
        if (tvProgressPercent != null) tvProgressPercent.setText("Progress: " + percent + "%");
    }

    private void addChatBubble(String who, String message, boolean isUser) {
        TextView tv = new TextView(this);
        tv.setText(who + ": " + message);
        tv.setTextSize(15f);
        tv.setPadding(16, 12, 16, 12);

        if (isUser) tv.setBackgroundColor(0xFFDDEAFF);
        else tv.setBackgroundColor(0xFFEDE7F6);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 8, 0, 8);
        tv.setLayoutParams(lp);

        chatContainer.addView(tv);
        scrollChat.post(() -> scrollChat.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
