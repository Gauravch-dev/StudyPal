package com.example.studypal;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DeadlineCheckWorker extends Worker {

    private static final String TAG = "DeadlineCheckWorker";
    public static final String KEY_USER_EMAIL = "USER_EMAIL_KEY";
    private static final String CHANNEL_ID = "DEADLINE_CHANNEL";

    public DeadlineCheckWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * This is the main background task.
     * It will run periodically (every 12 hours as you scheduled).
     */
    @NonNull
    @Override
    public Result doWork() {
        // Get the user's email from the input data
        String userEmail = getInputData().getString(KEY_USER_EMAIL);
        if (userEmail == null || userEmail.isEmpty()) {
            Log.e(TAG, "User email is missing. Stopping worker.");
            return Result.failure();
        }

        Log.d(TAG, "Worker running for user: " + userEmail);

        try {
            // This code runs on a background thread.
            // We can make a *synchronous* call to Firestore.
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            // Get "tomorrow's" date to check against
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            Date tomorrow = calendar.getTime();
            SimpleDateFormat sdf = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());
            String tomorrowDateString = sdf.format(tomorrow);

            Log.d(TAG, "Checking for deadlines on: " + tomorrowDateString);

            // Query Firestore for pending plans for this user
            db.collection("studyPlans")
                    .whereEqualTo("userEmail", userEmail)
                    .whereEqualTo("status", "pending")
                    .whereEqualTo("date", tomorrowDateString) // Check for plans due tomorrow
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            int count = task.getResult().getDocuments().size();
                            if (count > 0) {
                                Log.d(TAG, "Found " + count + " plans due tomorrow.");
                                // If we found plans, send a notification
                                String subject = task.getResult().getDocuments().get(0).getString("subject");
                                String notificationText = (count == 1)
                                        ? "Your plan for '" + subject + "' is due tomorrow!"
                                        : "You have " + count + " plans due tomorrow. Don't forget!";

                                sendNotification("StudyPal Deadline", notificationText);
                            } else {
                                Log.d(TAG, "No plans due tomorrow.");
                            }
                        } else {
                            Log.e(TAG, "Failed to fetch plans: ", task.getException());
                        }
                    });

            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Worker failed", e);
            return Result.failure();
        }
    }

    /**
     * Helper method to build the input data
     */
    public static Data inputFor(String userEmail) {
        return new Data.Builder()
                .putString(KEY_USER_EMAIL, userEmail)
                .build();
    }

    /**
     * Helper method to build the network constraints
     */
    public static Constraints netConnected() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    /**
     * Creates and displays a notification.
     */
    private void sendNotification(String title, String message) {
        NotificationManager notificationManager = (NotificationManager)
                getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        // Create a notification channel (required for Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Study Deadlines",
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // ⚠️ !! REPLACE with your app's icon !!
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        // Show the notification
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}