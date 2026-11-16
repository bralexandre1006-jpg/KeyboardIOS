package com.nomad.ioskeyboard;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.icu.text.BreakIterator;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class IosKeyboardService extends InputMethodService implements KeyboardView.Listener, EmojiView.Listener {
    private KeyboardView keyboard;
    private EmojiView emojiView;
    private FrameLayout root;
    private Dictionary dict;
    private String loadedLang = "", loadedLayout = "";

    private boolean suggest, autocorrect, autocap, doubleSpace, predict, multiline, numeric;
    private int action;
    private int variant;
    private long lastSpaceTime;
    private String revertOriginal, revertCorrected;
    private String gestureWord;
    private List<String> gestureAlts = new ArrayList<>();
    private boolean autoSpaced;
    private final String[] sugValues = {"", "", ""};
    private String pendingCorrection;
    private int emojiSlot = -1;
    private final List<PointF> touches = new ArrayList<>();
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable sugTask = this::updateSuggestionsNow;
    private int sugGen;
    private String pendingFor;
    private int selStart = -1, selEnd = -1;
    private boolean frSpacing, shortcutsOn, emojiSuggest, nextWord, grammar, easter;
    private static final java.util.Set<String> SUBJ = new java.util.HashSet<>(java.util.Arrays.asList(
            "il", "elle", "on", "qui", "ça", "ca", "cela", "y", "n'y", "l'", "t'", "m'", "lui", "personne", "tout", "rien",
            "chacun", "quelqu'un", "qu'il", "qu'elle", "qu'on", "s'il", "elle-même", "lui-même", "ce"));
    private static final java.util.Set<String> A_NEXT = new java.util.HashSet<>(java.util.Arrays.asList(
            "la", "l'", "demain", "plus", "bientôt", "bientot", "tout", "toi", "moi", "eux", "elles", "côté", "cote", "cause",
            "partir", "peine", "pied", "pieds", "vélo", "velo", "cheval", "midi", "minuit", "chaque", "quelle", "quel",
            "quelles", "quels", "ma", "ta", "sa", "mes", "tes", "ses", "nos", "vos", "leur", "leurs", "quoi", "part",
            "nouveau", "travers", "condition", "droite", "gauche", "cet", "cette", "lundi", "mardi", "mercredi", "jeudi",
            "vendredi", "samedi", "dimanche", "l'heure", "table", "fond", "fond", "l'aise", "l'école", "l'ecole", "propos",
            "jamais", "vie", "toute", "toutes", "tous", "nous", "vous", "domicile", "pas", "deux", "trois", "quatre", "cinq"));

    private void fixAccentA() {
        if (!grammar || !Prefs.activeKeyboard(this).equals("latin")) return;
        InputConnection ic = ic();
        if (ic == null) return;
        String b = before(60);
        if (!b.endsWith(" ")) return;
        String[] w = b.trim().split("\\s+");
        if (w.length < 2) return;
        String next = w[w.length - 1], mid = w[w.length - 2], prev = w.length >= 3 ? w[w.length - 3] : "";
        String ml = mid.toLowerCase();
        if (!ml.equals("a") && !ml.equals("à")) return;
        String nl = next.toLowerCase().replace('’', '\'');
        String pl = prev.toLowerCase().replace('’', '\'');
        boolean prevName = !prev.isEmpty() && Character.isUpperCase(prev.charAt(0)) && w.length >= 4
                && !w[w.length - 4].matches(".*[.!?]$");
        boolean subj = SUBJ.contains(pl) || pl.endsWith("'il") || prevName;
        boolean infinitive = nl.length() > 3 && (nl.endsWith("er") || nl.endsWith("ir") || nl.endsWith("re") || nl.endsWith("oir")) && dict.isValid(nl);
        String target = subj ? "a" : (A_NEXT.contains(nl) || nl.startsWith("l'") || infinitive) ? "à" : null;
        if (target == null || target.equals(ml)) return;
        if (Character.isUpperCase(mid.charAt(0))) target = target.toUpperCase();
        String tail = mid + " " + next + " ";
        if (!b.endsWith(tail)) return;
        ic.beginBatchEdit();
        ic.deleteSurroundingText(tail.length(), 0);
        ic.commitText(target + " " + next + " ", 1);
        ic.endBatchEdit();
    }
    private final java.util.Map<String, String> shortcuts = new java.util.HashMap<>();
    private android.hardware.SensorManager sensors;
    private android.hardware.SensorEventListener shakeListener;
    private long lastShake, lastShakeAction;

    private void loadShortcuts() {
        shortcuts.clear();
        for (String line : Prefs.str(this, Prefs.SHORTCUT_LIST).split("\n")) {
            int t = line.indexOf('\t');
            if (t > 0) shortcuts.put(line.substring(0, t).toLowerCase(), line.substring(t + 1));
        }
    }

    private void registerShake(boolean on) {
        if (sensors == null) sensors = (android.hardware.SensorManager) getSystemService(SENSOR_SERVICE);
        if (shakeListener != null) { sensors.unregisterListener(shakeListener); shakeListener = null; }
        if (!on || sensors == null) return;
        android.hardware.Sensor acc = sensors.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER);
        if (acc == null) return;
        shakeListener = new android.hardware.SensorEventListener() {
            @Override public void onSensorChanged(android.hardware.SensorEvent e) {
                float g = (float) Math.sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / 9.81f;
                if (g < 2.6f) return;
                long now = SystemClock.uptimeMillis();
                if (now - lastShake < 600 && now - lastShakeAction > 1500) {
                    lastShakeAction = now;
                    onDelete(true);
                    Toast.makeText(IosKeyboardService.this, "Saisie annulée", Toast.LENGTH_SHORT).show();
                }
                lastShake = now;
            }
            @Override public void onAccuracyChanged(android.hardware.Sensor s, int a) { }
        };
        sensors.registerListener(shakeListener, acc, android.hardware.SensorManager.SENSOR_DELAY_UI);
    }
    private boolean searching, swipeBeforeSearch;
    private final StringBuilder query = new StringBuilder();
    private List<String> results = new ArrayList<>();

    private SpeechRecognizer recognizer;
    private boolean listening;
    private String voicePrefix = "";

    @Override
    public void onCreate() {
        super.onCreate();
        dict = new Dictionary(this);
        reloadDictionary();
        new Thread(() -> EmojiData.loadKeywords(this)).start();
    }

    private void reloadDictionary() {
        String lang = Prefs.dictLang(this), layout = Prefs.layoutId(this);
        if (lang.equals(loadedLang) && layout.equals(loadedLayout)) return;
        loadedLang = lang; loadedLayout = layout;
        new Thread(() -> {
            dict.load(lang, layout);
            if (keyboard != null) keyboard.post(this::updateSuggestions);
        }).start();
    }

    @Override
    public View onCreateInputView() {
        keyboard = new KeyboardView(this);
        keyboard.setListener(this);
        emojiView = new EmojiView(this);
        emojiView.setListener(this);
        emojiView.setVisibility(View.GONE);
        root = new FrameLayout(this);
        root.setBackgroundColor(0);
        root.addView(keyboard, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(emojiView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        reloadDictionary();
        if (searching) endSearch();
        Theme theme = Theme.get(this);
        keyboard.applyTheme(theme);
        styleWindow(theme);

        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        int var = info.inputType & InputType.TYPE_MASK_VARIATION;
        boolean password = cls == InputType.TYPE_CLASS_TEXT && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
        boolean email = cls == InputType.TYPE_CLASS_TEXT && (var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);
        boolean url = cls == InputType.TYPE_CLASS_TEXT && var == InputType.TYPE_TEXT_VARIATION_URI;
        numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME;
        multiline = (info.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        variant = email ? Layouts.VAR_EMAIL : url ? Layouts.VAR_URL : Layouts.VAR_TEXT;

        predict = Prefs.bool(this, Prefs.PREDICT);
        suggest = predict && !password && !email && !url && !numeric && cls != 0;
        autocorrect = suggest && Prefs.bool(this, Prefs.AUTOCORRECT);
        autocap = Prefs.bool(this, Prefs.AUTOCAP) && !password && !email && !url && !numeric
                && var != InputType.TYPE_TEXT_VARIATION_FILTER;
        doubleSpace = false;

        action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean noEnter = (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        if (noEnter) action = EditorInfo.IME_ACTION_NONE;
        String label;
        boolean blue = true;
        switch (action) {
            case EditorInfo.IME_ACTION_GO: label = "aller"; break;
            case EditorInfo.IME_ACTION_SEARCH: label = "rechercher"; break;
            case EditorInfo.IME_ACTION_SEND: label = "envoyer"; break;
            case EditorInfo.IME_ACTION_NEXT: label = "suivant"; blue = false; break;
            case EditorInfo.IME_ACTION_PREVIOUS: label = "précédent"; blue = false; break;
            case EditorInfo.IME_ACTION_DONE: label = "OK"; break;
            default: label = "retour"; blue = false; action = EditorInfo.IME_ACTION_NONE;
        }
        String lang = Prefs.str(this, Prefs.LANG);
        String kb = Prefs.activeKeyboard(this);
        String langLabel = kb.equals("tr") ? "TR" : kb.equals("he") ? "HE" : lang.equals("fr_en") ? "FR EN" : lang.toUpperCase();
        keyboard.showGlobe = Prefs.keyboards(this).size() > 1;
        keyboard.iconPos = Prefs.str(this, Prefs.ICON_POS);
        keyboard.locale = kb.equals("tr") ? new java.util.Locale("tr") : java.util.Locale.FRENCH;
        keyboard.glass = Prefs.bool(this, Prefs.GLASS);
        keyboard.anims = Prefs.bool(this, Prefs.ANIMS);
        keyboard.smartTouch = Prefs.bool(this, Prefs.SMART_TOUCH);
        keyboard.swipeDown = Prefs.bool(this, Prefs.SWIPE_DOWN);
        keyboard.oneHand = Prefs.str(this, Prefs.ONE_HAND);
        keyboard.setLetterProbs(null);
        if (kb.equals("he")) autocap = false;
        frSpacing = Prefs.bool(this, Prefs.FR_SPACING) && kb.equals("latin");
        shortcutsOn = Prefs.bool(this, Prefs.SHORTCUTS);
        emojiSuggest = Prefs.bool(this, Prefs.EMOJI_SUGGEST);
        nextWord = Prefs.bool(this, Prefs.NEXT_WORD);
        grammar = Prefs.bool(this, Prefs.GRAMMAR);
        easter = Prefs.bool(this, Prefs.EASTER);
        keyboard.closeMenu();
        keyboard.quickMenu = Prefs.bool(this, Prefs.QUICK_MENU);
        keyboard.swipeClose = Prefs.bool(this, Prefs.SWIPE_CLOSE);
        keyboard.showTrIcon = Prefs.bool(this, Prefs.TR_BAR) && suggest;
        keyboard.trEmoji = Prefs.bool(this, Prefs.TR_EMOJI) && suggest;
        keyboard.trReturn = Prefs.bool(this, Prefs.TR_RETURN) && suggest;
        Translate.online = Prefs.bool(this, Prefs.TR_ONLINE);
        if (!restarting) { trMode = false; keyboard.setTranslation(false, "", ""); }
        loadShortcuts();
        registerShake(Prefs.bool(this, Prefs.SHAKE_UNDO));
        keyboard.capsLockEnabled = Prefs.bool(this, Prefs.CAPS_LOCK);
        keyboard.swipeEnabled = Prefs.bool(this, Prefs.SWIPE) && suggest;
        keyboard.showSuggestionBar = true;
        keyboard.showMic = Prefs.bool(this, Prefs.DICTATION) && variant == Layouts.VAR_TEXT && !numeric;
        boolean bar = Prefs.bool(this, Prefs.BOTTOM_BAR);
        if (bar != keyboard.showBottomBar) { keyboard.showBottomBar = bar; keyboard.requestLayout(); }
        keyboard.configure(Prefs.layoutId(this), variant, label, blue, langLabel);
        if (!restarting && keyboard.anims) {
            keyboard.setTranslationY(28 * getResources().getDisplayMetrics().density);
            keyboard.setAlpha(0.5f);
            keyboard.animate().translationY(0).alpha(1).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        }
        if (!restarting) {
            keyboard.forceShift(KeyboardView.SHIFT_OFF);
            keyboard.setMode(numeric ? Layouts.NUM : Layouts.LETTERS);
        }
        showKeyboard();
        revertOriginal = revertCorrected = null;
        selStart = info.initialSelStart;
        selEnd = info.initialSelEnd;
        gestureWord = null;
        autoSpaced = false;
        updateShift();
        updateSuggestions();
    }

    @SuppressWarnings("deprecation")
    private void styleWindow(Theme t) {
        if (getWindow() == null) return;
        Window w = getWindow().getWindow();
        if (w != null) {
            w.setNavigationBarColor(t.bg);
            if (Build.VERSION.SDK_INT >= 31) {
                try { w.setBackgroundBlurRadius(Prefs.bool(this, Prefs.GLASS) ? 60 : 0); } catch (Exception ignored) { }
            }
            View decor = w.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (Build.VERSION.SDK_INT >= 26) {
                if (t.dark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    @Override
    public void onFinishInputView(boolean finishing) {
        super.onFinishInputView(finishing);
        registerShake(false);
        stopVoice();
        dict.save();
    }

    @Override
    public void onDestroy() {
        stopVoice();
        dict.save();
        super.onDestroy();
    }

    @Override
    public void onUpdateSelection(int os, int oe, int ns, int ne, int cs, int ce) {
        super.onUpdateSelection(os, oe, ns, ne, cs, ce);
        selStart = ns;
        selEnd = ne;
        if (listening || searching) return;
        updateShift();
        updateSuggestions();
    }

    // ---------- Helpers ----------

    private InputConnection ic() { return getCurrentInputConnection(); }

    private String before(int n) {
        InputConnection ic = ic();
        if (ic == null) return "";
        CharSequence s = ic.getTextBeforeCursor(n, 0);
        return s == null ? "" : s.toString();
    }

    private static boolean wordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '\'' || c == '’' || c == '-';
    }

    private String currentWord() {
        String b = before(48);
        int i = b.length();
        while (i > 0 && wordChar(b.charAt(i - 1))) i--;
        while (i < b.length() && (b.charAt(i) == '\'' || b.charAt(i) == '’' || b.charAt(i) == '-')) i++;
        return b.substring(i);
    }

    private String previousWord(String b) {
        int i = b.length();
        while (i > 0 && wordChar(b.charAt(i - 1))) i--;
        int end = i;
        while (end > 0 && b.charAt(end - 1) == ' ') end--;
        int st = end;
        while (st > 0 && wordChar(b.charAt(st - 1))) st--;
        return b.substring(st, end);
    }

    private boolean sentenceStart() {
        String b = before(64);
        int i = b.length();
        if (i == 0) return true;
        char last = b.charAt(i - 1);
        if (last == '\n') return true;
        if (!Character.isWhitespace(last)) return false;
        while (i > 0 && b.charAt(i - 1) == ' ') i--;
        if (i == 0) return true;
        char c = b.charAt(i - 1);
        return c == '.' || c == '!' || c == '?' || c == '\n' || c == '…';
    }

    private boolean sentenceStartBefore(int wordLen) {
        String b = before(64);
        if (b.length() < wordLen) return true;
        b = b.substring(0, b.length() - wordLen).trim();
        if (b.isEmpty()) return true;
        char c = b.charAt(b.length() - 1);
        return c == '.' || c == '!' || c == '?' || c == '…';
    }

    private void updateShift() {
        if (keyboard == null || searching) return;
        if (keyboard.mode != Layouts.LETTERS) return;
        keyboard.setShift(autocap && sentenceStart() ? KeyboardView.SHIFT_ON : KeyboardView.SHIFT_OFF);
    }

    private String matchCase(String typed, String word) {
        if (typed.length() > 1 && typed.equals(typed.toUpperCase()) && !typed.equals(typed.toLowerCase())) return word.toUpperCase();
        if (!typed.isEmpty() && Character.isUpperCase(typed.charAt(0)))
            return word.substring(0, 1).toUpperCase() + word.substring(1);
        return word;
    }

    private String shiftCase(String w) {
        if (keyboard.shift == KeyboardView.SHIFT_LOCK) return w.toUpperCase();
        if (keyboard.shift == KeyboardView.SHIFT_ON) return w.substring(0, 1).toUpperCase() + w.substring(1);
        return w;
    }

    private void updateSuggestions() {
        main.removeCallbacks(sugTask);
        main.post(sugTask);
    }

    private static String wordAtEnd(String b) {
        int i = b.length();
        while (i > 0 && wordChar(b.charAt(i - 1))) i--;
        while (i < b.length() && (b.charAt(i) == '\'' || b.charAt(i) == '’' || b.charAt(i) == '-')) i++;
        return b.substring(i);
    }

    private static boolean startOfSentence(String b) {
        b = b.trim();
        if (b.isEmpty()) return true;
        char c = b.charAt(b.length() - 1);
        return c == '.' || c == '!' || c == '?' || c == '…';
    }

    private void updateSuggestionsNow() {
        if (keyboard == null || searching) return;
        if (trMode) { requestTranslate(); return; }
        sugGen++;
        pendingCorrection = null;
        pendingFor = null;
        emojiSlot = -1;
        for (int i = 0; i < 3; i++) sugValues[i] = "";
        if (!suggest || !dict.ready) { keyboard.setSuggestions(new String[3], -1); return; }
        String b = before(64);
        String word = wordAtEnd(b);
        if (gestureWord != null && b.endsWith(gestureWord)) {
            String[] show = new String[3];
            List<String> alts = new ArrayList<>();
            for (String a : gestureAlts) if (!a.equalsIgnoreCase(gestureWord)) alts.add(a);
            sugValues[0] = alts.size() > 0 ? alts.get(0) : "";
            sugValues[1] = gestureWord;
            sugValues[2] = alts.size() > 1 ? alts.get(1) : "";
            System.arraycopy(sugValues, 0, show, 0, 3);
            keyboard.setSuggestions(show, 1);
            return;
        }
        gestureWord = null;
        if (word.isEmpty()) {
            List<String> p = nextWord ? dict.predict(previousWord(b + "x"), 3) : new ArrayList<>();
            boolean start = b.isEmpty() || b.endsWith("\n") || (Character.isWhitespace(b.charAt(b.length() - 1)) && startOfSentence(b));
            String kbd = Prefs.activeKeyboard(this);
            String[] defaults = kbd.equals("tr") ? (start ? new String[]{"Ben", "Bu", "Bir"} : new String[]{"ve", "bir", "bu"})
                    : kbd.equals("he") ? new String[]{"אני", "זה", "לא"}
                    : start ? new String[]{"Je", "J’ai", "Le"} : new String[]{"de", "et", "la"};
            for (String s : defaults) if (p.size() < 3 && !p.contains(s.toLowerCase())) p.add(s);
            for (int i = 0; i < 3 && i < p.size(); i++) sugValues[i] = start ? cap(p.get(i)) : p.get(i);
            String[] show = {sugValues[0], sugValues[1], sugValues[2]};
            keyboard.setSuggestions(show, -1);
            return;
        }
        String rep = shortcutsOn ? shortcuts.get(word.toLowerCase()) : null;
        if (rep != null) {
            if (Character.isUpperCase(word.charAt(0))) rep = cap(rep);
            sugValues[0] = word; sugValues[1] = rep; sugValues[2] = "";
            pendingCorrection = rep; pendingFor = word;
            keyboard.setSuggestions(new String[]{"«" + word + "»", rep, ""}, 1);
            return;
        }
        if (word.length() != touches.size()) touches.clear();
        final List<PointF> t = touches.isEmpty() ? null : new ArrayList<>(touches);
        final java.util.Map<Character, PointF> centers = t == null ? null : keyboard.letterCenters();
        final float keyW = keyboard.keyWidth();
        final boolean properNoun = Character.isUpperCase(word.charAt(0)) && !startOfSentence(b.substring(0, b.length() - word.length()));
        final boolean ac = autocorrect;
        final int gen = sugGen;
        final String w = word;
        String[] quick = {"«" + word + "»", "", ""};
        keyboard.setSuggestions(quick, -1);
        sugValues[0] = word;
        worker.execute(() -> {
            String[] cr = ac ? dict.correctFull(w, t, centers, keyW) : new String[]{null, "0"};
            List<String> comp = dict.complete(w, 3);
            String corr = cr[0];
            boolean confident = "1".equals(cr[1]);
            String c1 = corr != null && !corr.equalsIgnoreCase(w) ? matchCase(w, corr) : null;
            String emo = emojiSuggest ? EmojiData.forWord(c1 != null && confident ? c1 : w) : null;
            if (emo == null && emojiSuggest) emo = EmojiData.forWord(w);
            final java.util.Map<Character, Float> probs = keyboard.smartTouch ? dict.nextLetterProbs(w) : null;
            final String fEmo = emo;
            main.post(() -> {
                if (gen != sugGen) return;
                keyboard.setLetterProbs(probs);
                int hl = -1, slot = 1;
                sugValues[0] = w; sugValues[1] = ""; sugValues[2] = "";
                if (c1 != null) {
                    sugValues[1] = c1;
                    slot = 2;
                    if (confident && !properNoun) { pendingCorrection = c1; pendingFor = w; hl = 1; }
                }
                for (String c : comp) {
                    if (slot > 2) break;
                    String m = matchCase(w, c);
                    if (m.equals(sugValues[1]) || m.equals(w)) continue;
                    sugValues[slot++] = m;
                }
                if (fEmo != null) { sugValues[2] = fEmo; emojiSlot = 2; }
                keyboard.setSuggestions(new String[]{"«" + w + "»", sugValues[1], sugValues[2]}, hl);
            });
        });
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /** Applies pending autocorrection to the word before the cursor. Returns true if replaced. */
    private boolean applyCorrection() {
        InputConnection ic = ic();
        if (ic == null || !autocorrect) return false;
        String b = before(64);
        String word = wordAtEnd(b);
        if (word.isEmpty()) return false;
        String corr = word.equals(pendingFor) ? pendingCorrection : null;
        if (corr == null && !word.equals(pendingFor)) {
            String[] cr = dict.correctFull(word, word.length() == touches.size() ? new ArrayList<>(touches) : null,
                    word.length() == touches.size() ? keyboard.letterCenters() : null, keyboard.keyWidth());
            boolean properNoun = Character.isUpperCase(word.charAt(0)) && !startOfSentence(b.substring(0, b.length() - word.length()));
            if (cr[0] != null && "1".equals(cr[1]) && !properNoun && !cr[0].equalsIgnoreCase(word)) corr = matchCase(word, cr[0]);
        }
        if (corr == null) return false;
        ic.deleteSurroundingText(word.length(), 0);
        ic.commitText(corr, 1);
        revertOriginal = word;
        revertCorrected = corr;
        pendingCorrection = null;
        pendingFor = null;
        return true;
    }

    private void learnWord(String w) {
        if (w == null || w.isEmpty()) return;
        String b = before(80);
        dict.learn(previousWord(b.length() >= w.length() && b.endsWith(w) ? b : b + " " + w), w);
    }

    private void learnCurrent() {
        String w = currentWord();
        if (w.isEmpty()) return;
        dict.learn(previousWord(before(64)), w);
    }

    // ---------- KeyboardView.Listener ----------

    @Override
    public void onText(String s) {
        if (searching) { query.append(s.toLowerCase()); refreshSearch(); return; }
        InputConnection ic = ic();
        if (ic == null) return;
        ic.beginBatchEdit();
        boolean punct = s.length() == 1 && ".,!?;:)]}…".indexOf(s.charAt(0)) >= 0;
        if (punct) {
            if (autoSpaced && before(1).equals(" ")) ic.deleteSurroundingText(1, 0);
            String typed = currentWord();
            boolean corrected = applyCorrection();
            learnWord(corrected ? revertCorrected : typed);
            if (!corrected) revertOriginal = null;
        } else {
            revertOriginal = null;
        }
        if (gestureWord != null && s.length() == 1 && Character.isLetterOrDigit(s.charAt(0))) {
            ic.commitText(" ", 1);
        }
        gestureWord = null;
        if (frSpacing && s.length() == 1 && "?!:;".indexOf(s.charAt(0)) >= 0) {
            String pb = before(1);
            if (!pb.isEmpty() && !Character.isWhitespace(pb.charAt(0))) ic.commitText(" ", 1);
        }
        if (s.length() == 1 && Character.isLetter(s.charAt(0)) && keyboard.mode == Layouts.LETTERS) touches.add(new PointF(keyboard.lastTapX, keyboard.lastTapY));
        else touches.clear();
        ic.commitText(s, 1);
        ic.endBatchEdit();
        autoSpaced = false;
        checkEaster();
        afterEdit();
    }

    private void afterEdit() {
        updateShift();
        updateSuggestions();
    }

    @Override
    public void onSpace() {
        if (searching) { query.append(' '); refreshSearch(); return; }
        InputConnection ic = ic();
        if (ic == null) return;
        long now = SystemClock.uptimeMillis();
        String b = before(3);
        ic.beginBatchEdit();
        if (doubleSpace && now - lastSpaceTime < 700 && b.length() >= 2 && b.endsWith(" ")
                && Character.isLetterOrDigit(b.charAt(b.length() - 2))) {
            ic.deleteSurroundingText(1, 0);
            ic.commitText(". ", 1);
            lastSpaceTime = 0;
            revertOriginal = null;
        } else {
            String w = shortcutsOn ? currentWord() : "";
            String rep = w.isEmpty() ? null : shortcuts.get(w.toLowerCase());
            if (rep != null) {
                if (Character.isUpperCase(w.charAt(0))) rep = cap(rep);
                ic.deleteSurroundingText(w.length(), 0);
                ic.commitText(rep, 1);
                revertOriginal = w;
                revertCorrected = rep;
            } else {
                String typed = currentWord();
                boolean corrected = applyCorrection();
                if (!corrected) revertOriginal = null;
                learnWord(corrected ? revertCorrected : typed);
            }
            ic.commitText(" ", 1);
            lastSpaceTime = now;
        }
        ic.endBatchEdit();
        fixAccentA();
        checkEaster();
        gestureWord = null;
        autoSpaced = false;
        touches.clear();
        if (keyboard.mode != Layouts.LETTERS) keyboard.setMode(Layouts.LETTERS);
        afterEdit();
    }

    @Override
    public void onDelete(boolean word) {
        if (searching) {
            if (query.length() > 0) query.setLength(word ? 0 : query.length() - 1);
            refreshSearch();
            return;
        }
        InputConnection ic = ic();
        if (ic == null) return;
        gestureWord = null;
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) {
            ic.commitText("", 1);
            afterEdit();
            return;
        }
        String b = before(64);
        if (b.isEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            return;
        }
        if (revertOriginal != null && b.endsWith(revertCorrected + " ")) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(revertCorrected.length() + 1, 0);
            ic.commitText(revertOriginal, 1);
            ic.endBatchEdit();
            dict.addWord(revertOriginal);
            revertOriginal = null;
            afterEdit();
            return;
        }
        revertOriginal = null;
        if (word) touches.clear();
        else if (!touches.isEmpty()) touches.remove(touches.size() - 1);
        int n;
        if (word) {
            int i = b.length();
            while (i > 0 && Character.isWhitespace(b.charAt(i - 1))) i--;
            if (i > 0 && wordChar(b.charAt(i - 1))) while (i > 0 && wordChar(b.charAt(i - 1))) i--;
            else if (i > 0) i--;
            n = b.length() - i;
        } else {
            BreakIterator it = BreakIterator.getCharacterInstance();
            it.setText(b);
            int prev = it.preceding(b.length());
            n = prev == BreakIterator.DONE ? 1 : b.length() - prev;
        }
        ic.deleteSurroundingText(Math.max(1, n), 0);
        autoSpaced = false;
        afterEdit();
    }

    @Override
    public void onReturn() {
        if (searching) { onSearchClose(); return; }
        InputConnection ic = ic();
        if (ic == null) return;
        if (trMode && action != EditorInfo.IME_ACTION_NONE) {
            String text = fieldText().trim();
            if (!text.isEmpty()) {
                if (text.equals(trFor) && !trResult.isEmpty()) { replaceField(trResult); sendAction(); }
                else Translate.translate(trSource(), Prefs.str(this, Prefs.TR_TARGET), text, (res, err) -> main.post(() -> {
                    if (res != null) replaceField(res);
                    sendAction();
                }));
                trResult = ""; trFor = "";
                return;
            }
        }
        if (action != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(action);
        } else {
            String typed = currentWord();
            boolean corrected = applyCorrection();
            learnWord(corrected ? revertCorrected : typed);
            if (multiline) ic.commitText("\n", 1);
            else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
        revertOriginal = null;
        gestureWord = null;
        autoSpaced = false;
        afterEdit();
    }

    @Override
    public void onEmoji() {
        if (searching) endSearch();
        int h = keyboard.normalHeight();
        emojiView.setTargetHeight(h);
        emojiView.prepare(keyboard.theme);
        emojiView.setVisibility(View.VISIBLE);
        keyboard.setVisibility(View.INVISIBLE);
        if (keyboard.anims) {
            emojiView.setAlpha(0f);
            emojiView.setTranslationY(16 * getResources().getDisplayMetrics().density);
            emojiView.animate().alpha(1).translationY(0).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
    }

    private void showKeyboard() {
        if (emojiView != null) emojiView.setVisibility(View.GONE);
        if (keyboard != null) keyboard.setVisibility(View.VISIBLE);
    }

    @Override
    public void onGlobe(boolean longPress) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (longPress) {
            imm.showInputMethodPicker();
            return;
        }
        boolean switched;
        if (Build.VERSION.SDK_INT >= 28) switched = switchToNextInputMethod(false);
        else switched = false;
        if (!switched) imm.showInputMethodPicker();
    }

    @Override
    public void onSuggestion(int i) {
        InputConnection ic = ic();
        if (ic == null) return;
        String s = sugValues[i];
        if (s.isEmpty()) return;
        if (i == emojiSlot) {
            ic.commitText(" " + s + " ", 1);
            emojiView.addRecent(s);
            autoSpaced = true;
            afterEdit();
            return;
        }
        ic.beginBatchEdit();
        String b = before(64);
        if (gestureWord != null && b.endsWith(gestureWord)) {
            ic.deleteSurroundingText(gestureWord.length(), 0);
            String w = matchCase(gestureWord, s);
            ic.commitText(w, 1);
            gestureWord = w;
            ic.endBatchEdit();
            updateSuggestions();
            return;
        }
        String word = currentWord();
        if (!word.isEmpty()) {
            if (i == 0) {
                dict.addWord(word);
                ic.commitText(" ", 1);
            } else {
                ic.deleteSurroundingText(word.length(), 0);
                ic.commitText(s + " ", 1);
            }
            dict.learn(previousWord(b), i == 0 ? word : s);
        } else {
            String prefix = !b.isEmpty() && !Character.isWhitespace(b.charAt(b.length() - 1)) ? " " : "";
            ic.commitText(prefix + s + " ", 1);
            dict.learn(previousWord(b + "x"), s);
        }
        touches.clear();
        ic.endBatchEdit();
        revertOriginal = null;
        autoSpaced = true;
        afterEdit();
    }

    @Override
    public void onGesture(List<PointF> path) {
        if (searching) return;
        InputConnection ic = ic();
        if (ic == null) return;
        List<String> words = dict.decodeGesture(path, keyboard.letterCenters(), keyboard.keyWidth());
        if (words.isEmpty()) return;
        String w = shiftCase(words.get(0));
        String b = before(2);
        ic.beginBatchEdit();
        learnCurrent();
        boolean needSpace = !b.isEmpty() && !Character.isWhitespace(b.charAt(b.length() - 1))
                && "([{\"«'’-/@".indexOf(b.charAt(b.length() - 1)) < 0;
        ic.commitText((needSpace ? " " : "") + w, 1);
        ic.endBatchEdit();
        dict.learn(previousWord(before(64)), w);
        touches.clear();
        gestureWord = w;
        gestureAlts = new ArrayList<>();
        for (String a : words) gestureAlts.add(shiftCase(a));
        revertOriginal = null;
        autoSpaced = false;
        if (keyboard.shift == KeyboardView.SHIFT_ON) keyboard.setShift(KeyboardView.SHIFT_OFF);
        updateSuggestions();
    }

    @Override
    public void onCursor(int dx, int dy) {
        InputConnection ic = ic();
        if (ic == null || dx == 0) return;
        int pos = selStart;
        if (pos < 0) {
            CharSequence bb = ic.getTextBeforeCursor(10000, 0);
            pos = bb == null ? 0 : bb.length();
        }
        if (dx < 0) {
            CharSequence bb = ic.getTextBeforeCursor(-dx, 0);
            pos -= bb == null ? 0 : bb.length();
        } else {
            CharSequence a = ic.getTextAfterCursor(dx, 0);
            pos += a == null ? 0 : a.length();
        }
        pos = Math.max(0, pos);
        ic.setSelection(pos, pos);
        selStart = selEnd = pos;
    }

    @Override
    public void onModeChanged() {
        if (keyboard.mode == Layouts.LETTERS) updateShift();
    }

    @Override
    public void onMicOption(int i) {
        String[] langs = {"fr-FR", "en-US", "tr-TR", "he-IL"};
        Prefs.get(this).edit().putString("dict_lang", langs[i]).apply();
        onMic();
    }

    @Override
    public void onNextKeyboard() {
        java.util.List<String> l = Prefs.keyboards(this);
        String cur = Prefs.activeKeyboard(this);
        String next = l.get((l.indexOf(cur) + 1) % l.size());
        Prefs.get(this).edit().putString(Prefs.ACTIVE_KB, next).apply();
        EditorInfo info = getCurrentInputEditorInfo();
        if (info != null) onStartInputView(info, true);
        keyboard.setMode(Layouts.LETTERS);
        updateShift();
    }

    // ---------- Translation ----------

    private boolean trMode;
    private String trResult = "", trFor = "";
    private int trGen;
    private final Runnable trTask = this::doTranslate;

    private String trSource() {
        String kb = Prefs.activeKeyboard(this);
        if (kb.equals("tr") || kb.equals("he")) return kb;
        return Prefs.str(this, Prefs.LANG).equals("en") ? "en" : "fr";
    }

    private String trLabel() {
        return trSource().toUpperCase() + " > " + Translate.name(Prefs.str(this, Prefs.TR_TARGET)).toUpperCase();
    }

    private String fieldText() {
        InputConnection ic = ic();
        if (ic == null) return "";
        CharSequence b = ic.getTextBeforeCursor(5000, 0), a = ic.getTextAfterCursor(5000, 0);
        return (b == null ? "" : b.toString()) + (a == null ? "" : a.toString());
    }

    private void replaceField(String text) {
        InputConnection ic = ic();
        if (ic == null) return;
        CharSequence b = ic.getTextBeforeCursor(5000, 0), a = ic.getTextAfterCursor(5000, 0);
        ic.beginBatchEdit();
        ic.deleteSurroundingText(b == null ? 0 : b.length(), a == null ? 0 : a.length());
        ic.commitText(text, 1);
        ic.endBatchEdit();
    }

    private void requestTranslate() {
        main.removeCallbacks(trTask);
        main.postDelayed(trTask, 350);
    }

    private void doTranslate() {
        if (!trMode) return;
        String text = fieldText().trim();
        if (text.isEmpty()) { trResult = ""; trFor = ""; keyboard.setTranslation(true, trLabel(), ""); return; }
        int gen = ++trGen;
        Translate.translate(trSource(), Prefs.str(this, Prefs.TR_TARGET), text, (res, err) -> main.post(() -> {
            if (gen != trGen || !trMode) return;
            if (res != null) { trResult = res; trFor = text; }
            keyboard.setTranslation(true, trLabel(), res != null ? res : (err != null ? err : ""));
        }));
        if (!Translate.isReady()) keyboard.setTranslation(true, trLabel(), "Préparation de la langue…");
    }

    @Override
    public void onTranslateToggle() {
        trMode = !trMode;
        if (trMode) {
            keyboard.setTranslation(true, trLabel(), "");
            requestTranslate();
        } else {
            keyboard.setTranslation(false, "", "");
            updateSuggestions();
        }
    }

    @Override
    public void onTranslatePill(boolean longPress) {
        if (longPress) { if (trMode) onTranslateToggle(); return; }
        java.util.List<String> l = new java.util.ArrayList<>();
        for (String c : Translate.CODES) if (!c.equals(trSource())) l.add(c);
        keyboard.openLangMenu(0, l.toArray(new String[0]), Prefs.str(this, Prefs.TR_TARGET));
    }

    @Override
    public void onTranslateTarget(String code) {
        Prefs.get(this).edit().putString(Prefs.TR_TARGET, code).apply();
        trResult = ""; trFor = "";
        keyboard.setTranslation(true, trLabel(), "");
        requestTranslate();
    }

    @Override
    public void onTranslateSendCode(String code) {
        String text = fieldText().trim();
        if (text.isEmpty()) return;
        keyboard.setSuggestions(new String[]{"", "Traduction…", ""}, -1);
        Translate.translate(trSource(), code, text, (res, err) -> main.post(() -> {
            if (res == null) { Toast.makeText(this, err != null ? err : "Traduction impossible", Toast.LENGTH_SHORT).show(); return; }
            replaceField(res);
            sendAction();
        }));
    }

    @Override
    public void onTranslateApply() {
        if (!trResult.isEmpty() && trFor.equals(fieldText().trim())) replaceField(trResult);
    }

    @Override
    public void onTranslateSend(int langIndex) {
        String[] codes = Prefs.str(this, Prefs.TR_SEND).split(",");
        java.util.List<String> l = new java.util.ArrayList<>();
        for (String c : codes) if (!c.trim().isEmpty()) l.add(c.trim());
        if (l.isEmpty()) java.util.Collections.addAll(l, Translate.CODES);
        keyboard.openLangMenu(1, l.toArray(new String[0]), "");
    }

    private void sendAction() {
        InputConnection ic = ic();
        if (ic == null) return;
        if (action != EditorInfo.IME_ACTION_NONE) ic.performEditorAction(action);
        else if (multiline) ic.commitText("\n", 1);
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
    }

    @Override
    public void onHide() {
        requestHideSelf(0);
    }

    private long lastEgg;

    private void checkEaster() {
        if (!easter) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastEgg < 2500) return;
        String b = Dictionary.norm(before(24).toLowerCase()).replaceAll("\\s+", " ").trim();
        String raw = before(24).replaceAll("\\s+", " ").trim();
        int type = b.endsWith("jeton fortnite") || b.endsWith("jetonfortnite") ? 1 : raw.endsWith("31 31 31") ? 2 : 0;
        if (type == 0) return;
        lastEgg = now;
        android.graphics.Bitmap bmp = null;
        try (java.io.InputStream in = getAssets().open(type == 1 ? "egg_token.png" : "egg_31.jpg")) {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            bmp = android.graphics.BitmapFactory.decodeStream(in, null, o);
        } catch (Exception ignored) { }
        keyboard.playEgg(type, bmp);
    }

    // ---------- Quick settings ----------

    @Override
    public void onQuickToggle(String key) {
        Prefs.get(this).edit().putBoolean(key, !Prefs.bool(this, key)).apply();
        EditorInfo info = getCurrentInputEditorInfo();
        if (info != null) onStartInputView(info, true);
        keyboard.invalidate();
    }

    @Override
    public void onOpenSettings() {
        Intent i = new Intent(this, SettingsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    // ---------- Emoji search ----------

    @Override
    public void onEmojiSearch() {
        searching = true;
        query.setLength(0);
        results = new ArrayList<>();
        swipeBeforeSearch = keyboard.swipeEnabled;
        keyboard.swipeEnabled = false;
        showKeyboard();
        keyboard.setMode(Layouts.LETTERS);
        keyboard.forceShift(KeyboardView.SHIFT_OFF);
        keyboard.setSearch(true, "", results);
    }

    private void refreshSearch() {
        results = EmojiData.search(query.toString(), 8);
        keyboard.forceShift(KeyboardView.SHIFT_OFF);
        keyboard.setSearch(true, query.toString(), results);
    }

    private void endSearch() {
        searching = false;
        keyboard.swipeEnabled = swipeBeforeSearch;
        keyboard.setSearch(false, "", null);
    }

    @Override
    public void onSearchResult(int i) {
        if (i >= results.size()) return;
        InputConnection ic = ic();
        if (ic != null) ic.commitText(results.get(i), 1);
        emojiView.addRecent(results.get(i));
    }

    @Override
    public void onSearchClose() {
        endSearch();
        onEmoji();
    }

    // ---------- EmojiView.Listener ----------

    @Override
    public void onEmojiText(String s) {
        InputConnection ic = ic();
        if (ic == null) return;
        ic.commitText(s, 1);
        gestureWord = null;
        revertOriginal = null;
    }

    @Override
    public void onEmojiDelete() {
        onDelete(false);
    }

    @Override
    public void onEmojiAbc() {
        showKeyboard();
        afterEdit();
    }

    // ---------- Voice ----------

    @Override
    public void onMic() {
        if (listening) {
            if (recognizer != null) recognizer.stopListening();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Intent i = new Intent(this, SettingsActivity.class);
            i.putExtra(SettingsActivity.EXTRA_ASK_MIC, true);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Aucun service de reconnaissance vocale disponible", Toast.LENGTH_SHORT).show();
            return;
        }
        showKeyboard();
        String b = before(1);
        voicePrefix = !b.isEmpty() && !Character.isWhitespace(b.charAt(0)) ? " " : "";
        final boolean capFirst = autocap && sentenceStart();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rms) { keyboard.setVoiceLevel(rms); }
            @Override public void onBufferReceived(byte[] b) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onError(int e) { finishVoice(null, capFirst); }
            @Override public void onResults(Bundle r) { finishVoice(first(r), capFirst); }
            @Override public void onPartialResults(Bundle r) {
                String t = first(r);
                InputConnection ic = ic();
                if (t != null && ic != null) ic.setComposingText(voicePrefix + (capFirst ? cap(t) : t), 1);
            }
            @Override public void onEvent(int t, Bundle p) { }
        });
        Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        String kbl = Prefs.activeKeyboard(this);
        String defLang = kbl.equals("tr") ? "tr-TR" : kbl.equals("he") ? "he-IL" : Prefs.str(this, Prefs.LANG).equals("en") ? "en-US" : "fr-FR";
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Prefs.get(this).getString("dict_lang", defLang));
        it.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        listening = true;
        keyboard.setVoice(true);
        recognizer.startListening(it);
    }

    private static String first(Bundle r) {
        ArrayList<String> l = r == null ? null : r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return l == null || l.isEmpty() ? null : l.get(0);
    }

    private void finishVoice(String text, boolean capFirst) {
        InputConnection ic = ic();
        if (ic != null) {
            if (text != null && !text.isEmpty()) ic.commitText(voicePrefix + (capFirst ? cap(text) : text), 1);
            else ic.finishComposingText();
        }
        listening = false;
        if (keyboard != null) keyboard.setVoice(false);
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        afterEdit();
    }

    private void stopVoice() {
        if (!listening) return;
        if (recognizer != null) recognizer.stopListening();
        InputConnection ic = ic();
        if (ic != null) ic.finishComposingText();
        listening = false;
        if (keyboard != null) keyboard.setVoice(false);
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
    }
}
