package com.example.miniasst;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText inputEditText;
    private TextView chatLogTextView;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputEditText = findViewById(R.id.inputEditText);
        chatLogTextView = findViewById(R.id.chatLogTextView);
        scrollView = findViewById(R.id.scrollView);
        Button sendButton = findViewById(R.id.sendButton);

        appendChat("এজেন্ট: হ্যালো! আমি আপনার অফলাইন মিনি অ্যাসিস্ট্যান্ট। কীভাবে সাহায্য করতে পারি?");

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = inputEditText.getText().toString().trim();
                if (!query.isEmpty()) {
                    appendChat("আপনি: " + query);
                    inputEditText.setText("");
                    processQuery(query);
                }
            }
        });
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

    // যেকোনো একটি শব্দাংশ মিললেই true
    private boolean hasAny(String q, String... keys) {
        for (String k : keys) {
            if (q.contains(k)) return true;
        }
        return false;
    }

    private void processQuery(String query) {
        String q = query.toLowerCase();
        String response = "দুঃখিত, এই বিষয়টি আমার লোকাল মেমরিতে নেই।";

        // নির্দিষ্ট বাক্যাংশ আগে, সাধারণ শব্দ পরে
        if (hasAny(q, "কেমন আছ", "কেমন আছেন", "how are you")) {
            response = "আমি ভালো আছি! আপনার ফোনেই অফলাইনে কাজ করছি।";
        } else if (hasAny(q, "তোমার নাম", "আপনার নাম", "your name", "নাম কী", "নাম কি")) {
            response = "আমার নাম লোকাল মিনি অ্যাসিস্ট্যান্ট (Mini Assistant)।";
        } else if (hasAny(q, "ধন্যবাদ", "thanks", "thank you")) {
            response = "আপনাকেও অনেক ধন্যবাদ!";
        } else if (hasAny(q, "হাই", "হ্যালো", "hello")) {
            response = "হ্যালো! বলুন, কীভাবে সাহায্য করতে পারি?";
        }

        appendChat("এজেন্ট: " + response);
    }
}
