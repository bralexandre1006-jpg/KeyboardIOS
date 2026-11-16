package com.nomad.ioskeyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Layouts {
    static final int LETTERS = 0, NUM = 1, SYM = 2;
    static final int VAR_TEXT = 0, VAR_EMAIL = 1, VAR_URL = 2;

    static final Map<String, String[]> ALTS = new HashMap<>();

    static {
        alt("e", "e é è ê ë");
        alt("a", "a à â æ ä");
        alt("c", "c ç");
        alt("i", "i î ï");
        alt("o", "o ô œ ö");
        alt("u", "u ù û ü");
        alt("y", "y ÿ");
        alt("n", "n ñ");
        alt("s", "s ş ß");
        alt("z", "z ž");
        alt("l", "l ł");
        alt("g", "g ğ");
        alt("ı", "ı i");
        alt("'", "' ’ ‘ ` ^ ¨");
        alt("0", "0 °");
        alt("-", "- – — •");
        alt("/", "/ \\");
        alt("€", "€ $ £ ¥ ₩ ₽");
        alt("$", "$ € £ ¥ ₩ ₽");
        alt("&", "& §");
        alt(".", ". …");
        alt("?", "? ¿");
        alt("!", "! ¡");
        alt("\"", "\" « » „ “ ”");
        alt("%", "% ‰");
        alt("=", "= ≠ ≈");
        alt(".com", ".com .fr .net .org .eu");
    }

    private static void alt(String k, String v) {
        ALTS.put(k, v.split(" "));
    }

    static List<List<Key>> build(int mode, String layout, int variant, String returnLabel, boolean mic, boolean globe) {
        List<List<Key>> rows = new ArrayList<>();
        boolean qwerty = "qwerty".equals(layout);
        if (mode == LETTERS && "tr".equals(layout)) {
            float w = 10f / 12f;
            rows.add(chars("qwertyuıopğü", w));
            List<Key> r2 = chars("asdfghjklşi", w);
            r2.add(0, spacer(w / 2)); r2.add(spacer(w / 2));
            rows.add(r2);
            List<Key> r3 = new ArrayList<>();
            r3.add(new Key(Key.SHIFT, "", "", 1.25f));
            r3.addAll(chars("zxcvbnmöç", w));
            r3.add(new Key(Key.DELETE, "", "", 1.25f));
            rows.add(r3);
        } else if (mode == LETTERS && "he".equals(layout)) {
            List<Key> r1 = chars("קראטוןםפ", 1f);
            r1.add(new Key(Key.DELETE, "", "", 2f));
            rows.add(r1);
            rows.add(chars("שדגכעיחלךף", 1f));
            List<Key> r3 = chars("זסבהנמצתץ", 1f);
            r3.add(0, spacer(0.5f)); r3.add(spacer(0.5f));
            rows.add(r3);
        } else if (mode == LETTERS) {
            rows.add(chars(qwerty ? "qwertyuiop" : "azertyuiop"));
            List<Key> r2 = chars(qwerty ? "asdfghjkl" : "qsdfghjklm");
            if (qwerty) { r2.add(0, spacer(0.5f)); r2.add(spacer(0.5f)); }
            rows.add(r2);
            List<Key> r3 = new ArrayList<>();
            r3.add(new Key(Key.SHIFT, "", "", 1.35f));
            r3.add(spacer(0.15f));
            r3.addAll(chars(qwerty ? "zxcvbnm" : "wxcvbn'"));
            r3.add(spacer(0.15f));
            r3.add(new Key(Key.DELETE, "", "", 1.35f));
            rows.add(r3);
        } else {
            rows.add(chars(mode == NUM ? "1234567890" : "[]{}#%^*+="));
            rows.add(chars(mode == NUM ? ("tr".equals(layout) ? "-/:;()₺&@\"" : "he".equals(layout) ? "-/:;()₪&@\"" : "-/:;()€&@\"") : "_\\|~<>$£¥•"));
            List<Key> r3 = new ArrayList<>();
            r3.add(mode == NUM ? new Key(Key.MODE_SYM, "#+=", "", 1.35f) : new Key(Key.MODE_NUM, "123", "", 1.35f));
            r3.add(spacer(0.15f));
            for (String s : new String[]{".", ",", "?", "!", "'"}) r3.add(chr(s, 1.4f));
            r3.add(spacer(0.15f));
            r3.add(new Key(Key.DELETE, "", "", 1.35f));
            rows.add(r3);
        }
        List<Key> r4 = new ArrayList<>();
        r4.add(mode == LETTERS ? new Key(Key.MODE_NUM, "123", "", 1.25f) : new Key(Key.MODE_ABC, "ABC", "", 1.25f));
        float spaceCut = 0;
        if (globe) { r4.add(new Key(Key.GLOBE, "", "", 1f)); r4.add(new Key(Key.EMOJI, "", "", 1f)); spaceCut = -0.75f; }
        else r4.add(new Key(Key.EMOJI, "", "", 1.25f));
        if (variant == VAR_EMAIL) {
            r4.add(new Key(Key.SPACE, "espace", " ", 3f + spaceCut));
            r4.add(chr("@", 1f));
            r4.add(chr(".", 1f));
        } else if (variant == VAR_URL) {
            r4.add(new Key(Key.SPACE, "espace", " ", 3.5f + spaceCut));
            Key dot = chr(".", 1.5f);
            dot.alts = new String[]{".", ".com", ".fr", "/", ".net", ".org"};
            r4.add(dot);
        } else if (mic) {
            r4.add(new Key(Key.MIC, "", "", 1.25f));
            r4.add(new Key(Key.SPACE, "espace", " ", 3.75f + spaceCut));
        } else {
            r4.add(new Key(Key.SPACE, "espace", " ", 5f + spaceCut));
        }
        r4.add(new Key(Key.RETURN, returnLabel, "\n", 2.5f));
        rows.add(r4);
        for (int i = 0; i < rows.size(); i++) for (Key k : rows.get(i)) k.row = i;
        return rows;
    }

    private static List<Key> chars(String s) { return chars(s, 1f); }

    private static List<Key> chars(String s, float w) {
        List<Key> l = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) l.add(chr(String.valueOf(s.charAt(i)), w));
        return l;
    }


    private static Key chr(String s, float w) {
        Key k = new Key(Key.CHAR, s, s, w);
        k.alts = ALTS.get(s);
        return k;
    }

    private static Key spacer(float w) {
        return new Key(Key.SPACER, "", "", w);
    }
}
