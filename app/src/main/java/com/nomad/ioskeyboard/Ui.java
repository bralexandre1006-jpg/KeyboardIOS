package com.nomad.ioskeyboard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/** Hand-drawn widgets for the app screen (dark "dashboard" style). */
final class Ui {
    static final int BG = 0xFF0B0B0D, CARD_TOP = 0xFF1D1D21, CARD_BOTTOM = 0xFF131316, BORDER = 0xFF27272C,
            TILE = 0xFF1F1F23, TILE_ON = 0xFF2E1624, TEXT = 0xFFFFFFFF, SUB = 0xFF8C8C95,
            GOLD_A = 0xFFFF9ACB, GOLD_B = 0xFFFF3D8B, GOLD = 0xFFFF5FA2, GREEN = 0xFF4ADE80, GREEN_BG = 0xFF14301F,
            ORANGE = 0xFFFB923C, ORANGE_BG = 0xFF3A2414, RED = 0xFFFF5A5F, KEY = 0xFF2C2C31, KEY_DARK = 0xFF1A1A1E;

    private static Typeface regular, semibold, heavy;

    private static Typeface load(Context c, String f) {
        try { return Typeface.createFromAsset(c.getAssets(), f); } catch (Exception e) { return Typeface.DEFAULT; }
    }

    static Typeface regular(Context c) { if (regular == null) regular = load(c, "outfit_500.ttf"); return regular; }
    static Typeface semibold(Context c) { if (semibold == null) semibold = load(c, "outfit_700.ttf"); return semibold; }
    static Typeface heavy(Context c) { if (heavy == null) heavy = load(c, "outfit_800.ttf"); return heavy; }

