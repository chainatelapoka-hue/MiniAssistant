package com.example.miniasst;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Donald এর অফলাইন মস্তিষ্ক: ইন্টারনেট ছাড়া হিসাব, মুদ্রা, CBM, তারিখ-সময়
 * এবং আমদানি-রপ্তানি ও ব্যবসার জানা উত্তর।
 */
final class OfflineBrain {

    /** ব্যবহারকারীর সেট করা রেট (টাকায়)। সেট না থাকলে 0। */
    interface Rates {
        double rmbToBdt();

        double usdToBdt();
    }

    private static final int BDT = 0;
    private static final int RMB = 1;
    private static final int USD = 2;

    private static final class Entry {
        final String answer;
        final String[] keys;

        Entry(String answer, String[] keys) {
            this.answer = answer;
            this.keys = keys;
        }
    }

    private final Rates rates;
    private final List<Entry> entries = new ArrayList<>();

    OfflineBrain(Rates rates) {
        this.rates = rates;
        buildKnowledge();
    }

    /** উত্তর জানা থাকলে টেক্সট, নইলে null। */
    String answer(String rawQuery) {
        String q = normalize(rawQuery);
        String r = dateTime(q);
        if (r != null) return r;
        r = cbm(q);
        if (r != null) return r;
        r = currency(q);
        if (r != null) return r;
        r = arithmetic(q);
        if (r != null) return r;
        return knowledge(q);
    }

    // ---------- সাধারণ সহায়ক ----------

