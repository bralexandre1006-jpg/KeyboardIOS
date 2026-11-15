package com.nomad.ioskeyboard;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SettingsActivity extends Activity {
    static final String EXTRA_ASK_MIC = "ask_mic";
    private static final int REQ_FONT = 1, REQ_MIC = 2;
    private float d;
    private Ui.Palette pal;
    private LinearLayout box;
    private final List<View> cards = new ArrayList<>();
    private TextView statusText;
    private View statusDot;
    private LinearLayout setupCard;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        pal = new Ui.Palette(this);
        Window w = getWindow();
        w.setStatusBarColor(pal.bg);
        w.setNavigationBarColor(pal.bg);
        int flags = 0;
        if (!pal.dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        w.getDecorView().setSystemUiVisibility(flags);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(pal.bg);
        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        sv.setClipToPadding(false);
        sv.setVerticalScrollBarEnabled(false);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(40));
        sv.addView(box);
        root.addView(sv);
        setContentView(root);

        buildContent();

        boolean animate = b == null && !getIntent().getBooleanExtra(EXTRA_ASK_MIC, false);
        if (animate) playIntro(root); else for (View v : cards) v.setAlpha(1);

        if (getIntent().getBooleanExtra(EXTRA_ASK_MIC, false))
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
    }

    // ---------- Content ----------

    private void buildContent() {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(4), dp(20), dp(4), dp(18));
        Ui.Logo logo = new Ui.Logo(this);
        head.addView(logo, new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(14), 0, 0, 0);
        TextView title = text("Clavier iOS", 30, pal.text, true);
        titles.addView(title);
        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(0, dp(4), 0, 0);
        statusDot = new View(this);
        status.addView(statusDot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        statusText = text("", 14, pal.sub, false);
        statusText.setPadding(dp(6), 0, 0, 0);
        status.addView(statusText);
        titles.addView(status);
        head.addView(titles);
        addCard(head, false);

        setupCard = card();
        setupCard.addView(sectionNote("Pour commencer"));
        setupCard.addView(actionRow(0xFF0A84FF, "1", "Activer le clavier", "Réglages Android › Clavier iOS",
                v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)), true));
        setupCard.addView(actionRow(0xFF5E5CE6, "2", "Choisir « Clavier iOS »", "Sélecteur de clavier",
                v -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker(), false));
        addCard(setupCard, true);

        header("ESSAYER");
        LinearLayout tryCard = card();
        EditText test = new EditText(this);
        test.setHint("Écrivez quelque chose…");
        test.setHintTextColor(pal.sub);
        test.setTextColor(pal.text);
        test.setTypeface(Ui.font(this));
        test.setTextSize(17);
        test.setBackground(null);
        test.setMinLines(3);
        test.setGravity(Gravity.TOP);
        test.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        test.setPadding(dp(16), dp(14), dp(16), dp(14));
        tryCard.addView(test);
        addCard(tryCard, true);

        header("SAISIE");
        LinearLayout c1 = card();
        c1.addView(toggleRow(0xFF34C759, "Aa", "Majuscules automatiques", Prefs.AUTOCAP, true));
        c1.addView(toggleRow(0xFF0A84FF, "✓", "Correction automatique", Prefs.AUTOCORRECT, true));
        c1.addView(toggleRow(0xFF5E5CE6, "…", "Prédictions", Prefs.PREDICT, true));
        c1.addView(toggleRow(0xFFFF9F0A, "~", "Balayer pour écrire", Prefs.SWIPE, true));
        c1.addView(toggleRow(0xFF8E8E93, ".", "Raccourci « . »", Prefs.DOUBLE_SPACE, true));
        c1.addView(toggleRow(0xFF636366, "⇪", "Verrouillage majuscules", Prefs.CAPS_LOCK, false));
        addCard(c1, true);
        footer("Double espace ajoute un point. Appui long sur 😊 dans le clavier pour les réglages rapides.");

        header("SONS ET VIBRATIONS");
        LinearLayout c2 = card();
        c2.addView(toggleRow(0xFFFF2D55, "♪", "Son des touches", Prefs.SOUND, true));
        c2.addView(toggleRow(0xFFFF375F, "≋", "Retour haptique", Prefs.HAPTIC, false));
        addCard(c2, true);

        header("DICTÉE");
        LinearLayout c3 = card();
        c3.addView(toggleRow(0xFFFF3B30, "◉", "Touche micro", Prefs.DICTATION, true));
        c3.addView(actionRow(0xFFFF9500, "⚙", "Autoriser le micro", null,
                v -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC), false));
        addCard(c3, true);

        header("DISPOSITION");
        LinearLayout c4 = card();
        c4.addView(segmentRow("Clavier", Prefs.LAYOUT, new String[]{"azerty", "qwerty"}, new String[]{"AZERTY", "QWERTY"}, true));
        c4.addView(segmentRow("Langues", Prefs.LANG, new String[]{"fr_en", "fr", "en"}, new String[]{"FR + EN", "Français", "English"}, true));
        c4.addView(segmentRow("Apparence", Prefs.THEME, new String[]{"auto", "light", "dark"}, new String[]{"Auto", "Clair", "Sombre"}, true));
        c4.addView(toggleRow(0xFF30B0C7, "◎", "Globe et micro en bas", Prefs.BOTTOM_BAR, false));
        addCard(c4, true);

        header("EMOJI");
        LinearLayout c5 = card();
        c5.addView(actionRow(0xFFFFCC00, "☺", "Importer les emoji iOS", ".zip du module ou fichier .ttf", v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_FONT);
        }, true));
        c5.addView(actionRow(0xFF8E8E93, "↺", "Police emoji du système", null, v -> {
            if (EmojiData.fontFile(this).delete()) toast("Police réinitialisée");
        }, false));
        addCard(c5, true);
        footer("Pour des emoji iOS dans toutes les apps, installez le module Magisk/KernelSU « iOS Emoji » (root).");

        header("DONNÉES");
        LinearLayout c6 = card();
        TextView del = text("Effacer les mots appris", 17, 0xFFFF3B30, false);
        del.setPadding(dp(16), dp(13), dp(16), dp(13));
        del.setBackground(rowBg());
        del.setOnClickListener(v -> {
            Dictionary.clearLearned(this);
            getSharedPreferences("emoji", MODE_PRIVATE).edit().clear().apply();
            toast("Mots appris effacés");
        });
        c6.addView(del);
        addCard(c6, true);

        TextView ver = text("Clavier iOS 1.4 · police Inter (OFL)", 12, pal.sub, false);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(24), 0, 0);
        addCard(ver, false);
    }

    // ---------- Intro animation ----------

    private void playIntro(FrameLayout root) {
        for (View v : cards) { v.setAlpha(0); v.setTranslationY(dp(36)); }
        FrameLayout splash = new FrameLayout(this);
        splash.setBackgroundColor(pal.bg);
        splash.setClickable(true);
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.Logo logo = new Ui.Logo(this);
        center.addView(logo, new LinearLayout.LayoutParams(dp(112), dp(112)));
        TextView t = text("Clavier iOS", 28, pal.text, true);
        t.setPadding(0, dp(18), 0, 0);
        center.addView(t);
        TextView s = text("pour Android", 15, pal.sub, false);
        center.addView(s);
        splash.addView(center, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(splash, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        logo.setScaleX(0.3f); logo.setScaleY(0.3f); logo.setAlpha(0f); logo.setRotation(-12);
        t.setAlpha(0); t.setTranslationY(dp(16));
        s.setAlpha(0); s.setTranslationY(dp(16));
        logo.animate().scaleX(1).scaleY(1).alpha(1).rotation(0).setDuration(650).setInterpolator(new OvershootInterpolator(1.8f)).start();
        logo.animateKeys();
        t.animate().alpha(1).translationY(0).setStartDelay(300).setDuration(450).setInterpolator(new DecelerateInterpolator()).start();
        s.animate().alpha(1).translationY(0).setStartDelay(420).setDuration(450).setInterpolator(new DecelerateInterpolator()).start();

        splash.postDelayed(() -> {
            AnimatorSet out = new AnimatorSet();
            out.playTogether(ObjectAnimator.ofFloat(splash, View.ALPHA, 1, 0),
                    ObjectAnimator.ofFloat(center, View.SCALE_X, 1, 1.15f),
                    ObjectAnimator.ofFloat(center, View.SCALE_Y, 1, 1.15f));
            out.setDuration(380);
            out.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) { root.removeView(splash); }
            });
            out.start();
            for (int i = 0; i < cards.size(); i++) {
                cards.get(i).animate().alpha(1).translationY(0).setStartDelay(120 + i * 45L)
                        .setDuration(480).setInterpolator(new DecelerateInterpolator(2f)).start();
            }
        }, 1500);
    }

    // ---------- Status ----------

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        boolean enabled = false;
        for (InputMethodInfo i : imm.getEnabledInputMethodList())
            if (i.getPackageName().equals(getPackageName())) enabled = true;
        String cur = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean selected = cur != null && cur.startsWith(getPackageName() + "/");
        statusText.setText(!enabled ? "Non activé" : !selected ? "Activé · non sélectionné" : "Actif");
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(selected && enabled ? pal.green : 0xFFFF9F0A);
        statusDot.setBackground(dot);
        setupCard.setVisibility(enabled && selected ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && statusText != null) refreshStatus();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == REQ_MIC) {
            boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
            toast(ok ? "Micro autorisé" : "Micro refusé");
            if (ok) Prefs.get(this).edit().putBoolean(Prefs.DICTATION, true).apply();
            if (ok && getIntent().getBooleanExtra(EXTRA_ASK_MIC, false)) finish();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_FONT || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        File tmp = new File(getFilesDir(), "emoji.tmp");
        try {
            copyFont(uri, tmp);
        } catch (Exception e) {
            tmp.delete();
            toast(e.getMessage() != null ? e.getMessage() : "Erreur de lecture");
            return;
        }
        try {
            Typeface.createFromFile(tmp);
        } catch (Exception e) {
            tmp.delete();
            toast("Police invalide");
            return;
        }
        if (tmp.renameTo(EmojiData.fontFile(this))) toast("Emoji iOS importés ✓");
    }


    private void copyFont(Uri uri, File dst) throws Exception {
        boolean zip;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            zip = in.read() == 'P' && in.read() == 'K';
        }
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (!zip) {
                write(in, dst);
                return;
            }
            ZipInputStream z = new ZipInputStream(in);
            ZipEntry e;
            File best = null;
            long bestSize = 0;
            boolean bestEmoji = false;
            int n = 0;
            while ((e = z.getNextEntry()) != null) {
                String name = e.getName().toLowerCase();
                if (e.isDirectory() || !(name.endsWith(".ttf") || name.endsWith(".otf"))) continue;
                File f = new File(getCacheDir(), "font" + (n++) + ".ttf");
                write(z, f);
                boolean emoji = name.contains("emoji");
                if ((emoji && !bestEmoji) || (emoji == bestEmoji && f.length() > bestSize)) {
                    if (best != null) best.delete();
                    best = f; bestSize = f.length(); bestEmoji = emoji;
                } else {
                    f.delete();
                }
            }
            if (best == null) throw new Exception("Aucune police trouvée dans le .zip");
            if (!best.renameTo(dst)) {
                try (InputStream bi = new java.io.FileInputStream(best)) { write(bi, dst); }
                best.delete();
            }
        }
    }
    private static void write(InputStream in, File f) throws Exception {
        try (OutputStream out = new FileOutputStream(f)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        }
    }

    // ---------- Builders ----------

    private int dp(float v) { return (int) (v * d); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(bold ? Ui.bold(this) : Ui.font(this));
        return t;
    }

    private void addCard(View v, boolean spaced) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (spaced) lp.topMargin = dp(4);
        box.addView(v, lp);
        cards.add(v);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pal.card);
        bg.setCornerRadius(dp(12));
        c.setBackground(bg);
        c.setClipToOutline(true);
        return c;
    }

    private void header(String s) {
        TextView t = text(s, 13, pal.sub, false);
        t.setLetterSpacing(0.04f);
        t.setPadding(dp(16), dp(26), dp(16), dp(6));
        addCard(t, false);
    }

    private void footer(String s) {
        TextView t = text(s, 13, pal.sub, false);
        t.setPadding(dp(16), dp(7), dp(16), 0);
        addCard(t, false);
    }

    private TextView sectionNote(String s) {
        TextView t = text(s, 15, pal.text, true);
        t.setPadding(dp(16), dp(14), dp(16), dp(4));
        return t;
    }

    private StateListDrawable rowBg() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, new android.graphics.drawable.ColorDrawable(pal.pressed));
        s.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(0));
        s.setExitFadeDuration(250);
        return s;
    }

    private LinearLayout baseRow(int color, String glyph, String label, String sub, boolean sep) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(9), dp(14), dp(9));
        row.setMinimumHeight(dp(50));
        row.addView(new Ui.Icon(this, color, glyph));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(13), 0, dp(8), 0);
        texts.addView(text(label, 17, pal.text, false));
        if (sub != null) texts.addView(text(sub, 13, pal.sub, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        outer.addView(row);
        if (sep) {
            View line = new View(this);
            line.setBackgroundColor(pal.sep);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f)));
            lp.leftMargin = dp(56);
            outer.addView(line, lp);
        }
        return outer;
    }

    private View toggleRow(int color, String glyph, String label, String key, boolean sep) {
        LinearLayout outer = baseRow(color, glyph, label, null, sep);
        LinearLayout row = (LinearLayout) outer.getChildAt(0);
        Ui.Switch sw = new Ui.Switch(this, pal, Prefs.bool(this, key));
        sw.setListener(on -> Prefs.get(this).edit().putBoolean(key, on).apply());
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        return outer;
    }

    private View actionRow(int color, String glyph, String label, String sub, View.OnClickListener l, boolean sep) {
        LinearLayout outer = baseRow(color, glyph, label, sub, sep);
        LinearLayout row = (LinearLayout) outer.getChildAt(0);
        row.addView(text("›", 24, pal.sub, false));
        row.setBackground(rowBg());
        row.setOnClickListener(l);
        return outer;
    }

    private View segmentRow(String label, String key, String[] values, String[] labels, boolean sep) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(16), dp(12), dp(16), dp(12));
        outer.addView(text(label, 15, pal.sub, false));
        String cur = Prefs.str(this, key);
        int sel = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) sel = i;
        Ui.Segmented seg = new Ui.Segmented(this, pal, labels, sel);
        seg.setListener(i -> Prefs.get(this).edit().putString(key, values[i]).apply());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        outer.addView(seg, lp);
        if (!sep) return outer;
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(outer);
        View line = new View(this);
        line.setBackgroundColor(pal.sep);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f)));
        l2.leftMargin = dp(16);
        wrap.addView(line, l2);
        return wrap;
    }
}
