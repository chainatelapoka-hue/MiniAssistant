package com.example.miniasst;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;

/** বিনামূল্যের অনলাইন উৎস (উইকিপিডিয়া)। কোনো API কী বা ক্রেডিট লাগে না। */
class WebSearch {

    static final class Result {
        final String title, text, url;
        Result(String title, String text, String url) {
            this.title = title;
            this.text = text;
            this.url = url;
        }
    }

    static Result search(String query) throws IOException {
        boolean ascii = true;
        for (int i = 0; i < query.length(); i++) if (query.charAt(i) > 127) { ascii = false; break; }
        String[] langs = ascii ? new String[]{"en", "bn"} : new String[]{"bn", "en"};
        IOException last = null;
        for (String lang : langs) {
            try {
                Result r = fetch(lang, query);
                if (r != null) return r;
            } catch (IOException e) {
                last = e;
            }
        }
        if (last != null) throw last;
        return null;
    }

    private static Result fetch(String lang, String query) throws IOException {
        String url = "https://" + lang + ".wikipedia.org/w/api.php?action=query&generator=search"
                + "&gsrsearch=" + URLEncoder.encode(query, "UTF-8")
                + "&gsrlimit=1&prop=extracts%7Cinfo&exintro=1&explaintext=1&exchars=900&exlimit=1"
                + "&inprop=url&format=json&utf8=1&redirects=1";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "DonaldAssistant/1.0 (personal Android app)");
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            return parse(readAll(conn.getInputStream()));
        } finally {
            conn.disconnect();
        }
    }

    static Result parse(String raw) throws IOException {
        try {
            JSONObject pages = new JSONObject(raw).optJSONObject("query");
            if (pages == null) return null;
            pages = pages.optJSONObject("pages");
            if (pages == null) return null;
            JSONObject best = null;
            int bestIdx = Integer.MAX_VALUE;
            Iterator<String> it = pages.keys();
            while (it.hasNext()) {
                JSONObject p = pages.optJSONObject(it.next());
                if (p == null) continue;
                int idx = p.optInt("index", Integer.MAX_VALUE - 1);
                if (idx < bestIdx) { bestIdx = idx; best = p; }
            }
            if (best == null) return null;
            String text = best.optString("extract", "").trim();
            if (text.isEmpty()) return null;
            return new Result(best.optString("title"), text, best.optString("fullurl"));
        } catch (org.json.JSONException e) {
            throw new IOException("bad response");
        }
    }

    private static String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        is.close();
        return out.toString("UTF-8");
    }
}
