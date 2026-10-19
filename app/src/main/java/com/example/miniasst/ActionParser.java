package com.example.miniasst;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ফোনের কাজের কমান্ড বোঝার অংশ। অ্যান্ড্রয়েড ছাড়াই চলে, তাই আলাদাভাবে পরীক্ষা করা যায়। */
final class ActionParser {
    private ActionParser() {}

    /** "01711 কালকে মাল আসবে" → {"01711", "কালকে মাল আসবে"}। শুরুতে নম্বর না থাকলে নম্বর ফাঁকা। */
    static String[] numberAndText(String arg) {
        arg = arg.trim();
        int sp = arg.indexOf(' ');
        String first = sp < 0 ? arg : arg.substring(0, sp);
        String ascii = OfflineBrain.toAsciiDigits(first);
        if (ascii.matches("\\+?[0-9][0-9\\-]{4,}")) {
            return new String[]{ascii.replace("-", ""), sp < 0 ? "" : arg.substring(sp + 1).trim()};
        }
        return new String[]{"", arg};
    }

    /** হোয়াটসঅ্যাপের জন্য দেশের কোডসহ নম্বর। বাংলাদেশি 01XXXXXXXXX → 8801XXXXXXXXX। */
    static String waNumber(String n) {
        String d = n.replaceAll("[^0-9]", "");
        if (d.length() == 11 && d.startsWith("0")) return "88" + d;
        return d;
    }

    /** {ঘণ্টা(০-২৩), মিনিট} বা অবৈধ হলে null। label[0] এ সময় বাদে বাকি লেখা বসে। */
    static int[] time(String arg, String[] label) {
        String s = OfflineBrain.toAsciiDigits(arg).toLowerCase(Locale.ROOT);
        Matcher m = Pattern.compile("(\\d{1,2})(?:[:.](\\d{2}))?").matcher(s);
        if (!m.find()) return null;
        int h = Integer.parseInt(m.group(1));
        int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        if (min > 59 || h > 23) return null;
        String rest = (s.substring(0, m.start()) + " " + s.substring(m.end())).trim();
        boolean pm = has(rest, "pm", "বিকাল", "বিকেল", "সন্ধ্যা", "রাত", "দুপুর");
        boolean am = has(rest, "am", "সকাল", "ভোর");
        if (pm && h < 12) h += 12;
        if (am && h == 12) h = 0;
        if (rest.contains("রাত") && h == 12) h = 0;
        label[0] = clean(arg, m, s);
        return new int[]{h, min};
    }

    private static String clean(String orig, Matcher m, String lower) {
        String origAscii = OfflineBrain.toAsciiDigits(orig);
        String rest = (origAscii.substring(0, m.start()) + " " + origAscii.substring(m.end())).trim();
        for (String w : new String[]{"am", "pm", "AM", "PM", "সকাল", "দুপুর", "বিকাল", "বিকেল", "সন্ধ্যা", "রাত", "ভোর"}) {
            rest = rest.replace(w, " ");
        }
        return rest.replaceAll("\\s+", " ").trim();
    }

    private static boolean has(String s, String... ws) {
        for (String w : ws) if (s.contains(w)) return true;
        return false;
    }

    /** টাইমারের সেকেন্ড: "30" = ৩০ মিনিট, "২ ঘণ্টা", "45 মিনিট"। অবৈধ হলে -1। */
    static int timerSeconds(String arg) {
        String s = OfflineBrain.toAsciiDigits(arg).toLowerCase(Locale.ROOT);
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(s);
        if (!m.find()) return -1;
        double v = Double.parseDouble(m.group(1));
        boolean hour = has(s, "ঘণ্টা", "ঘন্টা", "hour", "hr");
        boolean sec = has(s, "সেকেন্ড", "sec");
        double secs = hour ? v * 3600 : sec ? v : v * 60;
        if (secs < 1 || secs > 86400) return -1;
        return (int) Math.round(secs);
    }

    /** "alibaba.com" → "https://alibaba.com"। শুধু http/https; অন্যথায় null। */
    static String normalizeUrl(String in) {
        String u = in.trim();
        if (u.isEmpty() || u.contains(" ")) return null;
        String low = u.toLowerCase(Locale.ROOT);
        if (low.startsWith("http://") || low.startsWith("https://")) return u;
        if (low.contains("://") || low.startsWith("javascript:") || low.startsWith("file:")
                || low.startsWith("intent:") || low.startsWith("data:")) return null;
        if (!u.contains(".")) return null;
        return "https://" + u;
    }

    /** বাংলা নাম → ইংরেজি লেবেল, অ্যাপ খুঁজতে। */
    static String appAlias(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        String[][] map = {
            {"হোয়াটসঅ্যাপ", "whatsapp"}, {"হোয়াটসএপ", "whatsapp"}, {"ফেসবুক", "facebook"},
            {"ইউটিউব", "youtube"}, {"ক্যালকুলেটর", "calculator"}, {"ক্যামেরা", "camera"},
            {"সেটিংস", "settings"}, {"গ্যালারি", "gallery"}, {"ক্রোম", "chrome"},
            {"মেসেঞ্জার", "messenger"}, {"ঘড়ি", "clock"}, {"ফোন", "phone"}
        };
        for (String[] p : map) if (n.equals(p[0])) return p[1];
        return n;
    }

    /** ওয়াইফাই সিগন্যাল (dBm) → বাংলা বর্ণনা। */
    static String signalText(int rssi) {
        if (rssi >= -55) return "খুব ভালো";
        if (rssi >= -67) return "ভালো";
        if (rssi >= -78) return "মাঝারি";
        return "দুর্বল";
    }

    /** মোবাইল নেটওয়ার্কের ধরন-নাম → 2G/3G/4G/5G। */
    static String generation(String subtype) {
        String s = subtype == null ? "" : subtype.toUpperCase(Locale.ROOT);
        if (s.contains("NR")) return "5G";
        if (s.contains("LTE")) return "4G";
        if (s.contains("HSPA") || s.contains("HSDPA") || s.contains("HSUPA") || s.contains("UMTS")
                || s.contains("EVDO") || s.contains("TD-SCDMA") || s.contains("EHRPD")) return "3G";
        if (s.contains("EDGE") || s.contains("GPRS") || s.contains("GSM") || s.contains("CDMA")
                || s.contains("1XRTT") || s.contains("IDEN")) return "2G";
        return subtype == null || subtype.isEmpty() ? "অজানা" : subtype;
    }
}
