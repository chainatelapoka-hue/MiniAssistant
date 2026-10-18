package com.example.miniasst;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Donald এর নিজস্ব স্মৃতি ও শেখানো স্কিল। অ্যান্ড্রয়েড ছাড়াই চলে, তাই আলাদাভাবে পরীক্ষা করা যায়।
 */
class MemoryStore {

    interface Storage {
        String get(String key);
        void put(String key, String value);
    }

    static final class Skill {
        final String name, trigger, reply;
        Skill(String name, String trigger, String reply) {
            this.name = name;
            this.trigger = trigger;
            this.reply = reply;
        }
    }

    private static final String K_MEM = "memories";
    private static final String K_SKILL = "skills";
    static final int MAX_MEMORIES = 500;
    static final int MAX_SKILLS = 100;
    static final int MAX_LEN = 2000;

    private final Storage storage;
    private final List<String> memories = new ArrayList<>();
    private final List<Skill> skills = new ArrayList<>();

    MemoryStore(Storage storage) {
        this.storage = storage;
        for (String line : nz(storage.get(K_MEM)).split("\n")) {
            if (!line.trim().isEmpty()) memories.add(line.replace('\u0001', '\n'));
        }
        for (String line : nz(storage.get(K_SKILL)).split("\n")) {
            String[] p = line.split("\t", 3);
            if (p.length == 3) skills.add(new Skill(p[0], p[1], p[2].replace('\u0001', '\n')));
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String clean(String s) {
        s = s.replace('\t', ' ').replace('\r', ' ').trim();
        if (s.length() > MAX_LEN) s = s.substring(0, MAX_LEN);
        return s;
    }

    private static String enc(String s) { return s.replace('\n', '\u0001'); }

    private void save() {
        StringBuilder m = new StringBuilder();
        for (String s : memories) m.append(enc(s)).append('\n');
        storage.put(K_MEM, m.toString());
        StringBuilder k = new StringBuilder();
        for (Skill s : skills) {
            k.append(s.name).append('\t').append(s.trigger).append('\t').append(enc(s.reply)).append('\n');
        }
        storage.put(K_SKILL, k.toString());
    }

    // ---------- স্মৃতি ----------

    /** নতুন তথ্য যোগ করে; null মানে সফল, নয়তো কারণ। */
    String addMemory(String text) {
        text = clean(text);
        if (text.isEmpty()) return "কী মনে রাখব সেটা লিখুন। যেমন: /add.memory আমার সাপ্লায়ারের নাম লি ওয়েই";
        for (String m : memories) if (m.equalsIgnoreCase(text)) return "এটা আমার আগেই মনে আছে।";
        if (memories.size() >= MAX_MEMORIES) return "স্মৃতি ভরে গেছে (" + MAX_MEMORIES + "টি)। পুরনো কিছু /memory.delete দিয়ে মুছুন।";
        memories.add(text);
        save();
        return null;
    }

    int memoryCount() { return memories.size(); }

    String listMemories() {
        if (memories.isEmpty()) return "স্মৃতিতে এখনো কিছু নেই। /add.memory <তথ্য> দিয়ে যোগ করুন।";
        StringBuilder sb = new StringBuilder("আমার স্মৃতি (" + memories.size() + "টি):");
        for (int i = 0; i < memories.size(); i++) {
            String m = memories.get(i);
            if (m.length() > 120) m = m.substring(0, 120) + "…";
            sb.append('\n').append(i + 1).append(". ").append(m);
        }
        return sb.toString();
    }

    String deleteMemory(String arg) {
        int n = parseIndex(arg, memories.size());
        if (n < 0) return "সঠিক নম্বর দিন। যেমন: /memory.delete 2 (তালিকা দেখতে /memory)";
        String gone = memories.remove(n);
        save();
        return "মুছে ফেলেছি: " + (gone.length() > 60 ? gone.substring(0, 60) + "…" : gone);
    }

    String clearMemories() {
        int n = memories.size();
        memories.clear();
        save();
        return n + "টি স্মৃতি মুছে ফেলেছি।";
    }

    private static int parseIndex(String arg, int size) {
        try {
            int n = Integer.parseInt(OfflineBrain.toAsciiDigits(arg.trim())) - 1;
            return n >= 0 && n < size ? n : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static final String[] STOP = {
        "কী", "কি", "কত", "কে", "কোন", "কোনো", "কেন", "কীভাবে", "আমার", "আমি", "তুমি", "এর", "এই", "ওই",
        "আছে", "হয়", "হবে", "করে", "করো", "বলো", "বল", "দাও", "দেও", "জানো", "মনে", "the", "is", "what", "my", "of", "a", "an"
    };

    private static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        for (String t : OfflineBrain.toAsciiDigits(s).toLowerCase(Locale.ROOT)
                .split("[\\s,.;:?!।()\"'/\\\\\\-–—_]+")) {
            if (t.length() < 2) continue;
            boolean stop = false;
            for (String w : STOP) if (w.equals(t)) { stop = true; break; }
            if (!stop) out.add(t);
        }
        return out;
    }

    private static boolean tokMatch(String a, String b) {
        if (a.equals(b)) return true;
        if (a.length() < 3 || b.length() < 3) return false;
        return a.contains(b) || b.contains(a);
    }

    /** প্রশ্নের সাথে সবচেয়ে মেলে এমন স্মৃতি; না মিললে null। */
    String recall(String query) {
        List<String> q = tokens(query);
        if (q.isEmpty()) return null;
        int bestScore = 0;
        List<String> best = new ArrayList<>();
        for (String m : memories) {
            List<String> mt = tokens(m);
            int matched = 0, score = 0;
            for (String t : q) {
                for (String x : mt) {
                    if (tokMatch(t, x)) { matched++; score += t.length(); break; }
                }
            }
            if (matched * 2 < q.size() || matched == 0) continue;
            if (score > bestScore) { bestScore = score; best.clear(); best.add(m); }
            else if (score == bestScore) best.add(m);
        }
        if (best.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("আমার স্মৃতি থেকে:");
        for (int i = 0; i < best.size() && i < 3; i++) sb.append("\n- ").append(best.get(i));
        return sb.toString();
    }

    // ---------- স্কিল ----------

    /** ফরম্যাট: নাম | ট্রিগার | উত্তর। null মানে সফল। */
    String addSkill(String arg) {
        String[] p = arg.split("\\|", 3);
        if (p.length < 3) return skillUsage();
        String name = clean(p[0]).replace('\n', ' ');
        String trig = clean(p[1]).replace('\n', ' ').toLowerCase(Locale.ROOT);
        String reply = clean(p[2]);
        if (name.isEmpty() || trig.isEmpty() || reply.isEmpty()) return skillUsage();
        for (int i = 0; i < skills.size(); i++) {
            if (skills.get(i).name.equalsIgnoreCase(name)) {
                skills.set(i, new Skill(name, trig, reply));
                save();
                return null;
            }
        }
        if (skills.size() >= MAX_SKILLS) return "স্কিল ভরে গেছে (" + MAX_SKILLS + "টি)। কিছু /skill.delete দিয়ে মুছুন।";
        skills.add(new Skill(name, trig, reply));
        save();
        return null;
    }

    static String skillUsage() {
        return "স্কিল শেখানোর নিয়ম:\n/skill.add নাম | ট্রিগার | উত্তর\n\n"
                + "উদাহরণ:\n/skill.add অফিস | অফিসের ঠিকানা | আমাদের অফিস ৫ নম্বর রোড, ঢাকা\n"
                + "/skill.add সালাম | সালাম দাও | ওয়ালাইকুম আসসালাম {rest}\n\n"
                + "প্রশ্নে ট্রিগার বাক্যটা থাকলেই স্কিল চলবে। উত্তরে {rest} লিখলে ট্রিগারের পরের অংশ বসবে।\n"
                + "দেখতে: /skill.list, মুছতে: /skill.delete <নম্বর বা নাম>";
    }

    String listSkills() {
        if (skills.isEmpty()) return "কোনো স্কিল শেখানো হয়নি।\n\n" + skillUsage();
        StringBuilder sb = new StringBuilder("শেখানো স্কিল (" + skills.size() + "টি):");
        for (int i = 0; i < skills.size(); i++) {
            Skill s = skills.get(i);
            String r = s.reply.length() > 60 ? s.reply.substring(0, 60) + "…" : s.reply;
            sb.append('\n').append(i + 1).append(". ").append(s.name).append(" — যখন: \"").append(s.trigger)
                    .append("\" → ").append(r);
        }
        return sb.toString();
    }

    String deleteSkill(String arg) {
        arg = arg.trim();
        int n = parseIndex(arg, skills.size());
        if (n < 0) {
            for (int i = 0; i < skills.size(); i++) if (skills.get(i).name.equalsIgnoreCase(arg)) { n = i; break; }
        }
        if (n < 0) return "স্কিল পাইনি। /skill.list দেখে নম্বর বা নাম দিন।";
        Skill s = skills.remove(n);
        save();
        return "স্কিল মুছেছি: " + s.name;
    }

    /** প্রশ্নে কোনো স্কিলের ট্রিগার থাকলে তার উত্তর; সবচেয়ে লম্বা ট্রিগার জেতে। */
    String runSkill(String query) {
        String q = OfflineBrain.toAsciiDigits(query).toLowerCase(Locale.ROOT);
        Skill best = null;
        int pos = -1;
        for (Skill s : skills) {
            String t = OfflineBrain.toAsciiDigits(s.trigger);
            int i = q.indexOf(t);
            if (i >= 0 && (best == null || t.length() > OfflineBrain.toAsciiDigits(best.trigger).length())) {
                best = s;
                pos = i + t.length();
            }
        }
        if (best == null) return null;
        String rest = query.length() >= pos ? query.substring(Math.min(pos, query.length())).trim() : "";
        return best.reply.replace("{rest}", rest).trim();
    }
}
