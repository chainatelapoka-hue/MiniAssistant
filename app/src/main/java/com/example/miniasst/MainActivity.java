package com.example.miniasst;

import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.LinearLayout;
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
    private static final String PREF_ONLINE = "online_enabled";
    private static final String PREF_RMB = "rate_rmb";
    private static final String PREF_USD = "rate_usd";
    private static final int MAX_HISTORY = 20;

    private static final int MENU_RATES = 1;
    private static final int MENU_API_KEY = 2;
    private static final int MENU_ONLINE = 3;
    private static final int MENU_CLEAR = 4;

    private EditText inputEditText;
    private TextView chatLogTextView;
    private ScrollView scrollView;
    private Button sendButton;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final JSONArray history = new JSONArray();
    private boolean busy = false;

    private OfflineBrain brain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputEditText = findViewById(R.id.inputEditText);
        chatLogTextView = findViewById(R.id.chatLogTextView);
        scrollView = findViewById(R.id.scrollView);
        sendButton = findViewById(R.id.sendButton);

        brain = new OfflineBrain(new OfflineBrain.Rates() {
            @Override
            public double rmbToBdt() {
                return getRate(PREF_RMB);
            }

            @Override
            public double usdToBdt() {
                return getRate(PREF_USD);
            }
        });

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

    // ---------- সেটিংস ----------

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private String getApiKey() {
        return prefs().getString(PREF_API_KEY, "").trim();
    }

    private boolean isOnlineEnabled() {
        return prefs().getBoolean(PREF_ONLINE, true);
    }

    private double getRate(String key) {
        try {
            String s = OfflineBrain.toAsciiDigits(prefs().getString(key, "")).replace(",", "").trim();
            if (s.isEmpty()) return 0;
            double v = Double.parseDouble(s);
            return v > 0 ? v : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---------- মেনু ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(0, MENU_RATES, 0, "রেট সেট করুন (ইউয়ান/ডলার)");
        menu.add(0, MENU_ONLINE, 1,
                isOnlineEnabled() ? "Claude অনলাইন: চালু (বন্ধ করুন)" : "Claude অনলাইন: বন্ধ (চালু করুন)");
        menu.add(0, MENU_API_KEY, 2, "API কী সেট করুন");
        menu.add(0, MENU_CLEAR, 3, "কথোপকথন মুছুন");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == MENU_RATES) {
            showRatesDialog();
            return true;
        } else if (id == MENU_API_KEY) {
            showApiKeyDialog();
            return true;
        } else if (id == MENU_ONLINE) {
            boolean now = !isOnlineEnabled();
            prefs().edit().putBoolean(PREF_ONLINE, now).apply();
            Toast.makeText(this,
                    now ? "Claude অনলাইন চালু হয়েছে" : "অনলাইন বন্ধ, এখন শুধু অফলাইনে চলবে",
                    Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == MENU_CLEAR) {
            chatLogTextView.setText("");
            while (history.length() > 0) {
                history.remove(0);
            }
            showWelcome();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showRatesDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);

        final EditText rmb = new EditText(this);
        rmb.setHint("১ ইউয়ান = কত টাকা");
        rmb.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        double curRmb = getRate(PREF_RMB);
        if (curRmb > 0) rmb.setText(String.valueOf(curRmb));

        final EditText usd = new EditText(this);
        usd.setHint("১ ডলার = কত টাকা");
        usd.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        double curUsd = getRate(PREF_USD);
        if (curUsd > 0) usd.setText(String.valueOf(curUsd));

        box.addView(rmb);
        box.addView(usd);

        new AlertDialog.Builder(this)
                .setTitle("আপনার রেট (টাকায়)")
                .setMessage("আজকের রেট নিজে বসান। ফাঁকা রাখলে রেট মুছে যাবে।")
                .setView(box)
                .setPositiveButton("সেভ", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        prefs().edit()
                                .putString(PREF_RMB, rmb.getText().toString().trim())
                                .putString(PREF_USD, usd.getText().toString().trim())
                                .apply();
                        Toast.makeText(MainActivity.this, "রেট সেভ হয়েছে", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("বাতিল", null)
                .show();
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
                .setPositiveButton("সেভ", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String key = keyInput.getText().toString().trim();
                        prefs().edit().putString(PREF_API_KEY, key).apply();
                        Toast.makeText(MainActivity.this,
                                key.isEmpty() ? "কী মুছে ফেলা হয়েছে" : "কী সেভ হয়েছে",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("বাতিল", null)
                .show();
    }

    // ---------- চ্যাট ----------

    private void showWelcome() {
        appendChat("Donald: হ্যালো! আমি Donald। ইন্টারনেট ছাড়াই আমি হিসাব, মুদ্রা রূপান্তর, CBM, "
                + "আমদানি-রপ্তানির নিয়ম ও ব্যবসার সূত্র জানি। 'help' লিখে দেখুন আমি কী কী পারি।");
        if (getRate(PREF_RMB) <= 0 && getRate(PREF_USD) <= 0) {
            appendChat("Donald: মুদ্রা হিসাবের জন্য ⋮ মেনু → 'রেট সেট করুন' এ আজকের রেট বসিয়ে নিন।");
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

    private void processQuery(final String query) {
        // ১) আগে অফলাইনে চেষ্টা
        String offline = brain.answer(query);
        if (offline != null) {
            appendChat("Donald: " + offline);
            return;
        }

        // ২) অনলাইন বন্ধ বা কী না থাকলে ফলব্যাক
        final String apiKey = getApiKey();
        if (!isOnlineEnabled() || apiKey.isEmpty()) {
            appendChat("Donald: এই প্রশ্নের উত্তর আমার অফলাইন জ্ঞানে নেই। 'help' লিখে দেখুন আমি কী কী পারি।"
                    + (apiKey.isEmpty() ? "\n(আরও প্রশ্নের জন্য ⋮ মেনু থেকে API কী দিতে পারেন।)" : ""));
            return;
        }

        // ৩) Claude এর কাছে
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
                    answer = "ইন্টারনেট সংযোগ নেই। এই প্রশ্নের উত্তর অফলাইনে জানা নেই। 'help' লিখে দেখুন।";
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
        final boolean credit;

        ApiException(int code, String message, boolean credit) {
            super(message);
            this.code = code;
            this.credit = credit;
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
            // ক্রেডিট সমস্যা হলে আবার চেষ্টা অর্থহীন; ওয়েব সার্চ চালু না থাকলে সার্চ ছাড়া চেষ্টা
            if ((e.code == 400 || e.code == 403) && !e.credit) {
                String fallback = callApi(apiKey, false);
                return fallback + "\n\n(ওয়েব সার্চ বন্ধ থাকায় এটা আমার নিজের জ্ঞান থেকে দেওয়া উত্তর।)";
            }
            throw e;
        }
    }

    private String callApi(String apiKey, boolean withSearch) throws Exception {
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
                throw new ApiException(500, "অপ্রত্যাশিত উত্তর এসেছে।", false);
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
                throw new ApiException(500, "কোনো উত্তর পাওয়া যায়নি। আবার চেষ্টা করুন।", false);
            }
            return text;
        }
        throw new ApiException(500, "উত্তর শেষ করতে পারিনি। আবার চেষ্টা করুন।", false);
    }

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
                boolean credit = raw.toLowerCase(Locale.ROOT).contains("credit balance");
                throw new ApiException(code, friendlyError(code, raw, credit), credit);
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

    private String friendlyError(int code, String raw, boolean credit) {
        if (credit) {
            return "Claude এর ক্রেডিট নেই বা শেষ। ক্রেডিট যোগ করলে আবার কাজ করবে। "
                    + "ততদিন ⋮ মেনু থেকে 'Claude অনলাইন' বন্ধ রেখে অফলাইনে চালাতে পারেন।";
        }
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
            case 403:
                return "এই কী দিয়ে অনুমতি নেই। " + detail;
            case 429:
                return "অল্প সময়ে অনেক অনুরোধ হয়ে গেছে। একটু পরে আবার চেষ্টা করুন।";
            case 529:
                return "Claude এখন ব্যস্ত। একটু পরে আবার চেষ্টা করুন।";
            default:
                return "ত্রুটি (" + code + "): " + detail;
        }
    }
}
