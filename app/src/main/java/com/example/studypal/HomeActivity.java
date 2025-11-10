package com.example.studypal;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HomeActivity extends AppCompatActivity {

    EditText etSubject, etTopics, etDate;
    Button btnSavePlan, btnLogout, btnChat;
    RecyclerView recyclerViewPlans;

    FirebaseFirestore db;
    String userEmail;

    ProgressBar progressBar;
    TextView tvProgressPercent;
    Button btnAll, btnCompleted, btnPending;
    String currentFilter = "all";

    private final ArrayList<StudyPlan> allPlans = new ArrayList<>();
    private StudyPlanAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // 📘 Initialize UI
        etSubject = findViewById(R.id.etSubject);
        etTopics = findViewById(R.id.etTopics);
        etDate = findViewById(R.id.etDate);
        btnSavePlan = findViewById(R.id.btnSavePlan);
        btnLogout = findViewById(R.id.btnLogout);
        btnChat = findViewById(R.id.btnChat);
        recyclerViewPlans = findViewById(R.id.recyclerViewPlans);
        recyclerViewPlans.setLayoutManager(new LinearLayoutManager(this));

        // 🗺️ Navigation buttons
        ImageButton btnMap = findViewById(R.id.btnMap);
        btnMap.setOnClickListener(v -> startActivity(new Intent(this, MapsActivity.class)));

        ImageButton btnTools = findViewById(R.id.btnTools);
        btnTools.setOnClickListener(v -> startActivity(new Intent(this, MlActivity.class)));

        // 💬 Chat button → opens ChatBotActivity
        btnChat.setOnClickListener(v -> startActivity(new Intent(this, ChatBotActivity.class)));

        // 📅 Date picker
        etDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) ->
                            etDate.setText(dayOfMonth + "/" + (month + 1) + "/" + year),
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            ).show();
        });

        // 🔥 Firestore setup
        db = FirebaseFirestore.getInstance();
        userEmail = getIntent().getStringExtra("email");

        if (userEmail == null || userEmail.isEmpty()) {
            Toast.makeText(this, "User email missing — please log in again", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        insertProgressAndFilters();

        // 📋 Recycler adapter
        adapter = new StudyPlanAdapter(this, new ArrayList<>(), new StudyPlanAdapter.OnActionListener() {
            @Override
            public void onStatusChanged(String docId, boolean completed) {
                if (docId == null) return;
                String newStatus = completed ? "completed" : "pending";
                db.collection("studyPlans").document(docId)
                        .update("status", newStatus)
                        .addOnFailureListener(e ->
                                Toast.makeText(HomeActivity.this, "Status update failed", Toast.LENGTH_SHORT).show());
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
                                    .addOnSuccessListener(aVoid -> {
                                        adapter.removeByDocId(docId);
                                        allPlans.removeIf(p -> docId.equals(p.getDocId()));
                                        updateProgressBar();
                                        Toast.makeText(HomeActivity.this, "Deleted", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(HomeActivity.this, "Delete failed", Toast.LENGTH_SHORT).show());
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onItemClicked(StudyPlan plan) {
                if (plan == null) return;
                new AlertDialog.Builder(HomeActivity.this)
                        .setTitle(plan.getSubject())
                        .setMessage("Topics: " + plan.getTopics() +
                                "\nDate: " + plan.getDate() +
                                "\nStatus: " + plan.getStatus())
                        .setPositiveButton("OK", null)
                        .show();
            }
        });

        recyclerViewPlans.setAdapter(adapter);

        // 💾 Save Study Plan
        btnSavePlan.setOnClickListener(v -> saveStudyPlan());

        // 🚪 Logout
        btnLogout.setOnClickListener(v -> {
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // 🔔 Firestore Realtime Sync
        db.collection("studyPlans")
                .whereEqualTo("userEmail", userEmail)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (value == null) return;

                    allPlans.clear();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        allPlans.add(new StudyPlan(
                                doc.getId(),
                                doc.getString("subject"),
                                doc.getString("topics"),
                                doc.getString("date"),
                                doc.getString("status"),
                                doc.getString("userEmail")
                        ));
                    }
                    applyFilterAndRefresh();
                    updateProgressBar();
                });

        // 🧠 Periodic deadline reminder worker
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                DeadlineCheckWorker.class, 12, TimeUnit.HOURS)
                .setInputData(DeadlineCheckWorker.inputFor(userEmail))
                .setConstraints(DeadlineCheckWorker.netConnected())
                .build();

        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                        "deadline_checker_" + userEmail,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        workRequest);

        WorkManager.getInstance(this).enqueue(
                new OneTimeWorkRequest.Builder(DeadlineCheckWorker.class)
                        .setInputData(DeadlineCheckWorker.inputFor(userEmail))
                        .setConstraints(DeadlineCheckWorker.netConnected())
                        .build()
        );
    }

    private void saveStudyPlan() {
        String subject = etSubject.getText().toString().trim();
        String topics = etTopics.getText().toString().trim();
        String date = etDate.getText().toString().trim();

        if (TextUtils.isEmpty(subject)) { etSubject.setError("Subject required"); return; }
        if (TextUtils.isEmpty(topics)) { etTopics.setError("Topics required"); return; }
        if (TextUtils.isEmpty(date)) { etDate.setError("Date required"); return; }

        Map<String, Object> plan = new HashMap<>();
        plan.put("subject", subject);
        plan.put("topics", topics);
        plan.put("date", date);
        plan.put("userEmail", userEmail);
        plan.put("status", "pending");

        db.collection("studyPlans")
                .add(plan)
                .addOnSuccessListener(docRef -> {
                    Toast.makeText(this, "Study Plan saved!", Toast.LENGTH_SHORT).show();
                    etSubject.setText("");
                    etTopics.setText("");
                    etDate.setText("");
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void insertProgressAndFilters() {
        ViewParent parentView = recyclerViewPlans.getParent();
        if (!(parentView instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) parentView;

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.setPadding(12, 12, 12, 12);

        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);

        tvProgressPercent = new TextView(this);
        tvProgressPercent.setText("Progress: 0%");
        tvProgressPercent.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
        progressBar.setMax(100);
        progressRow.addView(tvProgressPercent);
        progressRow.addView(progressBar);

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);

        btnAll = new Button(this); btnAll.setText("All");
        btnCompleted = new Button(this); btnCompleted.setText("Completed");
        btnPending = new Button(this); btnPending.setText("Pending");

        filterRow.addView(btnAll);
        filterRow.addView(btnCompleted);
        filterRow.addView(btnPending);

        container.addView(progressRow);
        container.addView(filterRow);

        int index = parent.indexOfChild(recyclerViewPlans);
        parent.addView(container, index >= 0 ? index : 0);

        btnAll.setOnClickListener(v -> { currentFilter = "all"; applyFilterAndRefresh(); });
        btnCompleted.setOnClickListener(v -> { currentFilter = "completed"; applyFilterAndRefresh(); });
        btnPending.setOnClickListener(v -> { currentFilter = "pending"; applyFilterAndRefresh(); });
    }

    private void applyFilterAndRefresh() {
        ArrayList<StudyPlan> filtered = new ArrayList<>();
        for (StudyPlan p : allPlans) {
            if ("all".equals(currentFilter) ||
                    ("completed".equals(currentFilter) && p.isCompleted()) ||
                    ("pending".equals(currentFilter) && !p.isCompleted())) {
                filtered.add(p);
            }
        }
        adapter.updateList(filtered);
    }

    private void updateProgressBar() {
        int total = allPlans.size();
        if (total == 0) {
            progressBar.setProgress(0);
            tvProgressPercent.setText("Progress: 0%");
            return;
        }
        int completed = 0;
        for (StudyPlan p : allPlans) if (p.isCompleted()) completed++;
        int percent = (int) ((completed * 100.0f) / total);
        progressBar.setProgress(percent);
        tvProgressPercent.setText("Progress: " + percent + "%");
    }
}
