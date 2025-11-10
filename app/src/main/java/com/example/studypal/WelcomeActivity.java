package com.example.studypal;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.VideoView;
import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {

    Button btnLogin, btnRegister;
    VideoView videoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // Initialize buttons
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);

        // Initialize VideoView
        videoView = findViewById(R.id.videoView);

        // Keep video on top and visible
        videoView.setZOrderOnTop(true);
        videoView.setAlpha(1.0f);
        videoView.setBackgroundColor(Color.BLACK);

        // Load and play video from res/raw
        Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.studypal_video);
        videoView.setVideoURI(videoUri);

        // Start & loop video
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            videoView.start();
        });

        // Go to Login Page
        btnLogin.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        // Go to Register Page
        btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }
}