    static int blend(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        return Color.argb(255,
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    static void pressBounce(View v, MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
        else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)
            v.animate().scaleX(1).scaleY(1).setDuration(260).setInterpolator(new OvershootInterpolator(3f)).start();
    }

    /** Gold gradient call-to-action with glow. */
    static final class GoldButton extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private String label;

        GoldButton(Context c, String label) {
            super(c);
            this.label = label;
            d = getResources().getDisplayMetrics().density;
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(heavy(c));
            setOnTouchListener((v, e) -> { pressBounce(v, e); return false; });
        }

        void setLabel(String s) { label = s; invalidate(); }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), (int) (64 * d));
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), m = 10 * d;
            RectF r = new RectF(m * 0.4f, m * 0.6f, w - m * 0.4f, h - m * 1.2f);
            p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, GOLD_A, GOLD_B, Shader.TileMode.CLAMP));
            p.setShadowLayer(16 * d, 0, 4 * d, 0x88FF3D8B);
            c.drawRoundRect(r, 20 * d, 20 * d, p);
            p.setShader(null);
            p.clearShadowLayer();
            p.setColor(0xFFFFFFFF);
            p.setTextSize(16 * d);
            p.setLetterSpacing(0.04f);
            c.drawText(label, r.centerX(), r.centerY() - (p.descent() + p.ascent()) / 2, p);
        }
    }

    /** Pill selector with sliding highlight (like Jour / Nuit). */
    static final class Pills extends View {
        interface OnSelect { void selected(int i); }
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String[] labels;
        private final float d;
        private int sel;
        private float pos;
        private OnSelect listener;

        Pills(Context c, String[] labels, int sel) {
            super(c);
            this.labels = labels; this.sel = sel; pos = sel;
            d = getResources().getDisplayMetrics().density;
            p.setTextAlign(Paint.Align.CENTER);
        }

        void setListener(OnSelect l) { listener = l; }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), (int) (50 * d));
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                int i = (int) Math.max(0, Math.min(labels.length - 1, e.getX() / (getWidth() / (float) labels.length)));
                if (i != sel) {
                    sel = i;
                    ValueAnimator a = ValueAnimator.ofFloat(pos, i);
                    a.setDuration(320);
                    a.setInterpolator(new OvershootInterpolator(1.1f));
                    a.addUpdateListener(v -> { pos = (float) v.getAnimatedValue(); invalidate(); });
                    a.start();
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                    if (listener != null) listener.selected(i);
                }
            }
            return true;
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), in = 6 * d;
            RectF outer = new RectF(0.5f * d, 0.5f * d, w - 0.5f * d, h - 0.5f * d);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF141417);
            c.drawRoundRect(outer, h / 2, h / 2, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(d);
            p.setColor(BORDER);
            c.drawRoundRect(outer, h / 2, h / 2, p);
            float sw = (w - 2 * in) / labels.length;
            RectF r = new RectF(in + pos * sw, in, in + (pos + 1) * sw, h - in);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF26262B);
            c.drawRoundRect(r, r.height() / 2, r.height() / 2, p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(0xFF34343A);
            c.drawRoundRect(r, r.height() / 2, r.height() / 2, p);
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < labels.length; i++) {
                float t = 1 - Math.min(1, Math.abs(pos - i));
                p.setTypeface(semibold(getContext()));
                p.setTextSize(15 * d);
                p.setColor(blend(SUB, TEXT, t));
                c.drawText(labels[i], in + sw * (i + 0.5f), h / 2 - (p.descent() + p.ascent()) / 2, p);
            }
        }
    }

    /** Toggle tile (label + big state). */
    static final class Tile extends View {
        interface OnChange { void changed(boolean on); }
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String label;
        private final float d;
        private boolean on;
        private float t;
        private OnChange listener;

        Tile(Context c, String label, boolean on) {
            super(c);
            this.label = label; this.on = on; t = on ? 1 : 0;
            d = getResources().getDisplayMetrics().density;
            setOnTouchListener((v, e) -> { pressBounce(v, e); return false; });
            setOnClickListener(v -> toggle());
        }

        void setListener(OnChange l) { listener = l; }

        private void toggle() {
            on = !on;
            ValueAnimator a = ValueAnimator.ofFloat(t, on ? 1 : 0);
            a.setDuration(260);
            a.setInterpolator(new DecelerateInterpolator());
            a.addUpdateListener(v -> { t = (float) v.getAnimatedValue(); invalidate(); });
            a.start();
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            if (listener != null) listener.changed(on);
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), (int) (92 * d));
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            RectF r = new RectF(0, 0, w, h);
            p.setStyle(Paint.Style.FILL);
            p.setColor(blend(TILE, TILE_ON, t));
            c.drawRoundRect(r, 22 * d, 22 * d, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(d);
            p.setColor(blend(0xFF26262A, 0xFF5E2240, t));
            c.drawRoundRect(new RectF(0.5f * d, 0.5f * d, w - 0.5f * d, h - 0.5f * d), 22 * d, 22 * d, p);
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(regular(getContext()));
            p.setTextSize(15 * d);
            p.setColor(blend(SUB, 0xFFF8C6DD, t));
            c.drawText(label, 16 * d, 32 * d, p);
            p.setTypeface(heavy(getContext()));
            p.setTextSize(23 * d);
            p.setColor(blend(0xFF5C5C64, TEXT, t));
            c.drawText(on ? "Activé" : "Désactivé", 16 * d, h - 22 * d, p);
            float cx = w - 22 * d, cy = h - 30 * d;
            p.setColor(blend(0xFF3A3A40, GOLD, t));
            c.drawCircle(cx, cy, 6 * d, p);
            if (t > 0) {
                p.setColor(Color.argb((int) (70 * t), 0xFF, 0x5F, 0xA2));
                c.drawCircle(cx, cy, 6 * d + 6 * d * t, p);
            }
        }
    }

    /** Small rounded "chip" with optional colored dot. */
    static final class Chip extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private String text;
        private int fg, bg, dot;

        Chip(Context c, String text, int fg, int bg, int dot) {
            super(c);
            d = getResources().getDisplayMetrics().density;
            p.setTypeface(heavy(c));
            p.setLetterSpacing(0.08f);
            set(text, fg, bg, dot);
        }

        void set(String text, int fg, int bg, int dot) {
            this.text = text; this.fg = fg; this.bg = bg; this.dot = dot;
            requestLayout();
            invalidate();
        }

        @Override protected void onMeasure(int w, int h) {
            p.setTextSize(15 * d);
            float tw = p.measureText(text) + (dot != 0 ? 22 * d : 0) + 32 * d;
            setMeasuredDimension((int) tw, (int) (40 * d));
        }

        @Override protected void onDraw(Canvas c) {
            float h = getHeight();
            p.setColor(bg);
            c.drawRoundRect(new RectF(0, 0, getWidth(), h), h / 2, h / 2, p);
            float x = 16 * d;
            if (dot != 0) {
                p.setColor(dot);
                c.drawCircle(x + 5 * d, h / 2, 5 * d, p);
                x += 22 * d;
            }
            p.setColor(fg);
            p.setTextSize(15 * d);
            c.drawText(text, x, h / 2 - (p.descent() + p.ascent()) / 2, p);
        }
    }

    /** App logo: gold keyboard tile. */
    static final class Logo extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Logo(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            float ox = (getWidth() - s) / 2, oy = (getHeight() - s) / 2;
            p.setShader(new LinearGradient(ox, oy, ox + s, oy + s, GOLD_A, GOLD_B, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(ox, oy, ox + s, oy + s), s * 0.28f, s * 0.28f, p);
            p.setShader(null);
            p.setColor(0xFFFFFFFF);
            float pad = s * 0.17f, gap = s * 0.05f;
            float kw = (s - 2 * pad - 3 * gap) / 4, kh = s * 0.13f;
            float top = oy + s * 0.25f;
            int[] counts = {4, 3};
            for (int r = 0; r < 2; r++) {
                float rowW = counts[r] * kw + (counts[r] - 1) * gap;
                float x = ox + (s - rowW) / 2;
                for (int i = 0; i < counts[r]; i++) {
                    c.drawRoundRect(new RectF(x, top, x + kw, top + kh), kh * 0.3f, kh * 0.3f, p);
                    x += kw + gap;
                }
                top += kh + gap;
            }
            float sw = s * 0.5f;
            c.drawRoundRect(new RectF(ox + (s - sw) / 2, top, ox + (s + sw) / 2, top + kh), kh * 0.3f, kh * 0.3f, p);
        }
    }

    /** A little keyboard that types phrases by itself. */
    static final class TypingKeyboard extends View {
        interface OnDone { void done(); }
        private static final String[] ROWS = {"azertyuiop", "qsdfghjklm", "wxcvbn"};
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private final String[] phrases;
        private final boolean loop;
        private final boolean showText;
        private int phrase, typed;
        private boolean deleting;
        private char pressed;
        private long pressedAt, nextAt, blinkBase;
        private OnDone onDone;
        private boolean running, finished;
        private float textSize;

        TypingKeyboard(Context c, String[] phrases, boolean loop, boolean showText, float textSizeDp) {
            super(c);
            this.phrases = phrases; this.loop = loop; this.showText = showText;
            d = getResources().getDisplayMetrics().density;
            textSize = textSizeDp * d;
            p.setTextAlign(Paint.Align.CENTER);
        }

        void setOnDone(OnDone o) { onDone = o; }

        void start(long delay) {
            running = true;
            nextAt = SystemClock.uptimeMillis() + delay;
            blinkBase = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        }

        String current() { return phrases[phrase].substring(0, typed); }

        private void step(long now) {
            String ph = phrases[phrase];
            if (!deleting) {
                if (typed < ph.length()) {
                    char ch = Character.toLowerCase(ph.charAt(typed));
                    pressed = Dictionary.norm(String.valueOf(ch)).isEmpty() ? ch : Dictionary.norm(String.valueOf(ch)).charAt(0);
                    pressedAt = now;
                    typed++;
                    nextAt = now + 85 + (long) (Math.random() * 70);
                } else if (loop) {
                    deleting = true;
                    nextAt = now + 1500;
                } else if (!finished) {
                    finished = true;
                    nextAt = Long.MAX_VALUE;
                    if (onDone != null) postDelayed(() -> onDone.done(), 650);
                }
            } else {
                if (typed > 0) {
                    typed--;
                    pressed = '\b';
                    pressedAt = now;
                    nextAt = now + 35;
                } else {
                    deleting = false;
                    phrase = (phrase + 1) % phrases.length;
                    nextAt = now + 400;
                }
            }
        }

        @Override protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            if (running && now >= nextAt) step(now);
            float w = getWidth(), h = getHeight();
            float kbTop = showText ? h * 0.36f : 0;
            if (showText) {
                p.setTypeface(heavy(getContext()));
                p.setTextSize(textSize);
                p.setColor(TEXT);
                String s = current();
                float tw = p.measureText(s);
                float base = kbTop * 0.62f;
                c.drawText(s, w / 2, base, p);
                if (((now - blinkBase) / 450) % 2 == 0 || now - pressedAt < 400) {
                    p.setColor(GOLD);
                    float cx = w / 2 + tw / 2 + 4 * d;
                    c.drawRoundRect(new RectF(cx, base - textSize * 0.78f, cx + 3 * d, base + textSize * 0.12f), 2 * d, 2 * d, p);
                }
            }
            float pad = 4 * d, gap = Math.max(3 * d, w * 0.012f);
            float kw = (w - 2 * pad - 9 * gap) / 10;
            float kh = Math.min(kw * 1.3f, (h - kbTop - 4 * gap) / 4f);
            float y = kbTop + gap;
            float rad = kw * 0.18f;
            for (int r = 0; r < 4; r++) {
                if (r < 3) {
                    String row = ROWS[r];
                    float off = r == 2 ? kw * 1.5f + gap : 0;
                    float x = pad + off;
                    if (r == 2) drawKey(c, new RectF(pad, y, pad + kw * 1.3f, y + kh), rad, false, null, now);
                    for (int i = 0; i < row.length(); i++) {
                        char ch = row.charAt(i);
                        drawKey(c, new RectF(x, y, x + kw, y + kh), rad, ch == pressed, String.valueOf(ch), now);
                        x += kw + gap;
                    }
                    if (r == 2) drawKey(c, new RectF(w - pad - kw * 1.3f, y, w - pad, y + kh), rad, pressed == '\b', null, now);
                } else {
                    float sx = pad + 2 * (kw * 1.25f + gap);
                    drawKey(c, new RectF(pad, y, pad + kw * 2.5f + gap, y + kh), rad, false, null, now);
                    drawKey(c, new RectF(sx + gap * 0, y, w - pad - kw * 2.5f - gap, y + kh), rad, pressed == ' ', null, now);
                    drawKey(c, new RectF(w - pad - kw * 2.5f, y, w - pad, y + kh), rad, false, null, now);
                }
                y += kh + gap;
            }
            if (running) postInvalidateOnAnimation();
        }

        private void drawKey(Canvas c, RectF r, float rad, boolean isPressed, String label, long now) {
            float t = isPressed ? Math.max(0, 1 - (now - pressedAt) / 220f) : 0;
            p.setColor(KEY_DARK);
            c.drawRoundRect(new RectF(r.left, r.top + d, r.right, r.bottom + d), rad, rad, p);
            p.setShader(t > 0 ? new LinearGradient(r.left, r.top, r.right, r.bottom, blend(KEY, GOLD_A, t), blend(KEY, GOLD_B, t), Shader.TileMode.CLAMP) : null);
            p.setColor(KEY);
            float sc = 1 - 0.08f * t;
            float cx = r.centerX(), cy = r.centerY();
            RectF rr = new RectF(cx - r.width() / 2 * sc, cy - r.height() / 2 * sc, cx + r.width() / 2 * sc, cy + r.height() / 2 * sc);
            c.drawRoundRect(rr, rad, rad, p);
            p.setShader(null);
            if (label != null && r.height() > 18 * d) {
                p.setTypeface(semibold(getContext()));
                p.setTextSize(r.height() * 0.42f);
                p.setColor(blend(0xFFBDBDC4, 0xFFFFFFFF, t));
                c.drawText(label, cx, cy - (p.descent() + p.ascent()) / 2, p);
            }
        }
    }

    /** Compact list row with label and pink switch. */
    static final class ToggleRow extends View {
        interface OnChange { void changed(boolean on); }
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String label;
        private final float d;
        private boolean on, pressed;
        private float t;
        private OnChange listener;

        ToggleRow(Context c, String label, boolean on) {
            super(c);
            this.label = label; this.on = on; t = on ? 1 : 0;
            d = getResources().getDisplayMetrics().density;
            setOnClickListener(v -> toggle());
        }

        void setListener(OnChange l) { listener = l; }

        private void toggle() {
            on = !on;
            ValueAnimator a = ValueAnimator.ofFloat(t, on ? 1 : 0);
            a.setDuration(220);
            a.setInterpolator(new DecelerateInterpolator(1.5f));
            a.addUpdateListener(v -> { t = (float) v.getAnimatedValue(); invalidate(); });
            a.start();
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            if (listener != null) listener.changed(on);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) { pressed = true; invalidate(); }
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) { pressed = false; invalidate(); }
            return super.onTouchEvent(e);
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), (int) (54 * d));
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (pressed) { p.setColor(0x14FFFFFF); c.drawRect(0, 0, w, h, p); }
            p.setTypeface(regular(getContext()));
            p.setTextSize(16.5f * d);
            p.setColor(TEXT);
            p.setTextAlign(Paint.Align.LEFT);
            c.drawText(label, 18 * d, h / 2 - (p.descent() + p.ascent()) / 2, p);
            float sw = 46 * d, sh = 28 * d;
            RectF r = new RectF(w - 16 * d - sw, h / 2 - sh / 2, w - 16 * d, h / 2 + sh / 2);
            p.setColor(blend(0xFF2E2E33, GOLD_B, t));
            c.drawRoundRect(r, sh / 2, sh / 2, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(r.left + sh / 2 + (sw - sh) * t, r.centerY(), sh / 2 - 3 * d, p);
        }
    }
}
