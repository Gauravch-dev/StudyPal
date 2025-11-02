package com.example.studypal;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MlActivity extends AppCompatActivity {

    private ESEPredictor predictor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ml);

        try {
            predictor = ESEPredictor.load(getApplicationContext());
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load weights.json: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        EditText etMse = findViewById(R.id.etMse);
        EditText etCia = findViewById(R.id.etCia);
        EditText etProg = findViewById(R.id.etProg);
        TextView tvResult = findViewById(R.id.tvResult);
        Button btnPredict = findViewById(R.id.btnPredict);

        btnPredict.setOnClickListener(v -> {
            try {
                double mse = Double.parseDouble(etMse.getText().toString().trim());
                double cia = Double.parseDouble(etCia.getText().toString().trim());
                double prog = Double.parseDouble(etProg.getText().toString().trim());
                double esePct = predictor.predict(mse, cia, prog);
                double eseMarks = Math.round((esePct * 0.50) * 10.0) / 10.0;
                tvResult.setText("ESE: " + esePct + "%  (~" + eseMarks + "/50)");
            } catch (NumberFormatException ex) {
                Toast.makeText(this, "Please enter valid numbers", Toast.LENGTH_SHORT).show();
            }
        });
    }
}