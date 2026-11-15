package com.nomad.ioskeyboard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/** Small iOS-style widgets drawn by hand for the settings screen. */
final class Ui {
    static final class Palette {
        final boolean dark;
        final int bg, card, sep, text, sub, accent, pressed, green;
        Palette(Context c) {
            dark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            bg = dark ? 0xFF000000 : 0xFFF2F2F7;
            card = dark ? 0xFF1C1C1E : 0xFFFFFFFF;
            sep = dark ? 0xFF38383A : 0xFFC6C6C8;
            text = dark ? 0xFFFFFFFF : 0xFF000000;
            sub = dark ? 0xFF8E8E93 : 0xFF6D6D72;
            accent = dark ? 0xFF0A84FF : 0xFF007AFF;
            pressed = dark ? 0xFF2C2C2E : 0xFFE5E5EA;
            green = 0xFF34C759;
        }
    }

    private static Typeface font, bold;

    static Typeface font(Context c) {
        if (font == null) {
            try { font = Typeface.createFromAsset(c.getAssets(), "key_font.ttf"); } catch (Exception e) { font = Typeface.DEFAULT; }
        }
        return font;
    }

    static Typeface bold(Context c) {
        if (bold == null) bold = Typeface.create(font(c), Typeface.BOLD);
        return bold;
    }

    static int blend(int a, int b, float t) {
        return Color.argb(255,
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    /** Animated iOS switch. */
    static final class Switch extends View {
        interface OnChange { void changed(boolean on); }
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Palette pal;
        private final float d;
        private boolean on;
        private float pos;
        private OnChange listener;

        Switch(Context c, Palette pal, boolean on) {
            super(c);
            this.pal = pal;
            d = getResources().getDisplayMetrics().density;
            this.on = on;
            pos = on ? 1 : 0;
            setOnClickListener(v -> toggle());
        }

        void setListener(OnChange l) { listener = l; }

        void toggle() {
            on = !on;
            ValueAnimator a = ValueAnimator.ofFloat(pos, on ? 1 : 0);
            a.setDuration(220);
            a.setInterpolator(new OvershootInterpolator(1.2f));
            a.addUpdateListener(v -> { pos = (float) v.getAnimatedValue(); invalidate(); });
            a.start();
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            if (listener != null) listener.changed(on);
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension((int) (51 * d), (int) (31 * d));
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float t = Math.max(0, Math.min(1, pos));
            p.setColor(blend(pal.dark ? 0xFF39393D : 0xFFE9E9EA, pal.green, t));
            c.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, p);
            float r = h / 2 - 2 * d;
            float cx = h / 2 + (w - h) * pos;
            p.setColor(0x33000000);
            c.drawCircle(cx, h / 2 + d, r, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(cx, h / 2, r, p);
        }
    }

    /** Animated segmented control. */
    static final class Segmented extends View {
        interface OnSelect { void selected(int i); }
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Palette pal;
        private final String[] labels;
        private final float d;
        private int sel;
        private float pos;
        private OnSelect listener;

        Segmented(Context c, Palette pal, String[] labels, int sel) {
            super(c);
            this.pal = pal; this.labels = labels; this.sel = sel; pos = sel;
            d = getResources().getDisplayMetrics().density;
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(font(c));
        }

        void setListener(OnSelect l) { listener = l; }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), (int) (34 * d));
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                int i = (int) Math.max(0, Math.min(labels.length - 1, e.getX() / (getWidth() / (float) labels.length)));
                if (i != sel) {
                    sel = i;
                    ValueAnimator a = ValueAnimator.ofFloat(pos, i);
                    a.setDuration(250);
                    a.setInterpolator(new DecelerateInterpolator(2f));
                    a.addUpdateListener(v -> { pos = (float) v.getAnimatedValue(); invalidate(); });
                    a.start();
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                    if (listener != null) listener.selected(i);
                }
            }
            return true;
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(pal.dark ? 0xFF2C2C2E : 0xFFE3E3E8);
            c.drawRoundRect(new RectF(0, 0, w, h), 9 * d, 9 * d, p);
            float sw = w / labels.length;
            p.setColor(pal.dark ? 0xFF636366 : 0xFFFFFFFF);
            RectF r = new RectF(pos * sw + 2 * d, 2 * d, (pos + 1) * sw - 2 * d, h - 2 * d);
            c.drawRoundRect(r, 7 * d, 7 * d, p);
            p.setTextSize(13.5f * d);
            for (int i = 0; i < labels.length; i++) {
                p.setColor(pal.text);
                p.setFakeBoldText(i == sel);
                c.drawText(labels[i], sw * (i + 0.5f), h / 2 - (p.descent() + p.ascent()) / 2, p);
            }
            p.setFakeBoldText(false);
        }
    }

    /** App logo: a rounded tile with a tiny keyboard. */
    static final class Logo extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private float press = -1;

        Logo(Context c) {
            super(c);
            d = getResources().getDisplayMetrics().density;
        }

        void animateKeys() {
            ValueAnimator a = ValueAnimator.ofFloat(0, 8);
            a.setDuration(1400);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.addUpdateListener(v -> { press = (float) v.getAnimatedValue(); invalidate(); });
            a.start();
        }

        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            float ox = (getWidth() - s) / 2, oy = (getHeight() - s) / 2;
            RectF tile = new RectF(ox, oy, ox + s, oy + s);
            p.setShader(new android.graphics.LinearGradient(0, oy, 0, oy + s, 0xFF3A3A3C, 0xFF1C1C1E, android.graphics.Shader.TileMode.CLAMP));
            c.drawRoundRect(tile, s * 0.225f, s * 0.225f, p);
            p.setShader(null);
            float pad = s * 0.14f, gap = s * 0.035f;
            float kw = (s - 2 * pad - 4 * gap) / 5, kh = s * 0.13f;
            int[] counts = {5, 4, 3};
            float top = oy + s * 0.27f;
            int idx = 0;
            for (int r = 0; r < 3; r++) {
                int n = counts[r];
                float rowW = n * kw + (n - 1) * gap;
                float x = ox + (s - rowW) / 2;
                for (int i = 0; i < n; i++, idx++) {
                    boolean hit = press >= 0 && (int) press % 12 == idx;
                    p.setColor(hit ? 0xFF0A84FF : 0xFF8E8E93);
                    c.drawRoundRect(new RectF(x, top, x + kw, top + kh), kh * 0.25f, kh * 0.25f, p);
                    x += kw + gap;
                }
                top += kh + gap * 1.6f;
            }
            p.setColor(0xFFFFFFFF);
            float sw = s * 0.42f;
            c.drawRoundRect(new RectF(ox + (s - sw) / 2, top, ox + (s + sw) / 2, top + kh), kh * 0.25f, kh * 0.25f, p);
        }
    }

    /** Colored rounded-square row icon with a glyph. */
    static final class Icon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;
        private final String glyph;
        private final float d;

        Icon(Context c, int color, String glyph) {
            super(c);
            this.color = color; this.glyph = glyph;
            d = getResources().getDisplayMetrics().density;
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(bold(c));
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension((int) (29 * d), (int) (29 * d));
        }

        @Override protected void onDraw(Canvas c) {
            float s = getWidth();
            p.setColor(color);
            c.drawRoundRect(new RectF(0, 0, s, s), 7 * d, 7 * d, p);
            p.setColor(0xFFFFFFFF);
            p.setTextSize(glyph.length() > 1 ? 12 * d : 16 * d);
            c.drawText(glyph, s / 2, s / 2 - (p.descent() + p.ascent()) / 2, p);
        }
    }
}
