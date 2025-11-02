package com.example.studypal;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HomeActivity extends AppCompatActivity {

    // -----------------------------
    // Study Plan Section
    // -----------------------------
    EditText etSubject, etTopics, etDate;
    Button btnSavePlan, btnLogout;
    ListView listViewPlans;
    FirebaseFirestore db;
    String userEmail;
    ArrayList<Map<String, String>> plansList;
    ArrayList<String> subjects;
    android.widget.ArrayAdapter<String> adapter;

    // -----------------------------
    // Gemini Chat Section
    // -----------------------------
    EditText etMessage;
    Button btnSend;
    LinearLayout chatContainer;
    ScrollView scrollChat;

    private static final String GEMINI_API_KEY = "AIzaSyD5BXyyZ_MeQKtvuu__gYIdC07YR8WI7Kw";
    private static final String GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + GEMINI_API_KEY;

    private final OkHttpClient httpClient = new OkHttpClient();
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
    private final ArrayList<MessageTurn> conversation = new ArrayList<>();

    private static class MessageTurn {
        final String role;
        final String text;
        MessageTurn(String role, String text) { this.role = role; this.text = text; }
    }

    private static final int REQ_CODE_NOTIF = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) ensureNotificationPermission();

        // Initialize UI
        etSubject = findViewById(R.id.etSubject);
        etTopics = findViewById(R.id.etTopics);
        etDate = findViewById(R.id.etDate);
        btnSavePlan = findViewById(R.id.btnSavePlan);
        btnLogout = findViewById(R.id.btnLogout);
        listViewPlans = findViewById(R.id.listViewPlans);

        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        chatContainer = findViewById(R.id.chatContainer);
        scrollChat = findViewById(R.id.scrollChat);

        ImageButton btnMap = findViewById(R.id.btnMap);
        btnMap.setOnClickListener(v -> startActivity(new Intent(HomeActivity.this, MapsActivity.class)));

        ImageButton btnTools = findViewById(R.id.btnTools);
        btnTools.setOnClickListener(v -> startActivity(new Intent(HomeActivity.this, MlActivity.class)));

        // Date picker
        etDate.setOnClickListener(v -> {
            final Calendar calendar = Calendar.getInstance();
            new DatePickerDialog(
                    HomeActivity.this,
                    (view, year, month, day) ->
                            etDate.setText(day + "/" + (month + 1) + "/" + year),
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            ).show();
        });

        db = FirebaseFirestore.getInstance();
        plansList = new ArrayList<>();
        subjects = new ArrayList<>();
        userEmail = getIntent().getStringExtra("email");

        // Schedule background worker only for logged-in user
        if (userEmail != null && !userEmail.trim().isEmpty()) {
            scheduleDailyDeadlineWorker();
        }

        btnSavePlan.setOnClickListener(v -> saveStudyPlan());
        btnLogout.setOnClickListener(v -> {
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(HomeActivity.this, LoginActivity.class));
            finish();
        });

        fetchStudyPlans();

        listViewPlans.setOnItemClickListener((parent, view, position, id) -> {
            Map<String, String> plan = plansList.get(position);
            String details = "Topics: " + plan.get("topics") + "\nDate: " + plan.get("date");
            new AlertDialog.Builder(this)
                    .setTitle(plan.get("subject"))
                    .setMessage(details)
                    .setPositiveButton("OK", null)
                    .show();
        });

        btnSend.setOnClickListener(v -> {
            String userMsg = etMessage.getText().toString().trim();
            if (userMsg.isEmpty()) {
                Toast.makeText(this, "Type something first", Toast.LENGTH_SHORT).show();
                return;
            }
            addChatBubble("You", userMsg, true);
            conversation.add(new MessageTurn("user", userMsg));
            etMessage.setText("");
            new Thread(() -> {
                try {
                    String reply = callGemini(conversation);
                    String finalReply = reply != null ? reply : "(No reply)";
                    conversation.add(new MessageTurn("model", finalReply));
                    runOnUiThread(() -> addChatBubble("StudyPal AI", finalReply, false));
                } catch (Exception e) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "AI error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        });
    }

    // -----------------------------
    // Permissions
    // -----------------------------
    @RequiresApi(33)
    private void ensureNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_CODE_NOTIF
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE_NOTIF) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // -----------------------------
    // Firestore Logic + Alerts
    // -----------------------------
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

        db.collection("studyPlans").add(studyPlan)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Study Plan saved!", Toast.LENGTH_SHORT).show();
                    etSubject.setText(""); etTopics.setText(""); etDate.setText("");
                    fetchStudyPlans();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void fetchStudyPlans() {
        db.collection("studyPlans")
                .whereEqualTo("userEmail", userEmail)
                .get()
                .addOnSuccessListener(q -> {
                    plansList.clear();
                    subjects.clear();
                    for (DocumentSnapshot doc : q.getDocuments()) {
                        Map<String, String> plan = new HashMap<>();
                        plan.put("subject", doc.getString("subject"));
                        plan.put("topics", doc.getString("topics"));
                        plan.put("date", doc.getString("date"));
                        plansList.add(plan);
                        subjects.add(doc.getString("subject"));
                    }
                    adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, subjects);
                    listViewPlans.setAdapter(adapter);
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED) {
                        checkDeadlinesAndNotify();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error fetching plans: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private void checkDeadlinesAndNotify() {
        for (Map<String, String> plan : plansList) {
            try {
                String subject = plan.get("subject");
                String date = plan.get("date");
                if (date == null || date.isEmpty()) continue;

                String[] parts = date.split("/");
                if (parts.length != 3) continue;

                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]) - 1;
                int year = Integer.parseInt(parts[2]);

                Calendar today = Calendar.getInstance();
                Calendar due = Calendar.getInstance();
                due.set(year, month, day);

                long diff = due.getTimeInMillis() - today.getTimeInMillis();
                long daysLeft = diff / (1000 * 60 * 60 * 24);

                if (daysLeft >= 0 && daysLeft <= 1) {
                    String msg = "Your study plan for " + subject + " is due in " + daysLeft + " day(s).";
                    runOnUiThread(() -> new AlertDialog.Builder(this)
                            .setTitle("Upcoming Deadline")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show());
                    DeadlineNotifier.showNotification(this, "Upcoming Study Deadline", msg);
                }
            } catch (Exception ignored) {}
        }
    }

    // -----------------------------
    // WorkManager for Background Checks
    // -----------------------------
    private void scheduleDailyDeadlineWorker() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build();

        Data input = new Data.Builder()
                .putString("userEmail", userEmail)
                .build();

        PeriodicWorkRequest dailyCheck = new PeriodicWorkRequest.Builder(
                DeadlineCheckWorker.class, 1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInputData(input)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "deadline_check_daily",
                ExistingPeriodicWorkPolicy.UPDATE,
                dailyCheck
        );
    }

    // -----------------------------
    // Gemini Helpers
    // -----------------------------
    private JSONObject buildGeminiRequest(ArrayList<MessageTurn> convo) throws JSONException {
        JSONArray contents = new JSONArray();
        for (MessageTurn t : convo) {
            JSONObject turn = new JSONObject();
            turn.put("role", t.role);
            JSONArray parts = new JSONArray();
            JSONObject p = new JSONObject();
            p.put("text", t.text);
            parts.put(p);
            turn.put("parts", parts);
            contents.put(turn);
        }
        JSONObject root = new JSONObject();
        root.put("contents", contents);
        return root;
    }

    private String callGemini(ArrayList<MessageTurn> convo) throws IOException, JSONException {
        JSONObject reqJson = buildGeminiRequest(convo);
        RequestBody body = RequestBody.create(reqJson.toString(), JSON_MEDIA);
        Request request = new Request.Builder().url(GEMINI_ENDPOINT).post(body).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
            String respBody = response.body().string();
            return parseGeminiResponse(respBody);
        }
    }

    private String parseGeminiResponse(String respJson) throws JSONException {
        JSONObject root = new JSONObject(respJson);
        if (!root.has("candidates")) return null;
        JSONArray cands = root.getJSONArray("candidates");
        if (cands.length() == 0) return null;
        JSONObject first = cands.getJSONObject(0);
        JSONObject content = first.optJSONObject("content");
        if (content == null) return null;
        JSONArray parts = content.optJSONArray("parts");
        if (parts == null || parts.length() == 0) return null;
        return parts.getJSONObject(0).optString("text", "(no reply)");
    }

    private void addChatBubble(String who, String message, boolean isUser) {
        TextView tv = new TextView(this);
        tv.setText(who + ": " + message);
        tv.setTextSize(15f);
        tv.setPadding(16, 12, 16, 12);
        tv.setBackgroundColor(isUser ? 0xFFDDEAFF : 0xFFEDE7F6);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 8);
        tv.setLayoutParams(lp);
        chatContainer.addView(tv);
        scrollChat.post(() -> scrollChat.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