    static String toAsciiDigits(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '০' && c <= '৯') {
                sb.append((char) ('0' + (c - '০')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static String toBn(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9') {
                sb.append((char) ('০' + (c - '0')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String normalize(String s) {
        String t = toAsciiDigits(s.toLowerCase(Locale.ROOT));
        return t.replaceAll("(?<=\\d),(?=\\d)", "").trim();
    }

    private static boolean hasAny(String q, String... keys) {
        for (String k : keys) {
            if (q.contains(k)) return true;
        }
        return false;
    }

    private static Set<String> tokens(String q) {
        Set<String> set = new HashSet<>();
        for (String t : q.split("[^\\p{L}\\p{M}\\p{N}]+")) {
            if (!t.isEmpty()) set.add(t);
        }
        return set;
    }

    private static List<Double> numbers(String q) {
        List<Double> out = new ArrayList<>();
        Matcher m = Pattern.compile("\\d+(?:\\.\\d+)?").matcher(q);
        while (m.find()) {
            try {
                out.add(Double.parseDouble(m.group()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    /** বাংলাদেশি কমা (লাখ-কোটি) সহ বাংলা অঙ্কে সংখ্যা। */
    static String num(double v, int maxDecimals) {
        boolean neg = v < 0;
        v = Math.abs(v);
        StringBuilder pattern = new StringBuilder("0");
        if (maxDecimals > 0) {
            pattern.append('.');
            for (int i = 0; i < maxDecimals; i++) pattern.append('#');
        }
        String s = new DecimalFormat(pattern.toString(), new DecimalFormatSymbols(Locale.US)).format(v);
        String[] parts = s.split("\\.");
        String ip = parts[0];
        int n = ip.length();
        if (n > 3) {
            String last3 = ip.substring(n - 3);
            String rest = ip.substring(0, n - 3);
            StringBuilder r = new StringBuilder();
            int cnt = 0;
            for (int i = rest.length() - 1; i >= 0; i--) {
                r.append(rest.charAt(i));
                cnt++;
                if (cnt % 2 == 0 && i > 0) r.append(',');
            }
            ip = r.reverse().toString() + "," + last3;
        }
        String out = (neg ? "-" : "") + ip + (parts.length > 1 ? "." + parts[1] : "");
        return toBn(out);
    }

    // ---------- তারিখ ও সময় ----------

    private static final String[] DAYS = {
            "রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার"
    };
    private static final String[] MONTHS = {
            "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
            "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    };

    private String dateTime(String q) {
        boolean wantsTime = hasAny(q, "কয়টা বাজে", "কত বাজে", "এখন সময় কত", "what time", "current time");
        boolean wantsDate = hasAny(q, "আজকের তারিখ", "আজ কত তারিখ", "তারিখ কত", "আজ কী বার",
                "আজ কি বার", "আজকের বার", "today's date", "todays date", "what day");
        if (!wantsTime && !wantsDate) return null;

        Calendar c = Calendar.getInstance();
        String day = DAYS[c.get(Calendar.DAY_OF_WEEK) - 1];
        String date = toBn(String.valueOf(c.get(Calendar.DAY_OF_MONTH))) + " "
                + MONTHS[c.get(Calendar.MONTH)] + " " + toBn(String.valueOf(c.get(Calendar.YEAR)));

        int h24 = c.get(Calendar.HOUR_OF_DAY);
        int h12 = c.get(Calendar.HOUR);
        if (h12 == 0) h12 = 12;
        String period;
        if (h24 < 4) period = "রাত";
        else if (h24 < 12) period = "সকাল";
        else if (h24 < 15) period = "দুপুর";
        else if (h24 < 18) period = "বিকাল";
        else if (h24 < 20) period = "সন্ধ্যা";
        else period = "রাত";
        String time = period + " " + toBn(h12 + ":" + String.format(Locale.US, "%02d", c.get(Calendar.MINUTE)));

        if (wantsTime && !wantsDate) return "এখন " + time + " (ফোনের সময় অনুযায়ী)।";
        if (wantsDate && !wantsTime) return "আজ " + day + ", " + date + "।";
        return "আজ " + day + ", " + date + "। এখন " + time + "।";
    }

    // ---------- CBM ক্যালকুলেটর ----------

    private String cbm(String q) {
        if (!(tokens(q).contains("cbm") || q.contains("সিবিএম"))) return null;
        List<Double> n = numbers(q);
        if (n.size() < 3) return null; // বিস্তারিত ব্যাখ্যা জ্ঞানভান্ডার দেবে

        double l = n.get(0);
        double w = n.get(1);
        double h = n.get(2);
        double qty = n.size() >= 4 ? n.get(3) : 1;
        boolean meters = hasAny(q, "মিটার", "meter", "metre");
        double perCarton = meters ? l * w * h : l * w * h / 1_000_000.0;
        String unit = meters ? " মিটার" : " সেমি";

        StringBuilder sb = new StringBuilder();
        sb.append("প্রতি কার্টন: ").append(num(l, 2)).append(" × ").append(num(w, 2)).append(" × ")
                .append(num(h, 2)).append(unit).append(" = ").append(num(perCarton, 4)).append(" CBM");
        if (qty != 1) {
            sb.append("\nমোট (").append(num(qty, 2)).append(" কার্টন) = ")
                    .append(num(perCarton * qty, 3)).append(" CBM");
        }
        if (!meters) {
            double vol = l * w * h / 6000.0;
            sb.append("\nএয়ার ফ্রেইটে ভলিউমেট্রিক ওজন (÷৬০০০): ").append(num(vol, 2))
                    .append(" কেজি/কার্টন");
            if (qty != 1) sb.append(" (মোট ").append(num(vol * qty, 2)).append(" কেজি)");
            sb.append("\n(কিছু এয়ারলাইনে ÷৫০০০ ধরা হয়; ক্যারিয়ারের নিয়ম যাচাই করুন)");
        }
        sb.append("\nআনুমানিক ব্যবহারযোগ্য জায়গা: ২০ফু ≈ ২৮, ৪০ফু ≈ ৫৮, ৪০HQ ≈ ৬৮ CBM।");
        return sb.toString();
    }

    // ---------- মুদ্রা রূপান্তর ----------

    private static final Pattern CUR_AFTER = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(ইউয়ান|yuan|rmb|আরএমবি|cny|¥|ডলার|usd|dollar|\\$|টাকা|taka|bdt|৳)");
    private static final Pattern CUR_BEFORE = Pattern.compile(
            "(¥|\\$|৳)\\s*(\\d+(?:\\.\\d+)?)");

    private static int curOf(String u) {
        switch (u) {
            case "ইউয়ান":
            case "yuan":
            case "rmb":
            case "আরএমবি":
            case "cny":
            case "¥":
                return RMB;
            case "ডলার":
            case "usd":
            case "dollar":
            case "$":
                return USD;
            default:
                return BDT;
        }
    }

    private static boolean mentions(String q, int cur) {
        if (cur == RMB) return hasAny(q, "ইউয়ান", "yuan", "rmb", "আরএমবি", "cny", "¥");
        if (cur == USD) return hasAny(q, "ডলার", "usd", "dollar", "$");
        return hasAny(q, "টাকা", "taka", "bdt", "৳");
    }

    private static String curName(int cur) {
        if (cur == RMB) return "ইউয়ান";
        if (cur == USD) return "ডলার";
        return "টাকা";
    }

    private double rateOf(int cur) {
        if (cur == RMB) return rates.rmbToBdt();
        if (cur == USD) return rates.usdToBdt();
        return 1.0;
    }

    private String currency(String q) {
        double amount;
        int src;
        Matcher m = CUR_AFTER.matcher(q);
        if (m.find()) {
            amount = Double.parseDouble(m.group(1));
            src = curOf(m.group(2));
        } else {
            Matcher m2 = CUR_BEFORE.matcher(q);
            if (!m2.find()) return null;
            amount = Double.parseDouble(m2.group(2));
            src = curOf(m2.group(1));
        }

        int tgt = -1;
        int[] order = {BDT, RMB, USD};
        for (int c : order) {
            if (c != src && mentions(q, c)) {
                tgt = c;
                break;
            }
        }
        if (tgt == -1) {
            if (src == BDT) {
                return "কোন মুদ্রায় বদলাতে চান? যেমন: '১০০০০ টাকা কত ইউয়ান' বা '১০০০০ টাকা কত ডলার'।";
            }
            tgt = BDT;
        }

        double rs = rateOf(src);
        double rt = rateOf(tgt);
        if (rs <= 0 || rt <= 0) {
            return "রূপান্তরের জন্য আপনার রেট লাগবে। ওপরের ⋮ মেনু থেকে 'রেট সেট করুন' চেপে "
                    + "১ ইউয়ান ও ১ ডলারের টাকার রেট দিন, তারপর আবার জিজ্ঞেস করুন।";
        }

        double result = amount * rs / rt;
        StringBuilder sb = new StringBuilder();
        sb.append(num(amount, 2)).append(' ').append(curName(src)).append(" = ")
                .append(num(result, 2)).append(' ').append(curName(tgt));
        sb.append("\n(আপনার সেট করা রেট:");
        boolean first = true;
        for (int c : new int[]{src, tgt}) {
            if (c == BDT) continue;
            sb.append(first ? " " : ", ").append("১ ").append(curName(c)).append(" = ")
                    .append(num(rateOf(c), 4)).append(" টাকা");
            first = false;
        }
        sb.append(')');
        return sb.toString();
    }

    // ---------- সাধারণ হিসাব ----------

    private String arithmetic(String q) {
        String e = q.replace("=", "").replace("?", "").trim();
        if (!e.matches("[0-9+\\-*/×÷x().\\s]+")) return null;
        if (!e.matches(".*\\d\\s*[+\\-*/×÷x]\\s*[\\d(.\\-].*")) return null;
        e = e.replace('×', '*').replace('÷', '/').replace('x', '*').replace(" ", "");
        try {
            double v = new Expr(e).parse();
            if (Double.isNaN(v) || Double.isInfinite(v)) return "০ দিয়ে ভাগ করা যায় না।";
            return "= " + num(v, 4);
        } catch (RuntimeException ex) {
            return "হিসাবটা বুঝতে পারিনি। এভাবে লিখুন: (১২০০+৩০০)*২";
        }
    }

    private static final class Expr {
        private final String s;
        private int i = 0;

        Expr(String s) {
            this.s = s;
        }

        double parse() {
            double v = expr();
            if (i != s.length()) throw new IllegalArgumentException();
            return v;
        }

        private double expr() {
            double v = term();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '+') {
                    i++;
                    v += term();
                } else if (c == '-') {
                    i++;
                    v -= term();
                } else {
                    break;
                }
            }
            return v;
        }

        private double term() {
            double v = factor();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '*') {
                    i++;
                    v *= factor();
                } else if (c == '/') {
                    i++;
                    v /= factor();
                } else {
                    break;
                }
            }
            return v;
        }

        private double factor() {
            if (i >= s.length()) throw new IllegalArgumentException();
            char c = s.charAt(i);
            if (c == '-') {
                i++;
                return -factor();
            }
            if (c == '+') {
                i++;
                return factor();
            }
            if (c == '(') {
                i++;
                double v = expr();
                if (i >= s.length() || s.charAt(i) != ')') throw new IllegalArgumentException();
                i++;
                return v;
            }
            int st = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            if (st == i) throw new IllegalArgumentException();
            return Double.parseDouble(s.substring(st, i));
        }
    }

    // ---------- জ্ঞানভান্ডার ----------

    private String knowledge(String q) {
        Set<String> toks = tokens(q);
        Entry best = null;
        int bestScore = 0;
        for (Entry e : entries) {
            int score = 0;
            for (String k : e.keys) {
                if (hit(q, toks, k)) score += k.length();
            }
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        if (best == null) return null;
        return best.answer.replace("{RATES}", ratesSummary());
    }

    // ছোট ইংরেজি কী শুধু পুরো শব্দ হিসেবে মিললে ধরা হয় (যেমন 'hs' শব্দ 'months' এ ধরবে না)
    private static boolean hit(String q, Set<String> toks, String key) {
        if (key.matches("[a-z0-9]+")) return toks.contains(key);
        return q.contains(key);
    }

    private String ratesSummary() {
        double rmb = rates.rmbToBdt();
        double usd = rates.usdToBdt();
        if (rmb <= 0 && usd <= 0) return "আপনি এখনো কোনো রেট সেট করেননি।";
        StringBuilder sb = new StringBuilder("আপনার সেট করা রেট:");
        if (rmb > 0) sb.append(" ১ ইউয়ান = ").append(num(rmb, 4)).append(" টাকা;");
        if (usd > 0) sb.append(" ১ ডলার = ").append(num(usd, 4)).append(" টাকা;");
        return sb.toString();
    }

    private void add(String answer, String... keys) {
        entries.add(new Entry(answer, keys));
    }

    private void buildKnowledge() {
        // --- কথাবার্তা ---
        add("আমি ভালো আছি! আপনি কেমন আছেন?",
                "কেমন আছ", "কেমন আছেন", "how are you", "কি খবর", "কী খবর");
        add("আমার নাম Donald। আমি আপনার আমদানি-রপ্তানি ও রোজকার কাজের সহকারী।",
                "তোমার নাম", "আপনার নাম", "your name", "নাম কী", "নাম কি", "তুমি কে", "who are you");
        add("আপনাকেও অনেক ধন্যবাদ!",
                "ধন্যবাদ", "thanks", "thank you");
        add("ওয়ালাইকুম আসসালাম! বলুন, কীভাবে সাহায্য করতে পারি?",
                "আসসালামু", "assalamu", "সালাম");
        add("হ্যালো! বলুন, কীভাবে সাহায্য করতে পারি? 'help' লিখলে দেখবেন আমি কী কী পারি।",
                "হাই", "হ্যালো", "hello", "hi");

        add("আমি অফলাইনে এগুলো পারি:\n\n"
                        + "• হিসাব: ১২০০*৩৫ বা (৫০০+২০০)/৩\n"
                        + "• মুদ্রা: ১০০ ইউয়ান কত টাকা (আগে ⋮ মেনুতে রেট সেট করুন)\n"
                        + "• CBM: cbm 50x40x30 100 (দৈর্ঘ্য প্রস্থ উচ্চতা সেমি, কার্টন সংখ্যা)\n"
                        + "• তারিখ ও সময়: আজকের তারিখ, কয়টা বাজে\n"
                        + "• আমদানি-রপ্তানি: আমদানি প্রক্রিয়া, ইনকোটার্ম, FOB, CIF, CNF, এলসি, টিটি, "
                        + "পেমেন্ট টার্ম, বিএল, কাগজপত্র, HS কোড, শুল্ক, কনটেইনার, FCL/LCL, "
                        + "ল্যান্ডেড কস্ট, সি অ্যান্ড এফ এজেন্ট, সোর্সিং\n"
                        + "• ব্যবসা: মার্জিন, মার্কআপ, ব্রেক ইভেন, ক্যাশ ফ্লো\n\n"
                        + "অন্য প্রশ্নের উত্তর দিতে ইন্টারনেট ও Claude ক্রেডিট লাগবে।",
                "help", "সাহায্য", "কী কী পারো", "কি কি পারো", "কী পারো", "কি পারো",
                "কী করতে পারো", "কি করতে পারো", "মেনু");

        // --- রেট ---
        add("লাইভ রেট আমি অফলাইনে জানতে পারি না, কারণ রেট প্রতিদিন বদলায়। "
                        + "ওপরের ⋮ মেনু → 'রেট সেট করুন' এ আপনি নিজের রেট দিয়ে রাখুন। তারপর "
                        + "'১০০ ইউয়ান কত টাকা' বা '৫০০ ডলার কত টাকা' জিজ্ঞেস করলে আমি হিসাব করে দেব।\n\n{RATES}",
                "আজকের রেট", "আজকের ডলার", "ডলার রেট", "রেট কত", "exchange rate", "এক্সচেঞ্জ রেট",
                "ইউয়ানের রেট", "ইউয়ান রেট", "rmb rate", "usd rate", "রেট সেট");

        // --- আমদানি প্রক্রিয়া ---
        add("আমদানির সাধারণ ধাপ:\n"
                        + "১) পণ্য ও বাজার যাচাই, সরবরাহকারী বাছাই, নমুনা\n"
                        + "২) আমদানির নিবন্ধন ও লাইসেন্স প্রস্তুত (যেমন IRC, ট্রেড লাইসেন্স, TIN, BIN)\n"
                        + "৩) প্রফর্মা ইনভয়েস (PI) নিয়ে পেমেন্ট পদ্ধতি ঠিক করা (এলসি/টিটি)\n"
                        + "৪) শিপমেন্ট ও বিল অব লেডিং\n"
                        + "৫) গন্তব্য বন্দরে সি অ্যান্ড এফ এজেন্টের মাধ্যমে কাস্টমস ছাড়\n"
                        + "৬) ডেলিভারি, খরচ হিসাব ও বিক্রি\n\n"
                        + "নিয়ম সময়ে সময়ে বদলায়, তাই ব্যাংক ও সি অ্যান্ড এফ এজেন্টের কাছে যাচাই করুন। "
                        + "প্রতিটি ধাপ জানতে সেই শব্দটা লিখুন, যেমন 'এলসি' বা 'শুল্ক'।",
                "আমদানি প্রক্রিয়া", "আমদানির ধাপ", "কীভাবে আমদানি", "কিভাবে আমদানি",
                "import process", "how to import", "আমদানি করব", "আমদানি করতে");

        // --- ইনকোটার্ম ---
        add("ইনকোটার্ম হলো কোন খরচ ও ঝুঁকি ক্রেতা বা বিক্রেতা নেবে তার আন্তর্জাতিক নিয়ম।\n"
                        + "• EXW: বিক্রেতার গুদামে পণ্য প্রস্তুত; এরপর সব খরচ ও ঝুঁকি ক্রেতার।\n"
                        + "• FOB: জাহাজে তোলা পর্যন্ত বিক্রেতার দায়; সমুদ্রভাড়া ক্রেতার।\n"
                        + "• CFR (সিএনএফ): গন্তব্য বন্দর পর্যন্ত ভাড়া বিক্রেতার; ঝুঁকি জাহাজে তোলার পর ক্রেতার।\n"
                        + "• CIF: CFR এর সাথে বিক্রেতা ন্যূনতম বিমাও করে।\n"
                        + "• DDP: শুল্কসহ গন্তব্যে পৌঁছে দেওয়া পর্যন্ত সব দায় বিক্রেতার।\n"
                        + "(FOB, CFR, CIF শুধু সমুদ্র ও নদীপথের জন্য।) আলাদা করে জানতে 'FOB কী' ইত্যাদি লিখুন।",
                "incoterm", "incoterms", "ইনকোটার্ম", "ইনকোটার্মস");
        add("FOB (Free On Board): বিক্রেতা রপ্তানির ছাড়পত্রসহ পণ্য জাহাজে তোলা পর্যন্ত দায়ী। "
                        + "এরপর সমুদ্রভাড়া, বিমা, আমদানি শুল্ক ও গন্তব্যের খরচ ক্রেতার। "
                        + "ক্রেতা নিজের ফরোয়ার্ডার ঠিক করতে পারে বলে ভাড়ার খরচ নিয়ন্ত্রণে থাকে। "
                        + "শুল্কের জন্য FOB মূল্যের সাথে ভাড়া ও বিমা যোগ করে CIF মূল্য ধরা হয়।",
                "fob", "এফওবি");
        add("CIF: বিক্রেতা গন্তব্য বন্দর পর্যন্ত পণ্য, ভাড়া ও ন্যূনতম বিমার খরচ দেয়। "
                        + "তবে ঝুঁকি জাহাজে তোলার পর থেকেই ক্রেতার। শুল্ক সাধারণত CIF মূল্যের ওপর হিসাব হয়। "
                        + "বিমা ন্যূনতম মাত্রার, তাই দামি পণ্যে বাড়তি কভারের কথা ভাবতে পারেন।",
                "cif", "সিআইএফ");
        add("CFR (বাংলাদেশে প্রচলিত নাম C&F): বিক্রেতা গন্তব্য বন্দর পর্যন্ত ভাড়া দেয়, বিমা করে না। "
                        + "ঝুঁকি জাহাজে তোলার পর ক্রেতার, তাই বিমা ক্রেতা নিজে করা ভালো। "
                        + "খেয়াল রাখুন: 'সি অ্যান্ড এফ এজেন্ট' বলতে কাস্টমস ছাড় করানো এজেন্টকেও বোঝায়।",
                "cfr", "cnf", "c&f", "সিএনএফ", "সি অ্যান্ড এফ");
        add("EXW (Ex Works): বিক্রেতা নিজের কারখানা বা গুদামে পণ্য প্রস্তুত রাখে। "
                        + "পণ্য তোলা, দেশের ভেতরের পরিবহন, রপ্তানি ছাড়, ভাড়া, বিমা সবকিছু ক্রেতার। "
                        + "দাম সবচেয়ে কম দেখায়, কিন্তু ক্রেতার ঝামেলা ও ঝুঁকি সবচেয়ে বেশি।",
                "exw", "ex works");
        add("DDP (Delivered Duty Paid): বিক্রেতা শুল্ক-কর মিটিয়ে গন্তব্যে পণ্য পৌঁছে দেয়। "
                        + "ক্রেতার ঝামেলা কম, কিন্তু দাম বেশি ও আমদানির নিয়ম না মেনে চললে জটিলতা হতে পারে।",
                "ddp");

        // --- পেমেন্ট ---
        add("এলসি (Letter of Credit) হলো ক্রেতার ব্যাংকের দেওয়া পেমেন্টের প্রতিশ্রুতি: "
                        + "বিক্রেতা শর্তমতো কাগজপত্র জমা দিলে ব্যাংক টাকা দেবে।\n"
                        + "পক্ষ: আমদানিকারক (আবেদনকারী), ইস্যুকারী ব্যাংক, রপ্তানিকারক (সুবিধাভোগী), "
                        + "অ্যাডভাইজিং/নেগোশিয়েটিং ব্যাংক।\n"
                        + "ধরন: At Sight (দেখামাত্র পেমেন্ট), Usance (মেয়াদি), Back-to-Back (এক এলসির ভিত্তিতে আরেকটি)।\n"
                        + "সুবিধা: দুই পক্ষেরই ঝুঁকি কমে। অসুবিধা: খরচ ও কাগজপত্র বেশি, কাগজে সামান্য ভুলেও পেমেন্ট আটকাতে পারে।",
                "lc", "এলসি", "letter of credit", "ঋণপত্র");
        add("এলসি খুলতে সাধারণত লাগে: প্রফর্মা ইনভয়েস (PI), আমদানি নিবন্ধন সনদ (IRC), ট্রেড লাইসেন্স, "
                        + "টিআইএন, ভ্যাট নিবন্ধন (BIN), এলসি আবেদন ও ব্যাংকের ফর্ম, বিমা কভার নোট, "
                        + "আর কিছু পণ্যে বিশেষ অনুমতি বা সনদ।\n"
                        + "তালিকা ও নিয়ম সময়ে সময়ে বদলায়, তাই আপনার ব্যাংকের আমদানি শাখায় যাচাই করুন।",
                "এলসি খোলা", "এলসি খুলতে", "এলসি খুলব", "lc open", "এলসি কাগজ", "আইআরসি", "irc");
        add("টিটি (Telegraphic Transfer) হলো ব্যাংকের মাধ্যমে সরাসরি বিদেশে টাকা পাঠানো। "
                        + "এলসির চেয়ে দ্রুত ও খরচ কম, কিন্তু সরবরাহকারীর ওপর আস্থা না থাকলে ঝুঁকি বেশি, "
                        + "বিশেষ করে অগ্রিম পাঠালে। অগ্রিম পাঠানোর নিয়ম বাংলাদেশ ব্যাংকের নির্দেশনা অনুযায়ী, "
                        + "তাই ব্যাংকে জেনে নিন।",
                "tt", "টিটি", "telegraphic", "wire transfer", "টেলিগ্রাফিক");
        add("পেমেন্ট টার্মের সাধারণ ধরন:\n"
                        + "• অগ্রিম: পুরো টাকা আগে; ক্রেতার ঝুঁকি সবচেয়ে বেশি\n"
                        + "• ৩০/৭০ টিটি: ৩০% অর্ডারে, ৭০% কাগজপত্র দেখে বা জাহাজে তোলার পর\n"
                        + "• এলসি: ব্যাংকের গ্যারান্টি, কাগজ মিললে পেমেন্ট\n"
                        + "• D/P: টাকা দিলে তবেই কাগজ হস্তান্তর\n"
                        + "• D/A: কাগজ নিয়ে নির্দিষ্ট মেয়াদে পরে পেমেন্ট\n"
                        + "• ওপেন অ্যাকাউন্ট: মাল পেয়ে পরে পেমেন্ট; বিক্রেতার ঝুঁকি বেশি\n"
                        + "পরামর্শ: নতুন সরবরাহকারীর সাথে প্রথম অর্ডার ছোট রাখুন এবং নিরাপদ পদ্ধতি বাছুন।",
                "পেমেন্ট টার্ম", "payment term", "payment terms", "পেমেন্ট পদ্ধতি", "অগ্রিম", "d/p", "d/a",
                "open account", "ওপেন অ্যাকাউন্ট");
        add("বিদেশে পেমেন্ট বা মুদ্রা লেনদেন বাংলাদেশে বাংলাদেশ ব্যাংকের ফরেন এক্সচেঞ্জ নিয়ম মেনে "
                        + "অনুমোদিত ডিলার (AD) ব্যাংকের মাধ্যমে করাই বৈধ পথ। হুন্ডি বা হাওয়ালা অবৈধ ও ঝুঁকিপূর্ণ, "
                        + "টাকা হারানো বা আইনি সমস্যা দুটোই হতে পারে। আপনার লেনদেনের ধরন অনুযায়ী "
                        + "ব্যাংক বা আইনজীবীর কাছ থেকে নিয়ম নিশ্চিত করুন।",
                "হুন্ডি", "hundi", "hawala", "হাওয়ালা", "বৈধ চ্যানেল", "রেমিট্যান্স", "remittance",
                "বাংলাদেশ ব্যাংক", "ফরেক্স", "forex");

        // --- কাগজপত্র ---
        add("বিল অব লেডিং (B/L) হলো জাহাজ কোম্পানির দেওয়া তিনটি কাজের কাগজ: পণ্য গ্রহণের রসিদ, "
                        + "পরিবহনের চুক্তি এবং (মূল কপি হলে) পণ্যের মালিকানার দলিল।\n"
                        + "ধরন: MBL (জাহাজ কোম্পানির), HBL (ফরোয়ার্ডারের), Original B/L (মূল কপি লাগে), "
                        + "Seaway Bill ও Telex Release (মূল কপি ছাড়াই ছাড়)।\n"
                        + "বিলে নাম, পণ্য, পরিমাণ ও কনটেইনার নম্বর এলসি ও ইনভয়েসের সাথে হুবহু মেলাতে হয়।",
                "বিএল", "b/l", "bill of lading", "বিল অব লেডিং", "mbl", "hbl", "telex");
        add("আমদানি-রপ্তানির প্রধান কাগজপত্র:\n"
                        + "• কমার্শিয়াল ইনভয়েস: পণ্য, দাম ও শর্তের হিসাব\n"
                        + "• প্যাকিং লিস্ট: কার্টন সংখ্যা, ওজন, মাপ\n"
                        + "• বিল অব লেডিং (সমুদ্র) বা AWB (আকাশপথ)\n"
                        + "• সার্টিফিকেট অব অরিজিন (CO), যেখানে লাগে\n"
                        + "• বিমা সনদ (CIF হলে)\n"
                        + "• এলসি কপি ও প্রফর্মা ইনভয়েস\n"
                        + "• কিছু পণ্যে অনুমতি বা পরিদর্শন সনদ\n"
                        + "সব কাগজের নাম, পরিমাণ ও দাম পরস্পর মিলতে হবে, নইলে ছাড় বা পেমেন্ট আটকাতে পারে।",
                "কাগজপত্র", "ডকুমেন্ট", "document", "documents", "ইনভয়েস", "invoice", "প্যাকিং লিস্ট",
                "packing list", "certificate of origin", "সার্টিফিকেট অব অরিজিন");

        // --- শুল্ক ও HS কোড ---
        add("HS কোড হলো পণ্য শ্রেণিবিন্যাসের আন্তর্জাতিক সংখ্যা। আন্তর্জাতিকভাবে প্রথম ৬ অঙ্ক এক; "
                        + "বাংলাদেশের শুল্ক তালিকায় সাধারণত ৮ অঙ্কে পণ্য চেনা হয়। এই কোড দেখেই শুল্ক-করের হার, "
                        + "অনুমতির শর্ত ও নিষেধাজ্ঞা ঠিক হয়। ভুল কোড দিলে জরিমানা বা মাল আটকের ঝুঁকি আছে, "
                        + "তাই কোড নিশ্চিত করতে সি অ্যান্ড এফ এজেন্ট বা কাস্টমসের ট্যারিফ দেখে নিন।",
                "hs code", "hscode", "hs", "এইচএস", "harmonized", "হার্মোনাইজড");
        add("বাংলাদেশে আমদানিতে সাধারণত এই শুল্ক-কর লাগে: সিডি (কাস্টমস ডিউটি), আরডি (রেগুলেটরি ডিউটি), "
                        + "এসডি (সম্পূরক শুল্ক), ভ্যাট, এআইটি (অগ্রিম আয়কর) ও এটি (অগ্রিম কর)।\n"
                        + "ভিত্তি মূল্য (অ্যাসেসেবল ভ্যালু) সাধারণত CIF মূল্য × কাস্টমসের নির্ধারিত বিনিময় হার।\n"
                        + "হিসাব ধাপে ধাপে: সিডি → আরডি → এসডি → ভ্যাট (ভ্যাটের ভিত্তি সাধারণত মূল্য+সিডি+আরডি+এসডি)।\n"
                        + "রেট পণ্যের HS কোড ও প্রতি বছরের বাজেট অনুযায়ী বদলায়, তাই নির্দিষ্ট রেট আমি এখানে বলছি না। "
                        + "বর্তমান রেট কাস্টমসের ট্যারিফ বা আপনার সি অ্যান্ড এফ এজেন্টের কাছে যাচাই করুন।",
                "শুল্ক", "কাস্টমস", "customs", "duty", "ভ্যাট", "vat", "ait", "এআইটি", "ট্যাক্স",
                "শুল্কের রেট", "শুল্কের হার", "duty rate");

        // --- পরিবহন ---
        add("CBM (ঘনমিটার) = দৈর্ঘ্য × প্রস্থ × উচ্চতা (মিটারে)। সেমিতে মাপলে ১০,০০,০০০ দিয়ে ভাগ। "
                        + "মোট CBM = এক কার্টনের CBM × কার্টন সংখ্যা।\n"
                        + "হিসাব করতে লিখুন: cbm 50x40x30 100 (সেমিতে মাপ, শেষে কার্টন সংখ্যা)।\n"
                        + "এয়ার ফ্রেইটে ভলিউমেট্রিক ওজন = (দৈর্ঘ্য×প্রস্থ×উচ্চতা সেমিতে) ÷ ৬০০০ কেজি; "
                        + "কিছু ক্যারিয়ারে ÷৫০০০। ভাড়া হয় প্রকৃত ও ভলিউমেট্রিক ওজনের যেটা বেশি তার ওপর।",
                "cbm", "সিবিএম", "ঘনমিটার", "volumetric", "ভলিউমেট্রিক");
        add("কনটেইনারের আনুমানিক ব্যবহারযোগ্য জায়গা:\n"
                        + "• ২০ ফুট: প্রায় ২৮ CBM\n"
                        + "• ৪০ ফুট: প্রায় ৫৮ CBM\n"
                        + "• ৪০ ফুট হাই-কিউব (40HQ): প্রায় ৬৮ CBM\n"
                        + "TEU মানে ২০ ফুট কনটেইনারের একক; ৪০ ফুট = ২ TEU। পণ্যের ওজন সীমাও দেখতে হয়, "
                        + "ভারী পণ্যে জায়গা ভরার আগেই ওজনের সীমা শেষ হয়ে যেতে পারে।",
                "কনটেইনার", "কন্টেইনার", "container", "20ft", "40ft", "40hq", "teu");
        add("FCL (Full Container Load): পুরো কনটেইনার একজনের, পণ্য বেশি হলে সাধারণত সাশ্রয়ী ও নিরাপদ।\n"
                        + "LCL (Less than Container Load): কম পণ্য হলে অন্যদের সাথে কনটেইনার ভাগাভাগি; "
                        + "ভাড়া CBM বা ওজন অনুযায়ী। LCL এ খরচ প্রতি CBM এ বেশি, সময়ও বেশি লাগে "
                        + "(জমা ও খালাসে)। প্রায় ১৫ CBM এর বেশি হলে FCL এর দাম তুলনা করে দেখুন।",
                "fcl", "lcl");

        // --- খরচ হিসাব ---
        add("ল্যান্ডেড কস্ট (পণ্য গুদামে পৌঁছানো পর্যন্ত মোট খরচ):\n"
                        + "পণ্যের দাম (FOB) + সমুদ্র/আকাশ ভাড়া + বিমা = CIF মূল্য\n"
                        + "+ শুল্ক-কর + বন্দর ও CFS চার্জ + সি অ্যান্ড এফ কমিশন + ব্যাংক/এলসি খরচ\n"
                        + "+ দেশের ভেতরে পরিবহন + অন্যান্য (ক্ষয়ক্ষতি, ডেমারেজ)\n"
                        + "প্রতি পিসের খরচ = মোট খরচ ÷ পিস সংখ্যা। এর ওপর মার্জিন ধরে বিক্রয়মূল্য ঠিক করুন।\n"
                        + "টিপ: শুল্ক, ভাড়া ও রেটের একটু ওঠানামার জন্য খরচের ওপর ৫-১০% নিরাপদ জায়গা রাখুন।",
                "ল্যান্ডেড", "landed", "মোট খরচ", "পণ্যের খরচ", "একক খরচ", "ইউনিট কস্ট", "unit cost");
        add("সি অ্যান্ড এফ এজেন্ট আপনার হয়ে কাস্টমসে পণ্য ছাড় করান। সাধারণ ধাপ:\n"
                        + "১) জাহাজ পৌঁছানোর খবর ও ডেলিভারি অর্ডার (DO) সংগ্রহ\n"
                        + "২) বিল অব এন্ট্রি জমা (অনলাইন কাস্টমস সিস্টেমে)\n"
                        + "৩) শুল্ক নির্ধারণ ও পণ্য পরীক্ষা\n"
                        + "৪) শুল্ক-কর পরিশোধ\n"
                        + "৫) ছাড়পত্র নিয়ে বন্দর থেকে ডেলিভারি\n"
                        + "এজেন্ট নেওয়ার আগে তাঁর লাইসেন্স, অভিজ্ঞতা এবং কমিশন ও চার্জের তালিকা লিখিত নিন।",
                "সি অ্যান্ড এফ এজেন্ট", "সিএনএফ এজেন্ট", "c&f agent", "cnf agent", "clearing", "ছাড়করণ",
                "খালাস", "বিল অব এন্ট্রি", "bill of entry", "বন্দর");

        // --- মুদ্রা ও সোর্সিং ---
        add("RMB হলো চীনের মুদ্রার নাম (রেনমিনবি), আর ইউয়ান (¥) তার একক। কোড CNY; "
                        + "চীনের বাইরে লেনদেন হওয়া সংস্করণকে CNH বলা হয়। চীনা সরবরাহকারীকে পেমেন্ট সাধারণত "
                        + "ব্যাংক টিটি বা এলসির মাধ্যমে হয়। বাংলাদেশ থেকে কীভাবে পাঠানো বৈধ, তা জানতে 'বৈধ চ্যানেল' লিখুন।\n"
                        + "মুদ্রা হিসাব করতে: '১০০ ইউয়ান কত টাকা'।\n\n{RATES}",
                "rmb", "ইউয়ান", "yuan", "রেনমিনবি", "cny", "cnh", "আরএমবি");
        add("সরবরাহকারী বাছাইয়ের চেকলিস্ট:\n"
                        + "• কোম্পানির তথ্য ও ব্যবসার বৈধতা যাচাই করুন\n"
                        + "• একাধিক কোটেশন নিয়ে দাম ও শর্ত তুলনা করুন\n"
                        + "• আগে ছোট নমুনা অর্ডার দিন, তারপর বড় অর্ডার\n"
                        + "• মান, মাপ ও প্যাকিং লিখিতভাবে ঠিক করুন\n"
                        + "• জাহাজে তোলার আগে নিজে বা তৃতীয় পক্ষ দিয়ে পরিদর্শন করান\n"
                        + "• পেমেন্ট ধাপে ধাপে দিন; পুরো অগ্রিম এড়িয়ে চলুন\n"
                        + "• আলিবাবার মতো প্ল্যাটফর্মে ক্রেতা-সুরক্ষা সুবিধা (যেমন Trade Assurance) থাকলে সেটা ব্যবহার করুন",
                "সরবরাহকারী", "সাপ্লায়ার", "supplier", "alibaba", "আলিবাবা", "1688", "সোর্সিং",
                "sourcing", "ফ্যাক্টরি", "factory");

        // --- ব্যবসার সূত্র ---
        add("মার্জিন ও মার্কআপ আলাদা জিনিস:\n"
                        + "• মার্কআপ = (বিক্রয়মূল্য − খরচ) ÷ খরচ\n"
                        + "• মার্জিন = (বিক্রয়মূল্য − খরচ) ÷ বিক্রয়মূল্য\n"
                        + "উদাহরণ: খরচ ১০০, বিক্রয় ১২৫ → মার্কআপ ২৫%, মার্জিন ২০%।\n"
                        + "নির্দিষ্ট মার্জিন চাইলে: বিক্রয়মূল্য = খরচ ÷ (১ − মার্জিন%)। "
                        + "যেমন খরচ ১০০ ও মার্জিন ২০% হলে বিক্রয়মূল্য ১২৫।\n"
                        + "ভুল করে মার্কআপকে মার্জিন ধরলে ব্যবসায় লাভ কম আসে।",
                "মার্জিন", "মার্কআপ", "margin", "markup", "লাভের হিসাব", "লাভ কীভাবে", "লাভ কিভাবে");
        add("ব্রেক ইভেন হলো যে বিক্রির পরিমাণে লাভ-লোকসান সমান হয়।\n"
                        + "ব্রেক ইভেন পিস = মোট স্থির খরচ ÷ (প্রতি পিসের বিক্রয়মূল্য − প্রতি পিসের পরিবর্তনশীল খরচ)।\n"
                        + "উদাহরণ: স্থির খরচ ৫০,০০০, বিক্রয়মূল্য ২০০, পরিবর্তনশীল খরচ ১২০ → "
                        + "৫০,০০০ ÷ ৮০ = ৬২৫ পিস বিক্রির পর থেকে লাভ শুরু।",
                "ব্রেক ইভেন", "break even", "breakeven", "লোকসান ছাড়া");
        add("আমদানি ব্যবসায় ক্যাশ ফ্লো সবচেয়ে বড় চাপ, কারণ টাকা অনেকদিন আটকে থাকে।\n"
                        + "হিসাব করুন: অর্ডার থেকে পণ্য হাতে আসা পর্যন্ত সময় + বিক্রি ও টাকা আদায়ের সময় = "
                        + "আপনার টাকা কত দিন আটকে থাকছে।\n"
                        + "টিপ: একসাথে সব টাকা এক চালানে ঢালবেন না; ছোট ও দ্রুত ঘোরা চালান আগে করুন, "
                        + "বাকিতে বিক্রির সীমা ঠিক রাখুন, আর শুল্ক ও দেরির খরচের জন্য নগদ রিজার্ভ রাখুন।",
                "ক্যাশ ফ্লো", "cash flow", "cashflow", "মূলধন", "working capital", "নগদ প্রবাহ");
    }
}
