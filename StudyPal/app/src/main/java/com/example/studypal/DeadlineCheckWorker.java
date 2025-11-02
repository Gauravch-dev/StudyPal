package com.example.studypal;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.FirebaseApp;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DeadlineCheckWorker extends Worker {

    public static final String KEY_USER_EMAIL = "userEmail";
    private static final String CHANNEL_ID = "deadline_alerts";

    public DeadlineCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        // Ensure Firebase is initialized in this process
        try { FirebaseApp.initializeApp(context); } catch (Exception ignored) {}
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    @NonNull
    @Override
    public Result doWork() {
        String userEmail = getInputData().getString(KEY_USER_EMAIL);
        if (userEmail == null || userEmail.trim().isEmpty()) {
            return Result.success(); // nothing to do
        }

        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            // Fetch user plans (blocking inside Worker)
            List<DocumentSnapshot> docs = Tasks.await(
                    db.collection("studyPlans").whereEqualTo("userEmail", userEmail).get(),
                    20, TimeUnit.SECONDS
            ).getDocuments();

            Calendar now = Calendar.getInstance();
            Calendar due = Calendar.getInstance();

            for (DocumentSnapshot doc : docs) {
                String subject = safe(doc.getString("subject"));
                String dateStr = safe(doc.getString("date"));
                if (dateStr.isEmpty()) continue;

                long daysLeft = daysUntil(dateStr, now, due);
                // Notify for 0–1 days left (today or tomorrow). Tweak as you like.
                if (daysLeft >= 0 && daysLeft <= 1) {
                    String message = "Your study plan for " + subject + " is due in " + daysLeft + " day(s).";
                    showNotification(getApplicationContext(), message);
                }
            }

            return Result.success();
        } catch (Exception e) {
            // If it fails due to network/timeout, retry later
            return Result.retry();
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static long daysUntil(String ddMMyyyy, Calendar now, Calendar due) {
        try {
            String[] p = ddMMyyyy.split("/");
            int d = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]) - 1;
            int y = Integer.parseInt(p[2]);
            due.set(y, m, d, 0, 0, 0);
            due.set(Calendar.MILLISECOND, 0);

            Calendar today = (Calendar) now.clone();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            long diffMs = due.getTimeInMillis() - today.getTimeInMillis();
            return diffMs / (1000L * 60 * 60 * 24);
        } catch (Exception e) {
            return Long.MAX_VALUE; // invalid date => ignore
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private static void showNotification(Context ctx, String message) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Study Deadlines", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)  // you added ic_notification.xml
                .setContentTitle("Upcoming Study Deadline")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat.from(ctx)
                .notify((int) System.currentTimeMillis(), b.build());
    }

    /** Optional constraints if you want to reuse in code elsewhere. */
    public static Constraints netConnected() {
        return new Constraints.Builder().setRequiredNetworkType(
                androidx.work.NetworkType.CONNECTED).build();
    }

    /** Helper to create input data */
    public static Data inputFor(String userEmail) {
        return new Data.Builder().putString(KEY_USER_EMAIL, userEmail).build();
    }
}
