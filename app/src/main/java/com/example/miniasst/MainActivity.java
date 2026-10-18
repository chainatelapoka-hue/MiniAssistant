package com.example.miniasst;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-5-5";
    private static final String PREFS = "donald_prefs";
    private static final String PREF_API_KEY = "api_key";
    private static final int MAX_HISTORY = 20;

    private static final int MENU_SET_KEY = 1;
    private static final int MENU_CLEAR = 2;

    private EditText inputEditText;
    private TextView chatLogTextView;
    private ScrollView scrollView;
    private Button sendButton;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final JSONArray history = new JSONArray();
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputEditText = findViewById(R.id.inputEditText);
        chatLogTextView = findViewById(R.id.chatLogTextView);
        scrollView = findViewById(R.id.scrollView);
        sendButton = findViewById(R.id.sendButton);

        showWelcome();

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCurrentInput();
            }
        });

        inputEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendCurrentInput();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // ---------- মেনু ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SET_KEY, 0, "API কী সেট করুন");
        menu.add(0, MENU_CLEAR, 1, "কথোপকথন মুছুন");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_SET_KEY) {
            showApiKeyDialog();
            return true;
        } else if (item.getItemId() == MENU_CLEAR) {
            chatLogTextView.setText("");
            while (history.length() > 0) {
                history.remove(0);
            }
            showWelcome();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showApiKeyDialog() {
        final EditText keyInput = new EditText(this);
        keyInput.setHint("sk-ant-...");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setSingleLine(true);

        new AlertDialog.Builder(this)
                .setTitle("Anthropic API কী")
                .setMessage("কী শুধু এই ফোনেই থাকবে। ফাঁকা রেখে সেভ করলে কী মুছে যাবে।")
                .setView(keyInput)
                .setPositiveButton("সেভ", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String key = keyInput.getText().toString().trim();
                        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                .edit().putString(PREF_API_KEY, key).apply();
                        Toast.makeText(MainActivity.this,
                                key.isEmpty() ? "কী মুছে ফেলা হয়েছে" : "কী সেভ হয়েছে",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("বাতিল", null)
                .show();
    }

    private String getApiKey() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_API_KEY, "").trim();
    }

    // ---------- চ্যাট ----------

    private void showWelcome() {
        appendChat("Donald: হ্যালো! আমি Donald, আপনার সহকারী। যেকোনো বিষয়ে প্রশ্ন করুন, ইন্টারনেটে খুঁজে উত্তর দেব।");
        if (getApiKey().isEmpty()) {
            appendChat("Donald: শুরুর আগে ওপরের ⋮ মেনু থেকে \"API কী সেট করুন\" চাপুন। কী ছাড়া আমি শুধু সহজ কয়েকটা কথার উত্তর দিতে পারি।");
        }
    }

    private void sendCurrentInput() {
        if (busy) {
            return;
        }
        String query = inputEditText.getText().toString().trim();
        if (query.isEmpty()) {
            return;
        }
        appendChat("আপনি: " + query);
        inputEditText.setText("");
        processQuery(query);
    }

    private void appendChat(String text) {
        chatLogTextView.append(text + "\n\n");
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        sendButton.setEnabled(!value);
        sendButton.setText(value ? "অপেক্ষা…" : "পাঠান");
    }

    // যেকোনো একটি শব্দাংশ মিললেই true
    private boolean hasAny(String q, String... keys) {
        for (String k : keys) {
            if (q.contains(k)) return true;
        }
        return false;
    }

    // অফলাইনে জানা উত্তর; না পেলে null
    private String offlineAnswer(String query) {
        String q = query.toLowerCase();
        if (hasAny(q, "কেমন আছ", "কেমন আছেন", "how are you")) {
            return "আমি ভালো আছি! আপনি কেমন আছেন?";
        } else if (hasAny(q, "তোমার নাম", "আপনার নাম", "your name", "নাম কী", "নাম কি")) {
            return "আমার নাম Donald।";
        } else if (hasAny(q, "ধন্যবাদ", "thanks", "thank you")) {
            return "আপনাকেও অনেক ধন্যবাদ!";
        } else if (hasAny(q, "হাই", "হ্যালো", "hello")) {
            return "হ্যালো! বলুন, কীভাবে সাহায্য করতে পারি?";
        }
        return null;
    }

    private void processQuery(final String query) {
        String offline = offlineAnswer(query);
        if (offline != null) {
            appendChat("Donald: " + offline);
            return;
        }

        final String apiKey = getApiKey();
        if (apiKey.isEmpty()) {
            appendChat("Donald: এই প্রশ্নের উত্তরের জন্য API কী লাগবে। ওপরের ⋮ মেনু থেকে \"API কী সেট করুন\" চাপুন।");
            return;
        }

        // পুরোনো কথোপকথন ছোট রাখা (জোড়ায় জোড়ায় বাদ)
        while (history.length() > MAX_HISTORY) {
            history.remove(0);
            history.remove(0);
        }
        try {
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", query);
            history.put(userMsg);
        } catch (Exception e) {
            appendChat("Donald: দুঃখিত, প্রশ্নটা তৈরি করতে সমস্যা হয়েছে।");
            return;
        }

        setBusy(true);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                String answer;
                boolean ok = true;
                try {
                    answer = askClaude(apiKey);
                } catch (ApiException e) {
                    ok = false;
                    answer = e.getMessage();
                } catch (java.net.UnknownHostException e) {
                    ok = false;
                    answer = "ইন্টারনেট সংযোগ নেই। ইন্টারনেট চালু করে আবার চেষ্টা করুন।";
                } catch (java.net.SocketTimeoutException e) {
                    ok = false;
                    answer = "উত্তর আসতে অনেক দেরি হচ্ছে। একটু পরে আবার চেষ্টা করুন।";
                } catch (Exception e) {
                    ok = false;
                    answer = "সমস্যা হয়েছে: " + e.getClass().getSimpleName();
                }

                final boolean success = ok;
                final String finalAnswer = answer;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            try {
                                JSONObject assistantMsg = new JSONObject();
                                assistantMsg.put("role", "assistant");
                                assistantMsg.put("content", finalAnswer);
                                history.put(assistantMsg);
                            } catch (Exception ignored) {
                            }
                        } else {
                            // ব্যর্থ হলে শেষ প্রশ্নটা ইতিহাস থেকে সরানো, যাতে পরের বার ঠিক থাকে
                            if (history.length() > 0) {
                                history.remove(history.length() - 1);
                            }
                        }
                        appendChat("Donald: " + finalAnswer);
                        setBusy(false);
                    }
                });
            }
        });
    }

    // ---------- Claude API ----------

    private static class ApiException extends Exception {
        final int code;

        ApiException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    private String buildSystemPrompt() {
        String today = new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(new Date());
        return "You are Donald, a helpful general-purpose assistant inside a mobile app. "
                + "Today's date is " + today + ". "
                + "Answer in the same language the user writes in; default to Bengali. "
                + "For questions about current events, prices, news, or any fact that may have changed, "
                + "search the web first, then give one clear, well-organized final answer. "
                + "Do not describe your searching process. "
                + "Keep answers concise and easy to read on a phone. "
                + "Use plain text only: no markdown symbols such as ** or #. "
                + "Use short paragraphs, and a simple '-' at the start of a line for lists. "
                + "When you used web sources, end with a short line 'সূত্র:' naming the main sites.";
    }

    private String askClaude(String apiKey) throws Exception {
        try {
            return callApi(apiKey, true);
        } catch (ApiException e) {
            // ওয়েব সার্চ চালু না থাকলে সার্চ ছাড়া আবার চেষ্টা
            if (e.code == 400 || e.code == 403) {
                String fallback = callApi(apiKey, false);
                return fallback + "\n\n(ওয়েব সার্চ বন্ধ থাকায় এটা আমার নিজের জ্ঞান থেকে দেওয়া উত্তর।)";
            }
            throw e;
        }
    }

    private String callApi(String apiKey, boolean withSearch) throws Exception {
        // স্থানীয় কপি, যাতে pause_turn এ ফেরত পাঠানো যায়
        JSONArray messages = new JSONArray(history.toString());

        for (int attempt = 0; attempt < 4; attempt++) {
            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            body.put("max_tokens", 2048);
            body.put("system", buildSystemPrompt());
            body.put("messages", messages);
            if (withSearch) {
                JSONObject tool = new JSONObject();
                tool.put("type", "web_search_20250305");
                tool.put("name", "web_search");
                tool.put("max_uses", 5);
                body.put("tools", new JSONArray().put(tool));
            }

            JSONObject response = postJson(apiKey, body);
            JSONArray content = response.optJSONArray("content");
            if (content == null) {
                throw new ApiException(500, "অপ্রত্যাশিত উত্তর এসেছে।");
            }

            if ("pause_turn".equals(response.optString("stop_reason"))) {
                JSONObject assistant = new JSONObject();
                assistant.put("role", "assistant");
                assistant.put("content", content);
                messages.put(assistant);
                continue;
            }

            String text = extractFinalText(content);
            if (text.isEmpty()) {
                throw new ApiException(500, "কোনো উত্তর পাওয়া যায়নি। আবার চেষ্টা করুন।");
            }
            return text;
        }
        throw new ApiException(500, "উত্তর শেষ করতে পারিনি। আবার চেষ্টা করুন।");
    }

    // সার্চের পরের টেক্সটই চূড়ান্ত উত্তর; সার্চ না হলে সব টেক্সট
    private String extractFinalText(JSONArray content) {
        int lastNonText = -1;
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block != null && !"text".equals(block.optString("type"))) {
                lastNonText = i;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = lastNonText + 1; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block != null && "text".equals(block.optString("type"))) {
                sb.append(block.optString("text"));
            }
        }
        return sb.toString().trim();
    }

    private JSONObject postJson(String apiKey, JSONObject body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
            conn.setDoOutput(true);
            conn.setRequestProperty("content-type", "application/json");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(payload);
            os.close();

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String raw = readAll(is);

            if (code < 200 || code >= 300) {
                throw new ApiException(code, friendlyError(code, raw));
            }
            return new JSONObject(raw);
        } finally {
            conn.disconnect();
        }
    }

    private String readAll(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        is.close();
        return out.toString("UTF-8");
    }

    private String friendlyError(int code, String raw) {
        String detail = "";
        try {
            JSONObject err = new JSONObject(raw).optJSONObject("error");
            if (err != null) {
                detail = err.optString("message");
            }
        } catch (Exception ignored) {
        }
        switch (code) {
            case 401:
                return "API কী ঠিক নয়। ⋮ মেনু থেকে কীটা আবার দিন।";
            case 402:
            case 403:
                return "এই কী দিয়ে অনুমতি নেই বা ক্রেডিট শেষ। console.anthropic.com এ দেখুন। " + detail;
            case 429:
                return "অল্প সময়ে অনেক অনুরোধ হয়ে গেছে। একটু পরে আবার চেষ্টা করুন।";
            case 529:
                return "Claude এখন ব্যস্ত। একটু পরে আবার চেষ্টা করুন।";
            default:
                return "ত্রুটি (" + code + "): " + detail;
        }
    }
}
