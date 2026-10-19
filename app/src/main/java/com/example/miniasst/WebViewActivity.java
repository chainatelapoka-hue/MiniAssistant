package com.example.miniasst;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;

/** Donald এর ভেতরেই সাইট দেখানোর পাতা। শুধু http/https চলে। */
public class WebViewActivity extends AppCompatActivity {

    static final String EXTRA_URL = "url";
    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        final ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        root.addView(bar, new FrameLayout.LayoutParams(-1, (int) (4 * getResources().getDisplayMetrics().density)));
        setContentView(root);

        WebSettings st = web.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setAllowFileAccess(false);
        st.setAllowContentAccess(false);
        st.setBuiltInZoomControls(true);
        st.setDisplayZoomControls(false);
        st.setLoadWithOverviewMode(true);
        st.setUseWideViewPort(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString().toLowerCase();
                // শুধু http/https এখানে লোড হবে; অন্য স্কিম (intent:, file: ...) আটকানো
                return !(u.startsWith("http://") || u.startsWith("https://"));
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? android.view.View.GONE : android.view.View.VISIBLE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (title != null) setTitle(title);
            }
        });

        String url = getIntent().getStringExtra(EXTRA_URL);
        String safe = url == null ? null : ActionParser.normalizeUrl(url);
        if (safe == null) {
            finish();
            return;
        }
        web.loadUrl(safe);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }
}
