package com.nomad.ioskeyboard;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String SOUND = "sound", HAPTIC = "haptic", AUTOCAP = "autocap", AUTOCORRECT = "autocorrect",
            PREDICT = "predict", DOUBLE_SPACE = "double_space", CAPS_LOCK = "caps_lock", SWIPE = "swipe",
            LAYOUT = "layout", LANG = "lang", THEME = "theme", BOTTOM_BAR = "bottom_bar", DICTATION = "dictation",
            IOS_HAPTIC = "ios_haptic", IOS_SOUND = "ios_sound", SMART_TOUCH = "smart_touch", GLASS = "glass",
            ANIMS = "anims", NEXT_WORD = "next_word", EMOJI_SUGGEST = "emoji_suggest", FR_SPACING = "fr_spacing",
            SHORTCUTS = "shortcuts_on", SWIPE_DOWN = "swipe_down", SHAKE_UNDO = "shake_undo", ONE_HAND = "one_hand",
            SHORTCUT_LIST = "shortcut_list", KB_TR = "kb_tr", KB_HE = "kb_he", ACTIVE_KB = "active_kb", ICON_POS = "icon_pos", GRAMMAR = "grammar", QUICK_MENU = "quick_menu", SWIPE_CLOSE = "swipe_close", EASTER = "easter",
            TR_EMOJI = "tr_emoji", TR_BAR = "tr_bar", TR_RETURN = "tr_return", TR_TARGET = "tr_target", TR_SEND = "tr_send", TR_ONLINE = "tr_online";
    static final String DEFAULT_SHORTCUTS = "stp\ts'il te plaît\nsvp\ts'il vous plaît\ntkt\tt'inquiète\njsp\tje sais pas\nbcp\tbeaucoup\npk\tpourquoi\nrdv\trendez-vous\nqqn\tquelqu'un\nqqch\tquelque chose\nauj\taujourd'hui";

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static java.util.List<String> keyboards(Context c) {
        java.util.List<String> l = new java.util.ArrayList<>();
        l.add("latin");
        if (bool(c, KB_TR)) l.add("tr");
        if (bool(c, KB_HE)) l.add("he");
        return l;
    }

    static String activeKeyboard(Context c) {
        String a = str(c, ACTIVE_KB);
        return keyboards(c).contains(a) ? a : "latin";
    }

    static String layoutId(Context c) {
        String a = activeKeyboard(c);
        return a.equals("latin") ? str(c, LAYOUT) : a;
    }

    static String dictLang(Context c) {
        String a = activeKeyboard(c);
        return a.equals("latin") ? str(c, LANG) : a;
    }

    static boolean bool(Context c, String k) {
        return get(c).getBoolean(k, !BOTTOM_BAR.equals(k) && !DICTATION.equals(k) && !FR_SPACING.equals(k) && !KB_TR.equals(k) && !KB_HE.equals(k) && !SWIPE_CLOSE.equals(k));
    }

    static String str(Context c, String k) {
        SharedPreferences p = get(c);
        switch (k) {
            case LAYOUT: return p.getString(k, "azerty");
            case LANG: return p.getString(k, "fr_en");
            case ONE_HAND: return p.getString(k, "off");
            case ACTIVE_KB: return p.getString(k, "latin");
            case ICON_POS: return p.getString(k, "top");
            case TR_TARGET: return p.getString(k, "en");
            case TR_SEND: return p.getString(k, "en,he,tr");
            case SHORTCUT_LIST: return p.getString(k, DEFAULT_SHORTCUTS);
            default: return p.getString(k, "auto");
        }
    }
}
