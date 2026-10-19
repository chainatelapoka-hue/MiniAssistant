package com.example.miniasst;

import android.content.Context;
import android.content.SharedPreferences;

/** MainActivity ও WebViewActivity একই স্মৃতি-ভান্ডার খোলে। */
final class Stores {
    private Stores() {}

    static MemoryStore open(Context ctx) {
        final SharedPreferences sp = ctx.getApplicationContext()
                .getSharedPreferences("donald_prefs", Context.MODE_PRIVATE);
        return new MemoryStore(new MemoryStore.Storage() {
            @Override
            public String get(String key) {
                return sp.getString("mem_" + key, "");
            }

            @Override
            public void put(String key, String value) {
                sp.edit().putString("mem_" + key, value).apply();
            }
        });
    }
}
