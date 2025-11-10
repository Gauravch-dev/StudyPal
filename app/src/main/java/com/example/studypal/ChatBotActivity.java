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
 * ChatBotActivity — Gemini 2.x Flash/Pro integration.
 * -----------------------------------------------
 * ✔ Uses Google Generative Language API (v1beta)
 * ✔ Keeps chat context (conversation history)
 * ✔ Handles all response shapes (text / inline_data / function_call)
 * ✔ Shows fallback debug output if parsing fails
 */
public class ChatBotActivity extends AppCompatActivity {

    // ⚠️ Your Gemini API key (demo purpose only)
    private final String API_KEY = "AIzaSyCcM4fVzAypuB-UrRxOU1L4NlFnKwyJzyQ";

    // ✅ Choose any of these:
    // private static final String MODEL = "gemini-2.0-flash";
    // private static final String MODEL = "gemini-2.0-pro";
    private static final String MODEL = "gemini-2.0-flash";

    // ✅ Correct endpoint format
    private static final String GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent?key=";

    private TextView tvChatLog;
    private EditText etUserMessage;
    private Button btnSend;

    private final ArrayList<MessageTurn> conversation = new ArrayList<>();

    private final OkHttpClient httpClient = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        tvChatLog = findViewById(R.id.tvChatLog);
        etUserMessage = findViewById(R.id.etUserMessage);
        btnSend = findViewById(R.id.btnSend);

        // ✅ Add system role ONCE here
        conversation.add(new MessageTurn("user",
                "System prompt: You are StudyPal AI — a friendly and knowledgeable academic assistant. " +
                        "You help students plan study schedules, explain difficult topics, summarize lessons, " +
                        "and motivate them with short productivity advice. Keep responses short, clear, and positive."));

        btnSend.setOnClickListener(v -> {
            String userMsg = etUserMessage.getText().toString().trim();
            if (userMsg.isEmpty()) {
                Toast.makeText(this, "Type something first", Toast.LENGTH_SHORT).show();
                return;
            }

            appendMessageToChat("You", userMsg);
            conversation.add(new MessageTurn("user", userMsg));
            etUserMessage.setText("");

            new Thread(() -> {
                try {
                    String reply = callGemini(conversation);
                    if (reply == null) reply = "(no reply)";
                    String finalReply = reply;
                    conversation.add(new MessageTurn("model", finalReply));
                    runOnUiThread(() -> appendMessageToChat("StudyPal AI", finalReply));
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() ->
                            appendMessageToChat("StudyPal AI", "⚠️ Error: " + e.getMessage()));
                }
            }).start();
        });
    }

    /** Append message nicely to chat window */
    private void appendMessageToChat(String who, String text) {
        runOnUiThread(() -> {
            String existing = tvChatLog.getText().toString();
            tvChatLog.setText(existing + who + ": " + text + "\n\n");
        });
    }

    /** Build Gemini request JSON */
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

    /** Core network call */
    private String callGemini(ArrayList<MessageTurn> convo) throws IOException, JSONException {
        ArrayList<MessageTurn> requestConvo = new ArrayList<>(convo);
        JSONObject requestJson = buildRequestBody(requestConvo);

        RequestBody body = RequestBody.create(requestJson.toString(), JSON);
        Request request = new Request.Builder()
                .url(GEMINI_ENDPOINT + API_KEY)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "(no response body)";
            int code = response.code();
            boolean ok = response.isSuccessful();

            if (!ok) {
                return "⚠️ Gemini API error (HTTP " + code + ")\n\n" + respBody;
            }

            // Try to parse; if it fails, show raw response
            try {
                String parsed = parseGeminiResponse(respBody);
                return (parsed == null || parsed.trim().isEmpty()) ? respBody : parsed;
            } catch (Exception e) {
                return "⚠️ Parsing failed: " + e.getMessage() + "\n\nRaw:\n" + respBody;
            }
        } catch (Exception ex) {
            String msg = "❌ Exception: " + ex.getClass().getSimpleName() + " → " + ex.getMessage();
            runOnUiThread(() -> appendMessageToChat("ERROR", msg));
            throw new IOException(msg, ex);
        }
    }

    /** Robust response parser for all valid Gemini formats */
    private String parseGeminiResponse(String respJson) throws JSONException {
        JSONObject root = new JSONObject(respJson);
        if (root.has("error")) {
            JSONObject err = root.getJSONObject("error");
            return "❌ API Error: " + err.optString("message", "Unknown error");
        }

        if (!root.has("candidates")) return "(no candidates)";
        JSONArray candidates = root.getJSONArray("candidates");
        if (candidates.length() == 0) return "(empty candidates)";

        JSONObject cand = candidates.getJSONObject(0);
        if (!cand.has("content")) return "(no content)";
        JSONObject content = cand.getJSONObject("content");

        StringBuilder out = new StringBuilder();
        if (content.has("parts")) {
            JSONArray parts = content.getJSONArray("parts");
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i);
                if (part.has("text")) out.append(part.getString("text"));
                else if (part.has("inline_data")) out.append("[inline data]");
                else if (part.has("functionCall")) out.append("[function call]");
                else out.append(part.toString());
            }
        } else if (content.has("text")) {
            out.append(content.getString("text"));
        }

        if (out.length() == 0) out.append("(no text found)");
        return out.toString().trim();
    }

    /** Simple message container */
    private static class MessageTurn {
        final String role; // "user" or "model" or "system"
        final String text;
        MessageTurn(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }
}
