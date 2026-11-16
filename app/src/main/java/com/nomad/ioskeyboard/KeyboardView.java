package com.nomad.ioskeyboard;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyboardView extends View {
    interface Listener {
        void onText(String text);
        void onDelete(boolean word);
        void onSpace();
        void onReturn();
        void onEmoji();
        void onGlobe(boolean longPress);
        void onMic();
        void onSuggestion(int index);
        void onGesture(List<PointF> path);
        void onCursor(int dx, int dy);
        void onModeChanged();
        void onSearchResult(int index);
        void onSearchClose();
        void onQuickToggle(String prefKey);
        void onOpenSettings();
        void onNextKeyboard();
        void onMicOption(int index);
        void onHide();
        void onTranslateToggle();
        void onTranslatePill(boolean longPress);
        void onTranslateApply();
        void onTranslateSend(int langIndex);
        void onTranslateTarget(String code);
        void onTranslateSendCode(String code);
    }

    static final int SHIFT_OFF = 0, SHIFT_ON = 1, SHIFT_LOCK = 2;
    private static final int ST_NONE = 0, ST_PRESS = 1, ST_POPUP = 2, ST_GESTURE = 3, ST_TRACKPAD = 4,
            ST_DELETE = 5, ST_MODE_SLIDE = 6, ST_SUGGEST = 7, ST_BOTTOM = 8, ST_MICMENU = 9;
    private static final String[] MIC_LANGS = {"Français", "English", "Türkçe", "עברית"};
    private String[] MIC_ITEMS = MIC_LANGS;
    private int[] micActions = {0, 1, 2, 3};
    private int micSel = -1;
    private final RectF micRect = new RectF();

    private float d;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Feedback feedback;
    private Listener listener;
    Theme theme;

    private List<List<Key>> rows = new ArrayList<>();
    int mode = Layouts.LETTERS;
    private int variant = Layouts.VAR_TEXT;
    private String layoutName = "azerty";
    private String returnLabel = "retour";
    private boolean returnAction;
    private String langLabel = "";
    int shift = SHIFT_OFF;
    boolean showBottomBar = false;
    boolean capsLockEnabled = true, swipeEnabled = true, showSuggestionBar = true;

    private final String[] suggestions = {"", "", ""};
    private int highlight = -1;

    private float sugH, rowH, gapV, gapH, sidePad, bottomH, keysTop, unit, radius;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final android.text.TextPaint sugPaint = new android.text.TextPaint();
    private final RectF tmp = new RectF();

    private int pId = -1, pState = ST_NONE;
    private Key pKey, slideKey;
    private float pDownX, pDownY, pX, pY;
    private int modeBeforeSlide;
    private int shiftHeldId = -1;
    private boolean shiftUsed;
    private long lastShiftTap;
    private int pressedSug = -1, pressedBottom = 0;
    private Key previewKey;
    private String[] popupAlts;
    private int popupSel;
    private final RectF popupRect = new RectF();
    private boolean popupLeft;
    private final List<PointF> trail = new ArrayList<>();
    private float tpAccX, tpAccY;
    private int deleteCount;

    private boolean searchMode;
    private String searchQuery = "";
    private final List<String> searchResults = new ArrayList<>();
    private static final int SEARCH_SLOTS = 8, SEARCH_CLOSE = 99;
    private Typeface emojiFace;

    private static Typeface face;
    private float xRatio = 0.55f, capRatio = 0.73f;
    boolean iconsTop = true, quickMenu = true, swipeClose = false;
    String iconPos = "top";

    // ---------- Easter eggs ----------
    private int eggType;
    private long eggStart;
    private android.graphics.Bitmap eggBitmap;
    private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private float[][] eggParts;

    void playEgg(int type, android.graphics.Bitmap bmp) {
        eggType = type;
        eggBitmap = bmp;
        eggStart = SystemClock.uptimeMillis();
        java.util.Random rnd = new java.util.Random();
        eggParts = new float[40][];
        for (int i = 0; i < eggParts.length; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            float sp = (0.6f + rnd.nextFloat()) * unit * 9f;
            eggParts[i] = new float[]{(float) Math.cos(a) * sp, (float) Math.sin(a) * sp - unit * 4, 3 + rnd.nextFloat() * 6, rnd.nextFloat() * 360, rnd.nextInt(4)};
        }
        invalidate();
    }

    private void drawEgg(Canvas c) {
        float t = (SystemClock.uptimeMillis() - eggStart) / 1000f;
        float dur = eggType == 1 ? 1.5f : 1.9f;
        if (t > dur) { eggType = 0; eggBitmap = null; return; }
        float cx = getWidth() / 2f, cy = getHeight() * 0.48f;
        float size = Math.min(getWidth(), getHeight()) * 0.42f;
        if (eggType == 1) {
            if (t < 0.75f) {
                float k = t / 0.75f;
                float sc = k < 0.5f ? k * 2.3f : 1.15f - 0.15f * (k - 0.5f) * 2;
                float shake = k > 0.6f ? (float) Math.sin(t * 90) * unit * 0.12f * (k - 0.6f) * 3 : 0;
                c.save();
                c.translate(cx + shake, cy);
                c.rotate((1 - Math.min(1, k * 1.6f)) * 540);
                c.scale(sc, sc);
                fill.setColor(0x55FFD54A);
                c.drawCircle(0, 0, size * 0.62f * (0.8f + 0.2f * (float) Math.sin(t * 20)), fill);
                drawToken(c, size);
                c.restore();
            } else {
                float k = (t - 0.75f) / 0.75f;
                fill.setColor(android.graphics.Color.argb((int) (200 * Math.max(0, 1 - k * 2.5f)), 255, 240, 180));
                c.drawCircle(cx, cy, size * (0.5f + k * 1.6f), fill);
                stroke.setStrokeWidth(unit * 0.25f * (1 - k));
                stroke.setColor(android.graphics.Color.argb((int) (255 * (1 - k)), 255, 200, 60));
                c.drawCircle(cx, cy, size * (0.3f + k * 1.4f), stroke);
                int[] cols = {0xFFFFD54A, 0xFFFFA726, 0xFFFFF59D, 0xFFFFFFFF};
                for (float[] pt : eggParts) {
                    float x = cx + pt[0] * k, y = cy + pt[1] * k + unit * 14 * k * k;
                    fill.setColor((cols[(int) pt[4]] & 0x00FFFFFF) | ((int) (255 * (1 - k)) << 24));
                    c.save();
                    c.translate(x, y);
                    c.rotate(pt[3] + k * 720);
                    float sz = pt[2] * d * (1.2f - k * 0.6f);
                    c.drawRect(-sz, -sz * 0.5f, sz, sz * 0.5f, fill);
                    c.restore();
                }
            }
        } else {
            float k = Math.min(1, t / 0.45f);
            float bounce = (float) (1 - Math.pow(1 - k, 3) + Math.sin(k * Math.PI) * 0.18);
            float out = t > dur - 0.35f ? (t - (dur - 0.35f)) / 0.35f : 0;
            float y = cy + (1 - bounce) * getHeight() * 0.8f + out * getHeight();
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(size * 0.9f);
            text.setColor(android.graphics.Color.argb((int) (90 * (1 - out)), 255, 61, 139));
            c.drawText("31", cx, cy + size * 0.32f, text);
            c.save();
            c.translate(cx, y);
            c.rotate((float) Math.sin(t * 9) * 8);
            float r = size * 0.5f;
            if (eggBitmap != null) {
                android.graphics.Path clip = new android.graphics.Path();
                clip.addCircle(0, 0, r, android.graphics.Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                float bw = eggBitmap.getWidth(), bh = eggBitmap.getHeight(), s2 = 2 * r / Math.min(bw, bh);
                c.drawBitmap(eggBitmap, null, new RectF(-bw * s2 / 2, -bh * s2 / 2, bw * s2 / 2, bh * s2 / 2), bmpPaint);
                c.restore();
            } else {
                fill.setColor(0xFFFF3D8B);
                c.drawCircle(0, 0, r, fill);
                text.setColor(0xFFFFFFFF);
                text.setTextSize(r * 0.9f);
                c.drawText("31", 0, r * 0.32f, text);
            }
            stroke.setStrokeWidth(4 * d);
            stroke.setColor(0xFFFFFFFF);
            c.drawCircle(0, 0, r, stroke);
            c.restore();
            int[] cols = {0xFFFF3D8B, 0xFFFFD54A, 0xFF4ADE80, 0xFF0A84FF};
            for (float[] pt : eggParts) {
                float kk = Math.min(1, t / 1.4f);
                fill.setColor((cols[(int) pt[4]] & 0x00FFFFFF) | ((int) (255 * (1 - kk)) << 24));
                c.drawCircle(cx + pt[0] * kk, cy + pt[1] * kk + unit * 10 * kk * kk, pt[2] * d * 0.6f, fill);
            }
            text.setTypeface(face);
        }
        postInvalidateOnAnimation();
    }

    private void drawToken(Canvas c, float size) {
        if (eggBitmap != null) {
            float bw = eggBitmap.getWidth(), bh = eggBitmap.getHeight(), s2 = size / Math.max(bw, bh);
            c.drawBitmap(eggBitmap, null, new RectF(-bw * s2 / 2, -bh * s2 / 2, bw * s2 / 2, bh * s2 / 2), bmpPaint);
            return;
        }
        float r = size * 0.5f;
        Path star = new Path();
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16 - Math.PI / 2;
            float rr = i % 4 == 0 ? r : i % 2 == 0 ? r * 0.7f : r * 0.55f;
            float x = (float) Math.cos(a) * rr, y = (float) Math.sin(a) * rr;
            if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
        }
        star.close();
        fill.setShader(new android.graphics.LinearGradient(-r, -r, r, r, 0xFFFFF2B0, 0xFFC98A12, android.graphics.Shader.TileMode.CLAMP));
        c.drawPath(star, fill);
        c.drawCircle(0, 0, r * 0.52f, fill);
        fill.setShader(new android.graphics.RadialGradient(-r * 0.15f, -r * 0.15f, r * 0.45f, 0xFFFFF59D, 0xFFF5A623, android.graphics.Shader.TileMode.CLAMP));
        c.drawCircle(0, 0, r * 0.42f, fill);
        fill.setShader(null);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(r * 0.55f);
        text.setColor(0xFFFFFFFF);
        c.drawText("V", 0, r * 0.2f, text);
        text.setTypeface(face);
    }
    private String builtIconPos = "top";
    private final Key iconGlobe = new Key(Key.GLOBE, "", "", 1f), iconMic = new Key(Key.MIC, "", "", 1f),
            iconTr = new Key(Key.TRANSLATE, "", "", 1f), trPill = new Key(Key.TR_PILL, "", "", 1f), trTextKey = new Key(Key.TR_TEXT, "", "", 1f);
    boolean showTrIcon, trEmoji, trReturn;
    private boolean trActive;
    String[] sendLangs = new String[0];
    private float listAnim = 1f;
    // scrollable language overlay
    private boolean langOpen;
    private int langMode;
    private String[] langCodes = new String[0];
    private String langCurrent = "";
    private float langScroll, langDownY, langLastY;
    private boolean langDragging;
    private int langPressed = -1;

    void openLangMenu(int mode, String[] codes, String current) {
        langOpen = true; langMode = mode; langCodes = codes; langCurrent = current == null ? "" : current;
        langScroll = 0; langPressed = -1;
        reset();
        invalidate();
    }

    private float langItemH() { return rowH * 0.78f; }
    private float langTop() { return keysTop - gapV / 2 + 46 * d; }
    private float langMaxScroll() {
        return Math.max(0, langCodes.length * langItemH() - (keysBottom() - langTop() - 6 * d));
    }

    private void drawLangMenu(Canvas c) {
        Theme t = theme;
        float top = keysTop - gapV / 2;
        fill.setColor(t.bg);
        c.drawRect(0, top, getWidth(), keysBottom() + bottomH, fill);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(16 * d);
        text.setColor(t.text);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(langMode == 0 ? "Traduire vers" : "Envoyer en…", 16 * d, top + 28 * d, text);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(t.accent);
        c.drawText("Fermer", getWidth() - 16 * d, top + 28 * d, text);
        text.setTextAlign(Paint.Align.LEFT);
        c.save();
        c.clipRect(0, langTop(), getWidth(), keysBottom());
        float ih = langItemH();
        text.setTypeface(face);
        text.setTextSize(Math.min(17 * d, unit * 0.43f));
        for (int i = 0; i < langCodes.length; i++) {
            float y = langTop() + i * ih - langScroll;
            if (y + ih < langTop() || y > keysBottom()) continue;
            boolean cur = langCodes[i].equals(langCurrent);
            tmp.set(10 * d, y + 3 * d, getWidth() - 10 * d, y + ih - 3 * d);
            fill.setColor(i == langPressed ? t.preview : cur ? 0xFFFF3D8B : t.key);
            c.drawRoundRect(tmp, radius * 1.4f, radius * 1.4f, fill);
            text.setColor(0xFFFFFFFF);
            if (!cur && !t.dark && i != langPressed) text.setColor(t.text);
            c.drawText(Translate.name(langCodes[i]), 24 * d, tmp.centerY() - (text.descent() + text.ascent()) / 2, text);
        }
        c.restore();
        text.setTextAlign(Paint.Align.CENTER);
        if (langMaxScroll() > 0) {
            float trackH = keysBottom() - langTop();
            float h = trackH * trackH / (trackH + langMaxScroll());
            float y = langTop() + (trackH - h) * (langScroll / langMaxScroll());
            fill.setColor(t.subText);
            c.drawRoundRect(new RectF(getWidth() - 5 * d, y, getWidth() - 2 * d, y + h), 2 * d, 2 * d, fill);
        }
    }

    private void langTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                langDownY = langLastY = y; langDragging = false;
                langPressed = y >= langTop() && y <= keysBottom() ? (int) ((y - langTop() + langScroll) / langItemH()) : -1;
                if (langPressed >= langCodes.length) langPressed = -1;
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                if (!langDragging && Math.abs(y - langDownY) > 8 * d) { langDragging = true; langPressed = -1; }
                if (langDragging) {
                    langScroll = Math.max(0, Math.min(langMaxScroll(), langScroll - (y - langLastY)));
                    invalidate();
                }
                langLastY = y;
                break;
            case MotionEvent.ACTION_UP:
                if (!langDragging) {
                    if (y < langTop()) { langOpen = false; }
                    else if (langPressed >= 0) {
                        String code = langCodes[langPressed];
                        langOpen = false;
                        feedback.tick();
                        if (langMode == 0) listener.onTranslateTarget(code); else listener.onTranslateSendCode(code);
                    }
                }
                langPressed = -1;
                invalidate();
                break;
            case MotionEvent.ACTION_CANCEL:
                langPressed = -1;
                invalidate();
                break;
        }
    }
    private String trLabel = "", trText = "";

    void setTranslation(boolean active, String label, String text) {
        trActive = active; trLabel = label; trText = text == null ? "" : text;
        invalidate();
    }

    private boolean topGlobe() { return "top".equals(iconPos) && showGlobe; }
    private boolean topMic() { return "top".equals(iconPos) && showMic; }

    private boolean barIcons() { return (topGlobe() || topMic() || showTrIcon) && !searchMode && !trActive; }

    private List<Key> barIconList() {
        List<Key> l = new ArrayList<>();
        if (topGlobe()) l.add(iconGlobe);
        if (topMic()) l.add(iconMic);
        if (showTrIcon) l.add(iconTr);
        return l;
    }

    private void layoutBarIcons() {
        float third = getWidth() / 3f;
        List<Key> l = barIconList();
        float w = third / Math.max(1, l.size());
        for (int i = 0; i < l.size(); i++) l.get(i).rect.set(i * w + 4 * d, 4 * d, (i + 1) * w - 4 * d, sugH - 4 * d);
    }

    private void drawTranslateBar(Canvas c) {
        Theme t = theme;
        text.setTypeface(face);
        text.setTextSize(Math.min(14 * d, unit * 0.36f));
        float pw = text.measureText(trLabel) + 26 * d, ph = sugH * 0.62f;
        trPill.rect.set(8 * d, (sugH - ph) / 2, 8 * d + pw, (sugH + ph) / 2);
        fill.setColor(pKey == trPill && pState != ST_NONE ? 0xFFC72A6B : 0xFFFF3D8B);
        c.drawRoundRect(trPill.rect, ph / 2, ph / 2, fill);
        text.setColor(0xFFFFFFFF);
        c.drawText(trLabel, trPill.rect.centerX(), sugH * 0.5f - (text.descent() + text.ascent()) / 2, text);
        trTextKey.rect.set(trPill.rect.right + 8 * d, 4 * d, getWidth() - 6 * d, sugH - 4 * d);
        if (pKey == trTextKey && pState != ST_NONE) {
            fill.setColor(t.highlight);
            c.drawRoundRect(trTextKey.rect, 8 * d, 8 * d, fill);
        }
        text.setTextSize(Math.min(16 * d, unit * 0.4f));
        text.setColor(trText.isEmpty() ? t.subText : t.text);
        String s0 = trText.isEmpty() ? "Écrivez, la traduction s'affiche ici" : trText;
        sugPaint.set(text);
        CharSequence e = TextUtils.ellipsize(s0, sugPaint, trTextKey.rect.width() - 12 * d, TextUtils.TruncateAt.START);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(e.toString(), trTextKey.rect.right - 6 * d, sugH * 0.52f - (text.descent() + text.ascent()) / 2, text);
        text.setTextAlign(Paint.Align.CENTER);
    }

    boolean showMic, showGlobe, glass = true, anims = true, smartTouch = true, swipeDown = true;
    String oneHand = "off";
    java.util.Locale locale = java.util.Locale.FRENCH;
    private java.util.Map<Character, Float> letterProbs;
    private float modeFade = 1f, sugFade = 1f;
    private boolean builtGlobe;
    private String downAlt;

    void setLetterProbs(java.util.Map<Character, Float> p) { letterProbs = p; }

    private String up(String s) { return s.toUpperCase(locale); }

    private void animateFloat(java.util.function.Consumer<Float> set, long ms) {
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(ms);
        a.setInterpolator(new android.view.animation.DecelerateInterpolator());
        a.addUpdateListener(v -> { set.accept((float) v.getAnimatedValue()); invalidate(); });
        a.start();
    }
    private boolean menuOpen;
    private int menuPressed = -1;
    private static final String[][] MENU = {
            {"Dictée", Prefs.DICTATION}, {"Correction auto", Prefs.AUTOCORRECT},
            {"Prédictions", Prefs.PREDICT}, {"Balayer pour écrire", Prefs.SWIPE},
            {"Vibrations", Prefs.HAPTIC}, {"Son des touches", Prefs.SOUND}};

    private float popupAnim = 1f, previewAnim = 1f, selPos, popupOriginX;
    private android.animation.ValueAnimator popupAnimator, selAnimator, previewAnimator;

    private void animatePopupIn() {
        if (popupAnimator != null) popupAnimator.cancel();
        popupAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        popupAnimator.setDuration(90);
        popupAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        popupAnimator.addUpdateListener(a -> { popupAnim = (float) a.getAnimatedValue(); invalidate(); });
        popupAnimator.start();
    }

    private void animateSel(int target) {
        int pos = popupLeft ? popupAlts.length - 1 - target : target;
        if (selAnimator != null) selAnimator.cancel();
        selAnimator = android.animation.ValueAnimator.ofFloat(selPos, pos);
        selAnimator.setDuration(90);
        selAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        selAnimator.addUpdateListener(a -> { selPos = (float) a.getAnimatedValue(); invalidate(); });
        selAnimator.start();
    }

    private void animatePreviewIn() {
        if (previewAnimator != null) previewAnimator.cancel();
        previewAnimator = android.animation.ValueAnimator.ofFloat(0.82f, 1f);
        previewAnimator.setDuration(70);
        previewAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        previewAnimator.addUpdateListener(a -> { previewAnim = (float) a.getAnimatedValue(); invalidate(); });
        previewAnimator.start();
    }

    private boolean voiceActive;
    private float voiceLevel;

    public KeyboardView(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        feedback = new Feedback(c, this);
        text.setTextAlign(Paint.Align.CENTER);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setStrokeCap(Paint.Cap.ROUND);
        trailPaint.setStrokeJoin(Paint.Join.ROUND);
        trailPaint.setStrokeWidth(7 * d);
        theme = Theme.get(c);
        if (face == null) {
            try { face = Typeface.createFromAsset(c.getAssets(), "key_font.ttf"); } catch (Exception e) { face = Typeface.DEFAULT; }
        }
        android.graphics.Rect b = new android.graphics.Rect();
        text.setTypeface(face);
        text.setTextSize(100);
        text.getTextBounds("x", 0, 1, b);
        xRatio = b.height() / 100f;
        text.getTextBounds("H", 0, 1, b);
        capRatio = b.height() / 100f;
        rebuild();
    }

    void setListener(Listener l) { listener = l; }

    void applyTheme(Theme t) { theme = t; invalidate(); }

    void configure(String layout, int variant, String returnLabel, boolean returnAction, String langLabel) {
        boolean changed = !layout.equals(layoutName) || variant != this.variant || !returnLabel.equals(this.returnLabel) || builtMic != showMic || builtGlobe != showGlobe || !iconPos.equals(builtIconPos) || !oneHand.equals(builtOneHand);
        layoutName = layout; this.variant = variant; this.returnLabel = returnLabel;
        this.returnAction = returnAction; this.langLabel = langLabel;
        feedback.reload();
        if (changed) rebuild();
        invalidate();
    }

    void setMode(int m) {
        if (mode == m) return;
        mode = m;
        rebuild();
        if (anims) { modeFade = 0f; animateFloat(v -> modeFade = v, 160); }
        if (listener != null) listener.onModeChanged();
    }

    void setShift(int s) {
        if (shiftHeldId != -1) return;
        if (shift == SHIFT_LOCK && s != SHIFT_LOCK) return;
        if (shift != s) { shift = s; invalidate(); }
    }

    void forceShift(int s) { shift = s; invalidate(); }

    void setSuggestions(String[] s, int hl) {
        boolean changed = false;
        for (int i = 0; i < 3; i++) {
            String v = s != null && i < s.length && s[i] != null ? s[i] : "";
            if (!v.equals(suggestions[i])) changed = true;
            suggestions[i] = v;
        }
        if (changed && anims && sugFade >= 1f) { sugFade = 0.35f; animateFloat(v -> sugFade = 0.35f + 0.65f * v, 120); }
        highlight = hl;
        invalidate();
    }

    void setSearch(boolean on, String query, List<String> results) {
        boolean relayout = on != searchMode;
        searchMode = on;
        searchQuery = query;
        searchResults.clear();
        if (results != null) searchResults.addAll(results);
        if (on) emojiFace = EmojiData.typeface(getContext());
        if (relayout) { requestLayout(); layoutKeys(); }
        invalidate();
    }

    boolean inSearch() { return searchMode; }

    int normalHeight() {
        boolean was = searchMode;
        searchMode = false;
        computeDims();
        int h = (int) (keysTop + 4 * rowH + bottomH);
        searchMode = was;
        computeDims();
        return h;
    }

    private float searchRowH() { return sugH * 0.5f; }

    void setVoice(boolean active) { voiceActive = active; voiceLevel = 0; invalidate(); }

    void setVoiceLevel(float rms) { voiceLevel = Math.max(0, Math.min(1, (rms + 2) / 12f)); invalidate(); }

    float keyWidth() { return unit; }

    Map<Character, PointF> letterCenters() {
        Map<Character, PointF> m = new HashMap<>();
        if (mode != Layouts.LETTERS) return m;
        for (List<Key> r : rows) for (Key k : r)
            if (k.isLetter()) m.put(k.text.charAt(0), new PointF(k.rect.centerX(), k.rect.centerY()));
        return m;
    }

    private void rebuild() {
        builtMic = showMic;
        builtGlobe = showGlobe;
        builtOneHand = oneHand;
        builtIconPos = iconPos;
        rows = Layouts.build(mode, layoutName, variant, returnLabel, showMic && "bottom".equals(iconPos), showGlobe && "bottom".equals(iconPos));
        layoutKeys();
        invalidate();
    }

    private boolean landscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private float topRadius;
    private String builtOneHand = "off";
    private boolean builtMic;
    private float measureW;

    private void computeDims() {
        boolean land = landscape();
        float w = getWidth() > 0 ? getWidth() : measureW > 0 ? measureW : getResources().getDisplayMetrics().widthPixels;
        float p = (w - 6 * d) / 10f;
        sugH = searchMode ? (land ? 80 : 92) * d : (land ? 38 * d : 1.18f * p);
        rowH = land ? 42 * d : 1.39f * p;
        gapV = land ? 7 * d : 0.315f * p;
        gapH = land ? 6 * d : 0.16f * p;
        sidePad = 3 * d;
        bottomH = (showBottomBar && !land ? 50 : 4) * d;
        radius = land ? 6 * d : 0.2f * p;
        topRadius = land ? 14 * d : 0.55f * p;
        keysTop = sugH;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        measureW = MeasureSpec.getSize(wSpec);
        computeDims();
        int w = MeasureSpec.getSize(wSpec);
        int h = (int) (keysTop + 4 * rowH + bottomH);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        layoutKeys();
    }

    private void layoutKeys() {
        computeDims();
        float w = getWidth();
        if (w <= 0) return;
        unit = (w - 2 * sidePad) / 10f;
        float usable = w - 2 * sidePad, x0 = sidePad;
        if (!"off".equals(oneHand) && !landscape()) {
            usable = w * 0.84f;
            x0 = "left".equals(oneHand) ? sidePad : w - sidePad - usable;
        }
        float colUnit = usable / 10f;
        for (int i = 0; i < rows.size(); i++) {
            float top = keysTop + i * rowH;
            float x = x0;
            for (Key k : rows.get(i)) {
                float kw = k.weight * colUnit;
                k.slotL = x; k.slotR = x + kw;
                k.rect.set(x + gapH / 2, top + gapV / 2, x + kw - gapH / 2, top + rowH - gapV / 2);
                x += kw;
            }
        }
    }

    private float keysBottom() { return keysTop + 4 * rowH; }

    private Key findKey(float x, float y) {
        if (y < keysTop - gapV) return null;
        int r = (int) ((y - keysTop) / rowH);
        r = Math.max(0, Math.min(rows.size() - 1, r));
        Key best = null;
        float bd = Float.MAX_VALUE;
        for (Key k : rows.get(r)) {
            if (k.type == Key.SPACER) continue;
            float dist = x < k.slotL ? k.slotL - x : x > k.slotR ? x - k.slotR : 0;
            if (dist < bd) { bd = dist; best = k; }
        }
        if (smartTouch && letterProbs != null && best != null && best.isLetter() && mode == Layouts.LETTERS) {
            float bs = -Float.MAX_VALUE;
            Key pick = best;
            for (Key k : rows.get(r)) {
                if (!k.isLetter()) continue;
                float dx = Math.abs(x - k.rect.centerX()) / k.rect.width();
                if (dx > 0.85f) continue;
                Float pr = letterProbs.get(Character.toLowerCase(k.text.charAt(0)));
                float sc = -dx * dx * 5f + 0.55f * (float) Math.log((pr == null ? 0 : pr) + 0.03f);
                if (sc > bs) { bs = sc; pick = k; }
            }
            best = pick;
        }
        return best;
    }

    private boolean oneHandStrip(float x, float y) {
        if ("off".equals(oneHand) || y < keysTop || y > keysBottom()) return false;
        float w = getWidth(), usable = w * 0.84f;
        return "left".equals(oneHand) ? x > sidePad + usable : x < w - sidePad - usable;
    }

    // ---------- Drawing ----------

    @Override
    protected void onDraw(Canvas c) {
        Theme t = theme;
        c.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
        fill.setColor(glass ? (t.bg & 0x00FFFFFF) | 0xE8000000 : t.bg);
        tmp.set(0, 0, getWidth(), getHeight() + topRadius);
        c.drawRoundRect(tmp, topRadius, topRadius, fill);
        if (glass) {
            fill.setShader(new android.graphics.LinearGradient(0, 0, 0, sugH, t.dark ? 0x22FFFFFF : 0x55FFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
            c.drawRoundRect(tmp, topRadius, topRadius, fill);
            fill.setShader(null);
        }
        boolean tp = pState == ST_TRACKPAD;
        if (searchMode) drawSearch(c);
        else if (trActive && !tp) drawTranslateBar(c);
        else if (showSuggestionBar && !tp) drawSuggestions(c);
        int layer = modeFade < 1f ? c.saveLayerAlpha(0, keysTop, getWidth(), keysBottom(), (int) (255 * (0.3f + 0.7f * modeFade))) : -1;
        for (List<Key> r : rows) for (Key k : r) if (k.type != Key.SPACER) drawKey(c, k, tp);
        if (layer >= 0) c.restoreToCount(layer);
        if (!"off".equals(oneHand) && !landscape()) drawOneHandStrip(c);
        drawBottomBar(c);
        if (voiceActive) drawVoice(c);
        if (menuOpen) drawMenu(c);
        if (langOpen) drawLangMenu(c);
        if (pState == ST_GESTURE && trail.size() > 1) drawTrail(c);
        if (fadeKey != null && fadeKey != previewKey) drawPreview(c, fadeKey, fadeAlpha);
        if (previewKey != null && pState != ST_POPUP) drawPreview(c, previewKey, 1f);
        if (pState == ST_POPUP && popupAlts != null) drawPopup(c);
        if (pState == ST_MICMENU) drawMicMenu(c);
        if (eggType != 0) drawEgg(c);
    }

    private void drawOneHandStrip(Canvas c) {
        float w = getWidth(), usable = w * 0.84f;
        float l = "left".equals(oneHand) ? sidePad + usable : 0, rr = "left".equals(oneHand) ? w : w - sidePad - usable;
        float cx = (l + rr) / 2, cy = keysTop + rowH * 1.5f;
        stroke.setColor(theme.subText);
        stroke.setStrokeWidth(2f * d);
        float a = 7 * d, dir = "left".equals(oneHand) ? 1 : -1;
        c.drawLine(cx - a * 0.5f * dir, cy - a, cx + a * 0.5f * dir, cy, stroke);
        c.drawLine(cx + a * 0.5f * dir, cy, cx - a * 0.5f * dir, cy + a, stroke);
        tmp.set(cx - 6 * d, cy + rowH * 0.9f, cx + 6 * d, cy + rowH * 0.9f + 12 * d);
        c.drawRect(tmp.left, tmp.top, tmp.right, tmp.top + 1.5f * d, fill);
    }

    private void drawSuggestions(Canvas c) {
        Theme t = theme;
        float w = getWidth() / 3f;
        float cy = sugH / 2;
        boolean icons = barIcons();
        if (icons) {
            layoutBarIcons();
            for (Key k : barIconList()) {
                if (pKey == k && pState != ST_NONE) {
                    fill.setColor(t.special);
                    c.drawRoundRect(k.rect, 8 * d, 8 * d, fill);
                }
                if (k == iconGlobe) drawGlobe(c, k.rect.centerX(), sugH * 0.52f, unit * 0.27f, t.text);
                else if (k == iconMic) drawMic(c, k.rect.centerX(), sugH * 0.52f, unit * 0.3f, voiceActive ? t.accent : t.text);
                else {
                    text.setTypeface(Typeface.DEFAULT_BOLD);
                    text.setTextSize(unit * 0.36f);
                    text.setColor(t.text);
                    c.drawText("文A", k.rect.centerX(), sugH * 0.52f - (text.descent() + text.ascent()) / 2, text);
                    text.setTypeface(face);
                }
            }
        }
        for (int slot = icons ? 1 : 0; slot < 3; slot++) {
            int i = icons ? slot - 1 : slot;
            float l = slot * w;
            if (icons && slot == 1 && highlight != 0 && pressedSug != 0) {
                fill.setColor(t.divider);
                c.drawRect(l - 0.5f * d, sugH * 0.3f, l + 0.5f * d, sugH * 0.72f, fill);
            }
            if (i == highlight || i == pressedSug) {
                fill.setColor(i == pressedSug ? t.special : t.highlight);
                tmp.set(l + 4 * d, 5 * d, l + w - 4 * d, sugH - 5 * d);
                c.drawRoundRect(tmp, 8 * d, 8 * d, fill);
            }
            if (i > 0 && highlight != i && highlight != i - 1 && pressedSug != i && pressedSug != i - 1) {
                fill.setColor(t.divider);
                c.drawRect(l - 0.5f * d, sugH * 0.3f, l + 0.5f * d, sugH * 0.72f, fill);
            }
            String s = suggestions[i];
            if (s.isEmpty()) continue;
            text.setColor(t.text);
            text.setAlpha((int) (255 * sugFade));
            Typeface ef = EmojiData.typeface(getContext());
            text.setTypeface(ef != null && Character.isSurrogate(s.charAt(0)) ? ef : face);
            text.setTextSize(Math.min(16 * d, unit * 0.4f));
            sugPaint.set(text);
            CharSequence e = TextUtils.ellipsize(s, sugPaint, w - 14 * d, TextUtils.TruncateAt.END);
            c.drawText(e.toString(), l + w / 2, sugH * 0.52f - (text.descent() + text.ascent()) / 2, text);
            text.setAlpha(255);
        }
    }

    private void drawSearch(Canvas c) {
        Theme t = theme;
        float rh = searchRowH();
        fill.setColor(t.special);
        tmp.set(sidePad + 3 * d, 6 * d, getWidth() - sidePad - 3 * d, rh - 2 * d);
        c.drawRoundRect(tmp, 10 * d, 10 * d, fill);
        float cy = (6 * d + rh - 2 * d) / 2;
        stroke.setColor(t.subText);
        stroke.setStrokeWidth(1.8f * d);
        float mx = sidePad + 20 * d;
        c.drawCircle(mx, cy - 1.5f * d, 5.5f * d, stroke);
        c.drawLine(mx + 4 * d, cy + 2.5f * d, mx + 8 * d, cy + 6.5f * d, stroke);
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(16 * d);
        text.setTextAlign(Paint.Align.LEFT);
        boolean empty = searchQuery.isEmpty();
        text.setColor(empty ? t.subText : t.text);
        String q = empty ? "Rechercher un emoji" : searchQuery;
        float base = cy - (text.descent() + text.ascent()) / 2;
        c.drawText(q, mx + 16 * d, base, text);
        float cursorX = mx + 16 * d + (empty ? 0 : text.measureText(q) + 2 * d);
        fill.setColor(t.accent);
        c.drawRect(cursorX, cy - 10 * d, cursorX + 2 * d, cy + 10 * d, fill);
        text.setTextAlign(Paint.Align.CENTER);
        float xx = getWidth() - sidePad - 22 * d;
        fill.setColor(pressedSug == SEARCH_CLOSE ? t.key : t.subText);
        c.drawCircle(xx, cy, 9 * d, fill);
        stroke.setColor(t.special);
        stroke.setStrokeWidth(1.8f * d);
        c.drawLine(xx - 3.5f * d, cy - 3.5f * d, xx + 3.5f * d, cy + 3.5f * d, stroke);
        c.drawLine(xx - 3.5f * d, cy + 3.5f * d, xx + 3.5f * d, cy - 3.5f * d, stroke);
        float sw = getWidth() / (float) SEARCH_SLOTS;
        float ry = rh + (sugH - rh) / 2;
        text.setTypeface(emojiFace != null ? emojiFace : Typeface.DEFAULT);
        text.setTextSize(Math.min(sw, sugH - rh) * 0.62f);
        text.setColor(t.text);
        float off = -(text.descent() + text.ascent()) / 2;
        if (searchResults.isEmpty() && !empty) {
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(14 * d);
            text.setColor(t.subText);
            c.drawText("Aucun emoji", getWidth() / 2f, ry - (text.descent() + text.ascent()) / 2, text);
        }
        for (int i = 0; i < searchResults.size() && i < SEARCH_SLOTS; i++) {
            float cx = sw * (i + 0.5f);
            if (pressedSug == i) {
                fill.setColor(t.highlight);
                tmp.set(cx - sw / 2 + 3 * d, rh + 2 * d, cx + sw / 2 - 3 * d, sugH - 2 * d);
                c.drawRoundRect(tmp, 8 * d, 8 * d, fill);
            }
            c.drawText(searchResults.get(i), cx, ry + off, text);
        }
        text.setTypeface(Typeface.DEFAULT);
    }

    private int keyColor(Key k, boolean tp) {
        Theme t = theme;
        if (tp) return t.special;
        boolean pressed = (pKey == k && pState != ST_NONE && pState != ST_GESTURE) || slideKey == k;
        switch (k.type) {
            case Key.SHIFT: return shift != SHIFT_OFF ? t.shiftOn : (pressed ? t.preview : t.special);
            case Key.RETURN: return returnAction ? (pressed ? t.preview : t.accent) : (pressed ? t.preview : t.special);
            case Key.SPACE: return pressed ? t.preview : t.key;
            case Key.CHAR: return pressed && pState == ST_MODE_SLIDE ? t.special : t.key;
            default: return pressed ? t.preview : t.special;
        }
    }

    private void drawKey(Canvas c, Key k, boolean tp) {
        Theme t = theme;
        RectF r = k.rect;
        fill.setColor(t.shadow);
        tmp.set(r.left, r.top + d, r.right, r.bottom + d);
        c.drawRoundRect(tmp, radius, radius, fill);
        fill.setColor(keyColor(k, tp));
        c.drawRoundRect(r, radius, radius, fill);
        if (tp) return;
        int fg = t.text;
        if (k.type == Key.SHIFT && shift != SHIFT_OFF) fg = t.shiftOnText;
        if (k.type == Key.RETURN && returnAction) fg = t.accentText;
        float cx = r.centerX(), cy = r.centerY();
        float s = Math.min(r.height(), r.width()) * 0.5f;
        switch (k.type) {
            case Key.SHIFT: drawShiftIcon(c, cx, cy, unit * 0.56f, fg, shift != SHIFT_OFF, shift == SHIFT_LOCK); return;
            case Key.DELETE: drawBackspace(c, cx, cy, unit * 0.62f, fg); return;
            case Key.EMOJI: drawSmiley(c, cx, cy, unit * 0.27f, fg); return;
            case Key.MIC: drawMic(c, cx, cy, unit * 0.3f, fg); return;
            case Key.GLOBE: drawGlobe(c, cx, cy, unit * 0.27f, fg); return;
            case Key.RETURN: if (!returnAction && "retour".equals(k.label)) { drawReturn(c, cx, cy, unit * 0.5f, fg); return; } break;
            case Key.SPACE: if (!langLabel.isEmpty()) {
                    text.setTypeface(face);
                    text.setTextSize(unit * 0.26f);
                    text.setColor(t.subText);
                    text.setTextAlign(Paint.Align.RIGHT);
                    c.drawText(langLabel, r.right - unit * 0.22f, r.bottom - unit * 0.2f, text);
                    text.setTextAlign(Paint.Align.CENTER);
                }
                return;
            default: break;
        }
        String label = k.label;
        float size;
        boolean lower = false;
        if (k.type == Key.CHAR) {
            boolean up = mode == Layouts.LETTERS && shift != SHIFT_OFF && k.isLetter();
            if (up) label = up(label);
            lower = k.isLetter() && !up;
            size = label.length() > 1 ? unit * 0.42f : (k.isLetter() ? unit * 0.6f : unit * 0.56f);
        } else {
            size = unit * 0.42f;
        }
        text.setTypeface(face);
        text.setTextSize(size);
        text.setColor(fg);
        float baseline;
        if (lower) baseline = cy + xRatio * size / 2;
        else if (k.type == Key.CHAR && label.length() == 1 && !Character.isLetterOrDigit(label.charAt(0))) baseline = cy - (text.descent() + text.ascent()) / 2;
        else baseline = cy + capRatio * size / 2;
        c.drawText(label, cx, baseline, text);
        if (k.type == Key.SPACE && !langLabel.isEmpty()) {
            text.setTextSize(10 * d);
            text.setColor(t.subText);
            text.setTextAlign(Paint.Align.RIGHT);
            c.drawText(langLabel, r.right - 8 * d, r.bottom - 6 * d, text);
            text.setTextAlign(Paint.Align.CENTER);
        }
    }

    private void drawBottomBar(Canvas c) {
        if (bottomH < 20 * d) return;
        float cy = keysBottom() + bottomH * 0.45f;
        float gx = sidePad + unit * 0.65f;
        float mx = getWidth() - sidePad - unit * 0.65f;
        int col = theme.text;
        if (pressedBottom == 1) { fill.setColor(theme.special); c.drawCircle(gx, cy, 20 * d, fill); }
        if (pressedBottom == 2) { fill.setColor(theme.special); c.drawCircle(mx, cy, 20 * d, fill); }
        drawGlobe(c, gx, cy, 13 * d, col);
        drawMic(c, mx, cy, 13 * d, voiceActive ? theme.accent : col);
    }

    private void drawTrail(Canvas c) {
        trailPaint.setColor(theme.trail);
        path.reset();
        PointF p0 = trail.get(0);
        path.moveTo(p0.x, p0.y);
        for (int i = 1; i < trail.size(); i++) {
            PointF a = trail.get(i - 1), b = trail.get(i);
            path.quadTo(a.x, a.y, (a.x + b.x) / 2, (a.y + b.y) / 2);
        }
        PointF last = trail.get(trail.size() - 1);
        path.lineTo(last.x, last.y);
        c.drawPath(path, trailPaint);
    }

    private void bubblePath(RectF key, RectF bubble) {
        float rr = radius;
        float br = Math.min(bubble.width() * 0.24f, rr * 1.6f);
        float neck = key.height() * 0.3f;
        float wb = bubble.bottom;
        path.reset();
        path.moveTo(key.left, key.bottom - rr);
        path.lineTo(key.left, wb + neck);
        path.cubicTo(key.left, wb + neck * 0.45f, bubble.left, wb + neck * 0.55f, bubble.left, wb);
        path.lineTo(bubble.left, bubble.top + br);
        path.quadTo(bubble.left, bubble.top, bubble.left + br, bubble.top);
        path.lineTo(bubble.right - br, bubble.top);
        path.quadTo(bubble.right, bubble.top, bubble.right, bubble.top + br);
        path.lineTo(bubble.right, wb);
        path.cubicTo(bubble.right, wb + neck * 0.55f, key.right, wb + neck * 0.45f, key.right, wb + neck);
        path.lineTo(key.right, key.bottom - rr);
        path.quadTo(key.right, key.bottom, key.right - rr, key.bottom);
        path.lineTo(key.left + rr, key.bottom);
        path.quadTo(key.left, key.bottom, key.left, key.bottom - rr);
        path.close();
    }

    private void drawBubble(Canvas c, RectF key, RectF bubble) {
        bubblePath(key, bubble);
        if (!theme.dark) {
            c.save();
            c.translate(0, d);
            fill.setColor(0x33000000);
            c.drawPath(path, fill);
            c.restore();
        }
        fill.setColor(theme.preview);
        fill.setAlpha(bubbleAlpha);
        c.drawPath(path, fill);
        fill.setAlpha(255);
    }

    private Key fadeKey;
    private float fadeAlpha;
    private android.animation.ValueAnimator fadeAnimator;

    private void startPreviewFade(Key k) {
        if (fadeAnimator != null) fadeAnimator.cancel();
        fadeKey = k;
        fadeAnimator = android.animation.ValueAnimator.ofFloat(1f, 0f);
        fadeAnimator.setDuration(110);
        fadeAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        fadeAnimator.addUpdateListener(a -> {
            fadeAlpha = (float) a.getAnimatedValue();
            if (fadeAlpha <= 0.01f) fadeKey = null;
            invalidate();
        });
        fadeAnimator.start();
    }

    private int bubbleAlpha = 255;

    private void drawPreview(Canvas c, Key k, float alpha) {
        bubbleAlpha = (int) (255 * alpha);
        RectF r = k.rect;
        float bw = r.width() * 1.42f, bh = r.height() * 1.45f;
        float l = r.centerX() - bw / 2;
        l = Math.max(2 * d, Math.min(getWidth() - 2 * d - bw, l));
        float bottom = r.top + r.height() * 0.04f;
        float top = Math.max(0, bottom - bh);
        tmp.set(l, top, l + bw, bottom);
        RectF bubble = new RectF(tmp);
        c.save();
        drawBubble(c, r, bubble);
        String label = k.label;
        if (mode == Layouts.LETTERS && shift != SHIFT_OFF && k.isLetter()) label = up(label);
        if (k == pKey && downAlt != null) label = downAlt;
        text.setTypeface(face);
        float ps = label.length() > 1 ? unit * 0.45f : unit * 0.74f;
        text.setTextSize(ps);
        text.setColor(theme.text);
        text.setAlpha(bubbleAlpha);
        float pcy = bubble.top + bubble.height() * 0.46f;
        boolean low = k.isLetter() && label.equals(label.toLowerCase());
        c.drawText(label, bubble.centerX(), low ? pcy + xRatio * ps / 2 : pcy + capRatio * ps / 2, text);
        text.setAlpha(255);
        bubbleAlpha = 255;
        c.restore();
    }

    private void drawPopup(Canvas c) {
        RectF r = pKey.rect;
        int n = popupAlts.length;
        float cw = popupCellW();
        float pad = popupPad();
        float pw = n * cw + 2 * pad, ph = r.height() * 0.95f + 2 * pad;
        float l = popupLeft ? r.right + pad - pw : r.left - pad;
        l = Math.max(2 * d, Math.min(getWidth() - 2 * d - pw, l));
        float bottom = r.top + r.height() * 0.04f;
        float top = Math.max(0, bottom - ph - r.height() * 0.06f);
        popupRect.set(l, top, l + pw, bottom);
        int alpha = (int) (255 * Math.max(0f, Math.min(1f, popupAnim)));
        bubbleAlpha = alpha;
        drawBubble(c, r, popupRect);
        bubbleAlpha = 255;
        RectF first = popupCell(popupLeft ? n - 1 : 0, cw, pad);
        float hl = first.left + selPos * cw;
        fill.setColor(theme.accent);
        fill.setAlpha(alpha);
        tmp.set(hl + 1.5f * d, first.top + 1.5f * d, hl + cw - 1.5f * d, first.bottom - 1.5f * d);
        c.drawRoundRect(tmp, radius, radius, fill);
        fill.setAlpha(255);
        text.setTypeface(face);
        for (int i = 0; i < n; i++) {
            RectF cell = popupCell(i, cw, pad);
            String s = popupAlts[i];
            boolean upper = mode == Layouts.LETTERS && shift != SHIFT_OFF;
            if (upper) s = up(s);
            float ts = s.length() > 1 ? unit * 0.42f : unit * 0.6f;
            text.setTextSize(ts);
            boolean selected = Math.abs((popupLeft ? n - 1 - i : i) - selPos) < 0.5f;
            text.setColor(selected ? theme.accentText : theme.text);
            text.setAlpha(alpha);
            boolean letter = s.length() == 1 && Character.isLetter(s.charAt(0));
            float base = letter ? cell.centerY() + (upper ? capRatio : xRatio) * ts / 2 : cell.centerY() - (text.descent() + text.ascent()) / 2;
            c.drawText(s, cell.centerX(), base, text);
        }
        text.setAlpha(255);
    }

    private void drawMicMenu(Canvas c) {
        float rr = 20 * d;
        float k = Math.max(0f, Math.min(1f, listAnim));
        boolean below = pKey != null && micRect.top > pKey.rect.top;
        float ax = pKey != null ? Math.max(micRect.left, Math.min(micRect.right, pKey.rect.centerX())) : micRect.centerX();
        float ay = below ? micRect.top : micRect.bottom;
        c.save();
        float sc = 0.86f + 0.14f * k;
        c.scale(sc, sc, ax, ay);
        int alpha = (int) (255 * k);
        // soft shadow
        fill.setColor(android.graphics.Color.argb((int) (90 * k), 0, 0, 0));
        tmp.set(micRect.left, micRect.top + 4 * d, micRect.right, micRect.bottom + 6 * d);
        c.drawRoundRect(tmp, rr, rr, fill);
        // translucent glass body
        int body = theme.dark ? 0xE03A3A3E : 0xE6F4F4F7;
        fill.setColor(body);
        fill.setAlpha((int) (((body >>> 24) & 0xFF) * k));
        c.drawRoundRect(micRect, rr, rr, fill);
        // top sheen
        fill.setShader(new android.graphics.LinearGradient(0, micRect.top, 0, micRect.top + micRect.height() * 0.55f,
                android.graphics.Color.argb((int) ((theme.dark ? 46 : 120) * k), 255, 255, 255), 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
        c.drawRoundRect(micRect, rr, rr, fill);
        fill.setShader(null);
        // hairline rim
        stroke.setStrokeWidth(Math.max(1f, 0.8f * d));
        stroke.setShader(new android.graphics.LinearGradient(0, micRect.top, 0, micRect.bottom,
                android.graphics.Color.argb((int) (110 * k), 255, 255, 255), android.graphics.Color.argb((int) (25 * k), 255, 255, 255), android.graphics.Shader.TileMode.CLAMP));
        tmp.set(micRect.left + 0.5f * d, micRect.top + 0.5f * d, micRect.right - 0.5f * d, micRect.bottom - 0.5f * d);
        c.drawRoundRect(tmp, rr, rr, stroke);
        stroke.setShader(null);
        float ih = (micRect.height() - 12 * d) / MIC_ITEMS.length;
        text.setTypeface(face);
        text.setTextSize(unit * 0.42f);
        text.setTextAlign(Paint.Align.LEFT);
        int fg = theme.dark ? 0xFFFFFFFF : 0xFF000000;
        for (int i = 0; i < MIC_ITEMS.length; i++) {
            float t = micRect.top + 6 * d + i * ih;
            if (i == micSel) {
                fill.setColor(theme.dark ? 0x33FFFFFF : 0x1F000000);
                fill.setAlpha((int) ((theme.dark ? 0x33 : 0x1F) * k));
                tmp.set(micRect.left + 6 * d, t, micRect.right - 6 * d, t + ih);
                c.drawRoundRect(tmp, ih / 2, ih / 2, fill);
            } else if (i > 0 && i - 1 != micSel) {
                fill.setColor(theme.dark ? 0x22FFFFFF : 0x1A000000);
                c.drawRect(micRect.left + 18 * d, t - 0.35f * d, micRect.right - 18 * d, t + 0.35f * d, fill);
            }
            text.setColor(fg);
            text.setAlpha(alpha);
            c.drawText(MIC_ITEMS[i], micRect.left + 18 * d, t + ih / 2 - (text.descent() + text.ascent()) / 2, text);
        }
        text.setAlpha(255);
        text.setTextAlign(Paint.Align.CENTER);
        c.restore();
        if (k < 1f) postInvalidateOnAnimation();
    }

    private float popupCellW() {
        return pKey.rect.width() * (popupAlts[0].length() > 1 ? 1.5f : 1f);
    }

    private float popupPad() { return pKey.rect.width() * 0.06f; }

    private RectF popupCell(int i, float cw, float pad) {
        int pos = popupLeft ? popupAlts.length - 1 - i : i;
        float l = popupRect.left + pad + pos * cw;
        return new RectF(l, popupRect.top + pad, l + cw, popupRect.bottom - pad);
    }

    private RectF menuItemRect(int i) {
        float top = keysTop - gapV / 2 + 44 * d;
        float h = (keysBottom() - top - 8 * d) / 4f;
        float w = (getWidth() - 3 * 8 * d) / 2f;
        if (i == MENU.length) return new RectF(8 * d, top + 3 * h + 4 * d, getWidth() - 8 * d, top + 4 * h - 4 * d);
        int col = i % 2, row = i / 2;
        float l = 8 * d + col * (w + 8 * d);
        return new RectF(l, top + row * h + 4 * d, l + w, top + (row + 1) * h - 4 * d);
    }

    private RectF menuOkRect() {
        float top = keysTop - gapV / 2;
        return new RectF(getWidth() - 90 * d, top + 4 * d, getWidth() - 8 * d, top + 40 * d);
    }

    private void drawMenu(Canvas c) {
        Theme t = theme;
        float top = keysTop - gapV / 2;
        fill.setColor(t.bg);
        c.drawRect(0, top, getWidth(), keysBottom(), fill);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(17 * d);
        text.setColor(t.text);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText("Réglages rapides", 14 * d, top + 28 * d, text);
        text.setTextAlign(Paint.Align.CENTER);
        RectF ok = menuOkRect();
        fill.setColor(menuPressed == 99 ? t.special : t.accent);
        c.drawRoundRect(ok, ok.height() / 2, ok.height() / 2, fill);
        text.setColor(t.accentText);
        text.setTextSize(15 * d);
        c.drawText("OK", ok.centerX(), ok.centerY() - (text.descent() + text.ascent()) / 2, text);
        text.setTypeface(face);
        for (int i = 0; i <= MENU.length; i++) {
            RectF r = menuItemRect(i);
            fill.setColor(menuPressed == i ? t.special : t.key);
            c.drawRoundRect(r, radius * 1.6f, radius * 1.6f, fill);
            text.setColor(t.text);
            text.setTextSize(15 * d);
            if (i == MENU.length) {
                c.drawText("Tous les réglages…", r.centerX(), r.centerY() - (text.descent() + text.ascent()) / 2, text);
                continue;
            }
            text.setTextAlign(Paint.Align.LEFT);
            String label = MENU[i][0];
            CharSequence e = TextUtils.ellipsize(label, new android.text.TextPaint(text), r.width() - 70 * d, TextUtils.TruncateAt.END);
            c.drawText(e.toString(), r.left + 12 * d, r.centerY() - (text.descent() + text.ascent()) / 2, text);
            text.setTextAlign(Paint.Align.CENTER);
            boolean on = Prefs.bool(getContext(), MENU[i][1]);
            float sw = 44 * d, sh = 26 * d;
            tmp.set(r.right - 12 * d - sw, r.centerY() - sh / 2, r.right - 12 * d, r.centerY() + sh / 2);
            fill.setColor(on ? 0xFF34C759 : (t.dark ? 0xFF39393D : 0xFFE9E9EA));
            c.drawRoundRect(tmp, sh / 2, sh / 2, fill);
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(on ? tmp.right - sh / 2 : tmp.left + sh / 2, tmp.centerY(), sh / 2 - 2 * d, fill);
        }
        text.setTypeface(Typeface.DEFAULT);
    }

    private int menuHit(float x, float y) {
        if (menuOkRect().contains(x, y)) return 99;
        for (int i = 0; i <= MENU.length; i++) if (menuItemRect(i).contains(x, y)) return i;
        return -1;
    }

    void closeMenu() { menuOpen = false; langOpen = false; invalidate(); }

    private void drawVoice(Canvas c) {
        Theme t = theme;
        fill.setColor(t.bg);
        c.drawRect(0, 0, getWidth(), keysBottom(), fill);
        float cx = getWidth() / 2f, cy = keysBottom() * 0.45f;
        int bars = 24;
        float bw = 4 * d, gap = 4 * d;
        float total = bars * bw + (bars - 1) * gap;
        long now = SystemClock.uptimeMillis();
        fill.setColor(t.accent);
        for (int i = 0; i < bars; i++) {
            double ph = Math.sin(now / 120.0 + i * 0.7) * 0.5 + 0.5;
            float h = (6 + (float) (ph * voiceLevel * 60) + voiceLevel * 10) * d;
            float x = cx - total / 2 + i * (bw + gap);
            tmp.set(x, cy - h / 2, x + bw, cy + h / 2);
            c.drawRoundRect(tmp, bw / 2, bw / 2, fill);
        }
        text.setTypeface(Typeface.DEFAULT);
        text.setColor(t.text);
        text.setTextSize(18 * d);
        c.drawText("Parlez maintenant…", cx, cy + 70 * d, text);
        text.setColor(t.subText);
        text.setTextSize(13 * d);
        c.drawText("Touchez pour terminer", cx, cy + 94 * d, text);
        postInvalidateOnAnimation();
    }

    // ---------- Icons ----------

    private void drawShiftIcon(Canvas c, float cx, float cy, float s, int col, boolean filled, boolean lock) {
        float h = s / 2;
        path.reset();
        float oy = lock ? -2 * d : 0;
        path.moveTo(cx, cy - h + oy);
        path.lineTo(cx + h, cy + oy);
        path.lineTo(cx + h * 0.45f, cy + oy);
        path.lineTo(cx + h * 0.45f, cy + h * 0.75f + oy);
        path.lineTo(cx - h * 0.45f, cy + h * 0.75f + oy);
        path.lineTo(cx - h * 0.45f, cy + oy);
        path.lineTo(cx - h, cy + oy);
        path.close();
        if (filled) {
            fill.setColor(col);
            c.drawPath(path, fill);
        } else {
            stroke.setColor(col);
            stroke.setStrokeWidth(1.6f * d);
            c.drawPath(path, stroke);
        }
        if (lock) {
            fill.setColor(col);
            c.drawRect(cx - h * 0.45f, cy + h * 0.95f + oy, cx + h * 0.45f, cy + h * 0.95f + oy + 1.8f * d, fill);
        }
    }

    private void drawReturn(Canvas c, float cx, float cy, float s, int col) {
        stroke.setColor(col);
        stroke.setStrokeWidth(1.7f * d);
        float w = s / 2, h = s * 0.32f;
        path.reset();
        path.moveTo(cx + w, cy - h);
        path.lineTo(cx + w, cy + h * 0.25f);
        path.quadTo(cx + w, cy + h, cx + w - h * 0.75f, cy + h);
        path.lineTo(cx - w, cy + h);
        c.drawPath(path, stroke);
        path.reset();
        path.moveTo(cx - w + h * 0.9f, cy + h * 0.1f);
        path.lineTo(cx - w, cy + h);
        path.lineTo(cx - w + h * 0.9f, cy + h * 1.9f);
        c.drawPath(path, stroke);
    }

    private void drawBackspace(Canvas c, float cx, float cy, float s, int col) {
        float w = s / 2, h = s * 0.36f;
        path.reset();
        path.moveTo(cx - w, cy);
        path.lineTo(cx - w * 0.45f, cy - h);
        path.lineTo(cx + w, cy - h);
        path.lineTo(cx + w, cy + h);
        path.lineTo(cx - w * 0.45f, cy + h);
        path.close();
        stroke.setColor(col);
        stroke.setStrokeWidth(1.6f * d);
        c.drawPath(path, stroke);
        float xc = cx + w * 0.22f, x = h * 0.42f;
        c.drawLine(xc - x, cy - x, xc + x, cy + x, stroke);
        c.drawLine(xc - x, cy + x, xc + x, cy - x, stroke);
    }

    private void drawSmiley(Canvas c, float cx, float cy, float r, int col) {
        stroke.setColor(col);
        stroke.setStrokeWidth(1.6f * d);
        c.drawCircle(cx, cy, r, stroke);
        fill.setColor(col);
        c.drawCircle(cx - r * 0.35f, cy - r * 0.25f, r * 0.12f, fill);
        c.drawCircle(cx + r * 0.35f, cy - r * 0.25f, r * 0.12f, fill);
        tmp.set(cx - r * 0.5f, cy - r * 0.35f, cx + r * 0.5f, cy + r * 0.55f);
        c.drawArc(tmp, 20, 140, false, stroke);
    }

    private void drawGlobe(Canvas c, float cx, float cy, float r, int col) {
        stroke.setColor(col);
        stroke.setStrokeWidth(1.6f * d);
        c.drawCircle(cx, cy, r, stroke);
        tmp.set(cx - r * 0.45f, cy - r, cx + r * 0.45f, cy + r);
        c.drawOval(tmp, stroke);
        c.drawLine(cx, cy - r, cx, cy + r, stroke);
        c.drawLine(cx - r, cy, cx + r, cy, stroke);
        float hw = (float) Math.sqrt(1 - 0.36) * r;
        c.drawLine(cx - hw, cy - r * 0.6f, cx + hw, cy - r * 0.6f, stroke);
        c.drawLine(cx - hw, cy + r * 0.6f, cx + hw, cy + r * 0.6f, stroke);
    }

    private void drawMic(Canvas c, float cx, float cy, float r, int col) {
        stroke.setColor(col);
        stroke.setStrokeWidth(1.6f * d);
        tmp.set(cx - r * 0.38f, cy - r, cx + r * 0.38f, cy + r * 0.25f);
        c.drawRoundRect(tmp, r * 0.38f, r * 0.38f, stroke);
        tmp.set(cx - r * 0.7f, cy - r * 0.6f, cx + r * 0.7f, cy + r * 0.6f);
        c.drawArc(tmp, 0, 180, false, stroke);
        c.drawLine(cx, cy + r * 0.6f, cx, cy + r, stroke);
        c.drawLine(cx - r * 0.4f, cy + r, cx + r * 0.4f, cy + r, stroke);
    }

    // ---------- Touch ----------

    private final Runnable longPress = () -> {
        if (pState != ST_PRESS || pKey == null) return;
        if (pKey.type == Key.SPACE) {
            startTrackpad();
        } else if (pKey.alts != null) {
            popupAlts = pKey.isLetter() && pKey.alts.length > 1 ? java.util.Arrays.copyOfRange(pKey.alts, 1, pKey.alts.length) : pKey.alts;
            popupSel = pKey.isLetter() ? 0 : (popupAlts.length > 1 ? 1 : 0);
            popupLeft = pKey.rect.centerX() > getWidth() / 2f;
            selPos = popupLeft ? popupAlts.length - 1 - popupSel : popupSel;
            popupOriginX = pX;
            animatePopupIn();
            pState = ST_POPUP;
            previewKey = null;
            feedback.tick();
            invalidate();
        }
    };

    private final Runnable deleteRepeat = new Runnable() {
        @Override public void run() {
            if (pState != ST_DELETE) return;
            deleteCount++;
            boolean word = deleteCount > 26;
            listener.onDelete(word);
            if (deleteCount % (word ? 1 : 2) == 0) feedback.key(Key.DELETE);
            handler.postDelayed(this, word ? 130 : deleteCount < 6 ? 70 : 42);
        }
    };

    private final Runnable globeLong = () -> {
        if (pState == ST_BOTTOM && pressedBottom == 1) {
            pressedBottom = 0;
            pState = ST_NONE;
            pId = -1;
            invalidate();
            listener.onGlobe(true);
        }
    };

    private void startTrackpad() {
        pState = ST_TRACKPAD;
        tpAccX = tpAccY = 0;
        previewKey = null;
        feedback.tick();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (listener == null) return true;
        if (langOpen) { langTouch(e); return true; }
        int a = e.getActionMasked();
        int i = e.getActionIndex();
        switch (a) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                down(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int j = 0; j < e.getPointerCount(); j++) {
                    if (e.getPointerId(j) == pId) {
                        for (int h = 0; h < e.getHistorySize(); h++) move(e.getHistoricalX(j, h), e.getHistoricalY(j, h));
                        move(e.getX(j), e.getY(j));
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                up(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_CANCEL:
                reset();
                shiftHeldId = -1;
                break;
        }
        return true;
    }

    private void down(int id, float x, float y) {
        if (voiceActive) { listener.onMic(); return; }
        if (menuOpen) {
            if (y < keysTop - gapV / 2) { menuOpen = false; invalidate(); return; }
            menuPressed = menuHit(x, y);
            feedback.key(Key.MODE_NUM);
            invalidate();
            return;
        }
        if (pId != -1) {
            if (pState == ST_PRESS && pKey != null && pKey.type == Key.CHAR) {
                fire(pKey);
                reset();
            } else if (pState == ST_PRESS && pKey != null && pKey.type == Key.SPACE) {
                reset();
                listener.onSpace();
            } else if (pState == ST_PRESS && pKey != null && pKey.type == Key.SHIFT) {
                shiftHeldId = pId;
                shiftUsed = false;
                reset();
            } else {
                return;
            }
        }
        pId = id;
        pDownX = pX = x; pDownY = pY = y;
        if (y < keysTop - gapV / 2 && searchMode) {
            pState = ST_SUGGEST;
            if (y < searchRowH()) pressedSug = x > getWidth() - sidePad - 48 * d ? SEARCH_CLOSE : -1;
            else {
                int i = (int) (x / (getWidth() / (float) SEARCH_SLOTS));
                pressedSug = i < searchResults.size() ? i : -1;
            }
            invalidate();
            return;
        }
        if (y < keysTop - gapV / 2) {
            if (!showSuggestionBar) { pId = -1; return; }
            if (trActive && !searchMode) {
                pKey = x <= trPill.rect.right + 4 * d ? trPill : trTextKey;
                pState = ST_PRESS;
                feedback.key(Key.MODE_NUM);
                if (pKey == trPill) handler.postDelayed(trPillLong, 450);
                invalidate();
                return;
            }
            if (barIcons()) {
                layoutBarIcons();
                int ti = Math.min(2, (int) (x / (getWidth() / 3f)));
                if (ti == 0) {
                    List<Key> l = barIconList();
                    Key ik = l.get(Math.max(0, Math.min(l.size() - 1, (int) (x / ((getWidth() / 3f) / l.size())))));
                    pKey = ik;
                    pState = ST_PRESS;
                    feedback.key(Key.MODE_NUM);
                    if (ik == iconMic) handler.postDelayed(micMenuOpen, 420);
                    else if (ik == iconGlobe) handler.postDelayed(globeKeyLong, 450);
                    invalidate();
                    return;
                }
                pState = ST_SUGGEST;
                pressedSug = ti - 1;
                if (suggestions[pressedSug].isEmpty()) pressedSug = -1;
                invalidate();
                return;
            }
            pState = ST_SUGGEST;
            pressedSug = Math.min(2, (int) (x / (getWidth() / 3f)));
            if (suggestions[pressedSug].isEmpty()) pressedSug = -1;
            invalidate();
            return;
        }
        if (y > keysBottom()) {
            if (!showBottomBar) { pId = -1; return; }
            pState = ST_BOTTOM;
            pressedBottom = x < getWidth() / 2f ? 1 : 2;
            feedback.key(Key.MODE_NUM);
            if (pressedBottom == 1) handler.postDelayed(globeLong, 500);
            invalidate();
            return;
        }
        if (oneHandStrip(x, y)) {
            pId = -1;
            oneHand = "off";
            Prefs.get(getContext()).edit().putString(Prefs.ONE_HAND, "off").apply();
            rebuild();
            return;
        }
        Key k = findKey(x, y);
        if (k == null) { pId = -1; return; }
        pKey = k;
        downAlt = null;
        feedback.key(k.type);
        switch (k.type) {
            case Key.CHAR:
                pState = ST_PRESS;
                previewKey = k.label.length() <= 2 ? k : null;
                if (k.alts != null) handler.postDelayed(longPress, 380);
                break;
            case Key.SHIFT:
                pState = ST_PRESS;
                long now = SystemClock.uptimeMillis();
                if (shift == SHIFT_LOCK) shift = SHIFT_OFF;
                else if (capsLockEnabled && now - lastShiftTap < 350) shift = SHIFT_LOCK;
                else shift = shift == SHIFT_OFF ? SHIFT_ON : SHIFT_OFF;
                lastShiftTap = now;
                break;
            case Key.DELETE:
                pState = ST_DELETE;
                deleteCount = 0;
                listener.onDelete(false);
                handler.postDelayed(deleteRepeat, 360);
                break;
            case Key.SPACE:
                pState = ST_PRESS;
                handler.postDelayed(longPress, 450);
                break;
            case Key.MODE_NUM:
            case Key.MODE_SYM:
            case Key.MODE_ABC:
                modeBeforeSlide = mode;
                pState = ST_MODE_SLIDE;
                slideKey = null;
                setMode(k.type == Key.MODE_NUM ? Layouts.NUM : k.type == Key.MODE_SYM ? Layouts.SYM : Layouts.LETTERS);
                break;
            case Key.EMOJI:
                pState = ST_PRESS;
                handler.postDelayed(openMenu, 450);
                break;
            case Key.GLOBE:
                pState = ST_PRESS;
                handler.postDelayed(globeKeyLong, 450);
                break;
            case Key.MIC:
                pState = ST_PRESS;
                handler.postDelayed(micMenuOpen, 420);
                break;
            case Key.RETURN:
                pState = ST_PRESS;
                if (trReturn) handler.postDelayed(returnMenu, 450);
                break;
            default:
                pState = ST_PRESS;
        }
        invalidate();
    }

    private final Runnable micMenuOpen = () -> {
        if (pState != ST_PRESS || pKey == null || pKey.type != Key.MIC) return;
        MIC_ITEMS = MIC_LANGS;
        micActions = new int[]{0, 1, 2, 3};
        openListMenu();
    };

    private void openListMenu() {
        pState = ST_MICMENU;
        micSel = -1;
        text.setTypeface(face);
        text.setTextSize(unit * 0.42f);
        float maxW = 0;
        for (String it : MIC_ITEMS) maxW = Math.max(maxW, text.measureText(it));
        float w = Math.max(unit * 3.4f, maxW + 40 * d), ih = rowH * 0.7f, h = ih * MIC_ITEMS.length + 12 * d;
        float l = pKey.rect.centerX() > getWidth() / 2f ? pKey.rect.right - w : pKey.rect.left;
        l = Math.max(4 * d, Math.min(getWidth() - 4 * d - w, l));
        float t = pKey.rect.top < keysTop ? pKey.rect.bottom + 4 * d : Math.max(2 * d, pKey.rect.top - 6 * d - h);
        micRect.set(l, t, l + w, t + h);
        feedback.tick();
        listAnim = 0f;
        animateFloat(v -> listAnim = v, 160);
        invalidate();
    }

    private final Runnable trPillLong = () -> {
        if (pState != ST_PRESS || pKey != trPill) return;
        reset();
        listener.onTranslatePill(true);
    };

    private final Runnable returnMenu = () -> {
        if (pState != ST_PRESS || pKey == null || pKey.type != Key.RETURN) return;
        if (sendLangs.length == 0) return;
        int n = Math.min(sendLangs.length, 7);
        MIC_ITEMS = new String[n];
        micActions = new int[n];
        for (int i = 0; i < n; i++) { MIC_ITEMS[i] = Translate.name(sendLangs[i]); micActions[i] = 30 + i; }
        openListMenu();
    };


    private final Runnable globeKeyLong = () -> {
        if (pState != ST_PRESS || pKey == null || pKey.type != Key.GLOBE) return;
        reset();
        listener.onGlobe(true);
    };

    private String downCharFor(Key k) {
        if (!k.isLetter() || mode != Layouts.LETTERS) return null;
        List<Key> row = rows.get(k.row);
        int col = 0;
        for (Key o : row) { if (o == k) break; if (o.type == Key.CHAR) col++; }
        String src = k.row == 0 ? "1234567890" : k.row == 1 ? "-/:;()€&@\"" : ".,?!'\"-";
        return col < src.length() ? String.valueOf(src.charAt(col)) : null;
    }

    private final Runnable openMenu = () -> {
        if (pState != ST_PRESS || pKey == null || pKey.type != Key.EMOJI) return;
        boolean emojiIcons = "emoji".equals(iconPos) && (showMic || showGlobe);
        if (emojiIcons || trEmoji) {
            List<String> items = new ArrayList<>();
            List<Integer> acts = new ArrayList<>();
            if (emojiIcons && showGlobe) { items.add("Langue suivante"); acts.add(10); }
            if (emojiIcons && showMic) { items.add("Dictée"); acts.add(11); }
            if (trEmoji) { items.add(trActive ? "Arrêter la traduction" : "Traduire"); acts.add(13); }
            if (quickMenu) { items.add("Réglages rapides"); acts.add(12); }
            MIC_ITEMS = items.toArray(new String[0]);
            micActions = new int[acts.size()];
            for (int i = 0; i < acts.size(); i++) micActions[i] = acts.get(i);
            openListMenu();
            return;
        }
        if (!quickMenu) return;
        reset();
        menuOpen = true;
        menuPressed = -2;
        feedback.tick();
        invalidate();
    };

    private void move(float x, float y) {
        pX = x; pY = y;
        switch (pState) {
            case ST_PRESS:
                if (pKey.type == Key.SPACE) {
                    if (swipeClose && y - pDownY > rowH * 0.6f && Math.abs(x - pDownX) < unit * 0.6f) {
                        reset();
                        listener.onHide();
                        return;
                    }
                    if (Math.abs(x - pDownX) > unit * 0.5f) {
                        handler.removeCallbacks(longPress);
                        startTrackpad();
                        tpAccX = x - pDownX;
                    }
                } else if (pKey.type == Key.CHAR) {
                    if (swipeDown && downAlt == null && y - pDownY > rowH * 0.45f && Math.abs(x - pDownX) < unit * 0.4f) {
                        String alt = downCharFor(pKey);
                        if (alt != null) {
                            downAlt = alt;
                            handler.removeCallbacks(longPress);
                            feedback.tick();
                            invalidate();
                            break;
                        }
                    }
                    if (downAlt != null) break;
                    Key k = findKey(x, y);
                    float dist = (float) Math.hypot(x - pDownX, y - pDownY);
                    if (swipeEnabled && mode == Layouts.LETTERS && pKey.isLetter() && dist > unit * 0.6f && k != pKey && k != null && k.isLetter()) {
                        handler.removeCallbacks(longPress);
                        pState = ST_GESTURE;
                        previewKey = null;
                        trail.clear();
                        trail.add(new PointF(pDownX, pDownY));
                        trail.add(new PointF(x, y));
                        invalidate();
                    } else if (k != null && k != pKey && k.type == Key.CHAR && dist > unit * 0.5f) {
                        handler.removeCallbacks(longPress);
                        pKey = k;
                        previewKey = k.label.length() <= 2 ? k : null;
                        if (k.alts != null) handler.postDelayed(longPress, 380);
                        invalidate();
                    }
                }
                break;
            case ST_GESTURE: {
                PointF last = trail.get(trail.size() - 1);
                if (Math.hypot(x - last.x, y - last.y) > 3 * d) {
                    trail.add(new PointF(x, y));
                    invalidate();
                }
                break;
            }
            case ST_POPUP: {
                float cw = popupCellW();
                float pad = popupPad();
                int pos = (int) Math.floor((x - popupRect.left - pad) / cw);
                pos = Math.max(0, Math.min(popupAlts.length - 1, pos));
                int sel = popupLeft ? popupAlts.length - 1 - pos : pos;
                if (Math.abs(x - popupOriginX) < cw * 0.45f) break;
                popupOriginX = -10000;
                if (sel != popupSel) { popupSel = sel; animateSel(sel); feedback.tick(); }
                break;
            }
            case ST_MICMENU: {
                int sel = -1;
                if (micRect.contains(x, y)) {
                    float ih = (micRect.height() - 8 * d) / MIC_ITEMS.length;
                    sel = Math.max(0, Math.min(MIC_ITEMS.length - 1, (int) ((y - micRect.top - 4 * d) / ih)));
                }
                if (sel != micSel) { micSel = sel; if (sel >= 0) feedback.tick(); invalidate(); }
                break;
            }
            case ST_TRACKPAD: {
                float stepX = unit * 0.38f, stepY = rowH * 0.9f;
                tpAccX += x - pDownX; tpAccY += y - pDownY;
                pDownX = x; pDownY = y;
                int dx = (int) (tpAccX / stepX), dy = (int) (tpAccY / stepY);
                if (dx != 0 || dy != 0) {
                    tpAccX -= dx * stepX; tpAccY -= dy * stepY;
                    listener.onCursor(dx, dy);
                }
                break;
            }
            case ST_MODE_SLIDE: {
                Key k = findKey(x, y);
                Key ns = (k != null && k.type == Key.CHAR && Math.hypot(x - pDownX, y - pDownY) > unit * 0.4f) ? k : null;
                if (ns != slideKey) { slideKey = ns; previewKey = ns; invalidate(); }
                break;
            }
            case ST_SUGGEST: {
                if (searchMode) break;
                if (swipeClose && y - pDownY > rowH * 0.6f) { reset(); listener.onHide(); return; }
                int s = y < keysTop + rowH * 0.5f ? Math.min(2, (int) (x / (getWidth() / 3f))) : -1;
                if (barIcons() && s >= 0) s = s == 0 ? -1 : s - 1;
                if (s != pressedSug) { pressedSug = s >= 0 && !suggestions[s].isEmpty() ? s : -1; invalidate(); }
                break;
            }
            default: break;
        }
    }

    private void up(int id, float x, float y) {
        if (menuOpen && menuPressed != -2) {
            int m = menuPressed;
            menuPressed = -1;
            if (m == 99) menuOpen = false;
            else if (m == MENU.length) { menuOpen = false; listener.onOpenSettings(); }
            else if (m >= 0) listener.onQuickToggle(MENU[m][1]);
            invalidate();
            return;
        }
        if (id == shiftHeldId) {
            shiftHeldId = -1;
            if (shiftUsed && shift == SHIFT_ON) { shift = SHIFT_OFF; invalidate(); }
            return;
        }
        if (id != pId) return;
        handler.removeCallbacks(longPress);
        handler.removeCallbacks(deleteRepeat);
        handler.removeCallbacks(globeLong);
        int st = pState;
        Key k = pKey;
        switch (st) {
            case ST_PRESS:
                if (k.type == Key.CHAR && downAlt != null) { lastTapX = k.rect.centerX(); lastTapY = k.rect.centerY(); commitText(downAlt); downAlt = null; }
                else if (k.type == Key.CHAR) fire(k);
                else if (k.type == Key.GLOBE) listener.onNextKeyboard();
                else if (k.type == Key.TRANSLATE) listener.onTranslateToggle();
                else if (k.type == Key.TR_PILL) listener.onTranslatePill(false);
                else if (k.type == Key.TR_TEXT) listener.onTranslateApply();
                else if (k.type == Key.SPACE) listener.onSpace();
                else if (k.type == Key.RETURN) listener.onReturn();
                else if (k.type == Key.EMOJI) listener.onEmoji();
                else if (k.type == Key.MIC) listener.onMic();
                break;
            case ST_POPUP:
                if (popupSel >= 0) {
                    String s = popupAlts[popupSel];
                    if (mode == Layouts.LETTERS && shift != SHIFT_OFF) s = up(s);
                    lastTapX = pKey.rect.centerX(); lastTapY = pKey.rect.centerY();
                    commitText(s);
                }
                break;
            case ST_MICMENU: {
                int m = micSel >= 0 && micSel < micActions.length ? micActions[micSel] : -1;
                reset();
                if (m >= 0 && m < 10) listener.onMicOption(m);
                else if (m == 10) listener.onNextKeyboard();
                else if (m == 11) listener.onMic();
                else if (m == 12) { menuOpen = true; menuPressed = -1; invalidate(); }
                else if (m == 13) listener.onTranslateToggle();
                else if (m >= 30) listener.onTranslateSendCode(sendLangs[m - 30]);
                else if (m >= 20) listener.onTranslateSend(m - 20);
                return;
            }
            case ST_GESTURE: {
                List<PointF> pts = new ArrayList<>(trail);
                reset();
                listener.onGesture(pts);
                return;
            }
            case ST_MODE_SLIDE:
                if (slideKey != null) {
                    Key sk = slideKey;
                    reset();
                    fire(sk);
                    setMode(modeBeforeSlide);
                    return;
                }
                break;
            case ST_SUGGEST:
                if (searchMode) {
                    int s = pressedSug;
                    reset();
                    if (s == SEARCH_CLOSE) listener.onSearchClose();
                    else if (s >= 0) listener.onSearchResult(s);
                    return;
                }
                if (pressedSug >= 0) { int s = pressedSug; reset(); listener.onSuggestion(s); return; }
                break;
            case ST_BOTTOM:
                if (pressedBottom == 1) listener.onGlobe(false);
                else if (pressedBottom == 2) listener.onMic();
                break;
            default: break;
        }
        reset();
    }

    private void reset() {
        handler.removeCallbacks(openMenu);
        handler.removeCallbacks(globeKeyLong);
        handler.removeCallbacks(micMenuOpen);
        handler.removeCallbacks(trPillLong);
        handler.removeCallbacks(returnMenu);
        handler.removeCallbacks(longPress);
        handler.removeCallbacks(deleteRepeat);
        handler.removeCallbacks(globeLong);
        if (previewKey != null) startPreviewFade(previewKey);
        pId = -1; pState = ST_NONE; pKey = null; slideKey = null; previewKey = null;
        popupAlts = null; pressedSug = -1; pressedBottom = 0; trail.clear();
        invalidate();
    }

    float lastTapX, lastTapY;

    private void fire(Key k) {
        if (pKey == k && pId != -1) { lastTapX = pX; lastTapY = pY; }
        else { lastTapX = k.rect.centerX(); lastTapY = k.rect.centerY(); }
        String s = k.text;
        if (mode == Layouts.LETTERS && shift != SHIFT_OFF && k.isLetter()) s = up(s);
        commitText(s);
        if (mode != Layouts.LETTERS && "'".equals(k.text)) setMode(Layouts.LETTERS);
    }

    private void commitText(String s) {
        if (shiftHeldId != -1) shiftUsed = true;
        listener.onText(s);
    }
}
