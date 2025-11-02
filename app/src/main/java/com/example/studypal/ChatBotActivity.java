package com.example.studypal;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Simple chatbot UI using Gemini 2.0 Flash via REST v1beta.
 *
 * NOTE: Do NOT ship real secrets like API keys in production apps.
 * This is for demo / local builds only.
 */
public class ChatBotActivity extends AppCompatActivity {

    // TODO: put your Gemini API key here for testing
    private static final String API_KEY = "PUT_YOUR_GEMINI_API_KEY_HERE";

    // Gemini 2.0 Flash model endpoint (v1beta)
    // ref: curl usage for gemini-2.0-flash with generateContent. :contentReference[oaicite:3]{index=3}
    private static final String GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    private TextView tvChatLog;
    private EditText etUserMessage;
    private Button btnSend;

    // We'll store conversation turns as role/text pairs, so we can send the full chat log.
    // Gemini expects "contents": [ { "role":"user"|"model", "parts":[{"text":"..."}] }, ... ]
    private final ArrayList<MessageTurn> conversation = new ArrayList<>();

    private final OkHttpClient httpClient = new OkHttpClient();
    private static final MediaType JSON
            = MediaType.get("application/json; charset=utf-8");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        tvChatLog = findViewById(R.id.tvChatLog);
        etUserMessage = findViewById(R.id.etUserMessage);
        btnSend = findViewById(R.id.btnSend);

        btnSend.setOnClickListener(v -> {
            String userMsg = etUserMessage.getText().toString().trim();
            if (userMsg.isEmpty()) {
                Toast.makeText(this, "Type something first", Toast.LENGTH_SHORT).show();
                return;
            }

            // Add user's message to conversation + UI
            appendMessageToChat("You", userMsg);
            conversation.add(new MessageTurn("user", userMsg));
            etUserMessage.setText("");

            // Call Gemini in background
            new Thread(() -> {
                try {
                    String reply = callGemini(conversation);
                    if (reply == null) reply = "(no reply)";

                    // Save model reply as a "model" turn
                    String finalReply = reply;
                    conversation.add(new MessageTurn("model", finalReply));

                    runOnUiThread(() -> appendMessageToChat("Gemini", finalReply));
                } catch (Exception e) {
                    runOnUiThread(() ->
                            Toast.makeText(ChatBotActivity.this,
                                    "Error: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show());
                }
            }).start();
        });
    }

    /** Append to on-screen chat log. */
    private void appendMessageToChat(String who, String text) {
        String existing = tvChatLog.getText().toString();
        String line = who + ": " + text + "\n\n";
        tvChatLog.setText(existing + line);
    }

    /**
     * Build request JSON body from the conversation.
     *
     * {
     *   "contents": [
     *      {"role":"user","parts":[{"text":"hi"}]},
     *      {"role":"model","parts":[{"text":"hello!"}]}
     *   ]
     * }
     *
     * This matches the v1beta generateContent contract for gemini-2.x models. :contentReference[oaicite:4]{index=4}
     */
    private JSONObject buildRequestBody(ArrayList<MessageTurn> convo) throws JSONException {
        JSONArray contents = new JSONArray();

        for (MessageTurn turn : convo) {
            JSONObject turnObj = new JSONObject();
            turnObj.put("role", turn.role);

            JSONArray parts = new JSONArray();
            JSONObject partObj = new JSONObject();
            partObj.put("text", turn.text);
            parts.put(partObj);

            turnObj.put("parts", parts);
            contents.put(turnObj);
        }

        JSONObject root = new JSONObject();
        root.put("contents", contents);
        return root;
    }

    /**
     * Talk to Gemini 2.0 Flash using OkHttp.
     * Returns model's text response.
     *
     * Response shape (simplified):
     * {
     *   "candidates":[
     *     {
     *       "content":{
     *          "parts":[{"text":"... model reply ..."}]
     *       }
     *     }
     *   ]
     * }
     *
     * Gemini 2.x Flash is optimized for speed and supports chat. :contentReference[oaicite:5]{index=5}
     */
    private String callGemini(ArrayList<MessageTurn> convo) throws IOException, JSONException {
        JSONObject requestJson = buildRequestBody(convo);

        RequestBody body = RequestBody.create(
                requestJson.toString(), JSON);

        Request request = new Request.Builder()
                .url(GEMINI_ENDPOINT + API_KEY)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            String respBody = response.body().string();
            return parseGeminiResponse(respBody);
        }
    }

    /** Pull the first candidate text out of Gemini response JSON. */
    private String parseGeminiResponse(String respJson) throws JSONException {
        JSONObject root = new JSONObject(respJson);

        if (!root.has("candidates")) return null;
        JSONArray candidates = root.getJSONArray("candidates");
        if (candidates.length() == 0) return null;

        JSONObject firstCand = candidates.getJSONObject(0);
        if (!firstCand.has("content")) return null;

        JSONObject content = firstCand.getJSONObject("content");
        if (!content.has("parts")) return null;

        JSONArray parts = content.getJSONArray("parts");
        if (parts.length() == 0) return null;

        JSONObject firstPart = parts.getJSONObject(0);

        // Gemini usually returns the generated text here.
        if (firstPart.has("text")) {
            return firstPart.getString("text");
        }

        // fallback if not present
        return firstPart.toString();
    }

    /** simple message-turn container */
    private static class MessageTurn {
        final String role; // "user" or "model"
        final String text;
        MessageTurn(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }
}
