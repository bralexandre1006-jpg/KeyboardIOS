package com.nomad.ioskeyboard;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
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
    private LinearLayout box;
    private final List<View> blocks = new ArrayList<>();
    private Ui.Chip headerChip, heroChip;
    private Ui.GoldButton cta;
    private EditText test;
    private int state;
    private LinearLayout shortcutCard;

    private void fillShortcuts() {
        shortcutCard.removeAllViews();
        String raw = Prefs.str(this, Prefs.SHORTCUT_LIST);
        List<String> lines = new ArrayList<>();
        for (String l : raw.split("\n")) if (l.indexOf('\t') > 0) lines.add(l);
        for (String l : lines) {
            int t = l.indexOf('\t');
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            TextView k = text(l.substring(0, t), 18, Ui.GOLD, Ui.heavy(this));
            row.addView(k, new LinearLayout.LayoutParams(dp(90), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(text(l.substring(t + 1), 16, Ui.TEXT, Ui.regular(this)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView x = text("✕", 18, Ui.SUB, Ui.semibold(this));
            x.setPadding(dp(12), dp(4), dp(4), dp(4));
            x.setOnClickListener(v -> {
                List<String> keep = new ArrayList<>(lines);
                keep.remove(l);
                Prefs.get(this).edit().putString(Prefs.SHORTCUT_LIST, String.join("\n", keep)).apply();
                fillShortcuts();
            });
            row.addView(x);
            shortcutCard.addView(row);
        }
        TextView addBtn = link("+ Ajouter un raccourci", Ui.GOLD);
        addBtn.setOnClickListener(v -> addShortcut());
        shortcutCard.addView(addBtn);
    }

    private void addShortcut() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(20), dp(8), dp(20), 0);
        EditText a = new EditText(this);
        a.setHint("Raccourci (ex : stp)");
        EditText b = new EditText(this);
        b.setHint("Phrase (ex : s'il te plaît)");
        f.addView(a);
        f.addView(b);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Nouveau remplacement").setView(f)
                .setPositiveButton("Ajouter", (dlg, w) -> {
                    String k = a.getText().toString().trim().toLowerCase(), v = b.getText().toString().trim();
                    if (k.isEmpty() || v.isEmpty() || k.contains(" ")) return;
                    String raw = Prefs.str(this, Prefs.SHORTCUT_LIST);
                    Prefs.get(this).edit().putString(Prefs.SHORTCUT_LIST, (raw.isEmpty() ? "" : raw + "\n") + k + "\t" + v).apply();
                    fillShortcuts();
                })
                .setNegativeButton("Annuler", null).show();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        Window w = getWindow();
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.BG);
        w.getDecorView().setSystemUiVisibility(0);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(10), dp(18), dp(48));
        sv.addView(box);
        root.addView(sv);
        setContentView(root);

        build();

        boolean intro = b == null && !getIntent().getBooleanExtra(EXTRA_ASK_MIC, false);
        if (intro) playIntro(root);
        if (getIntent().getBooleanExtra(EXTRA_ASK_MIC, false))
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
    }

    // ---------- Layout ----------

    private void build() {
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(18), 0, dp(20));
        head.addView(new Ui.Logo(this), new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(14), 0, 0, 0);
        titles.addView(text("Clavier", 34, Ui.TEXT, Ui.heavy(this)));
        TextView sub = text("STYLE IOS", 14, Ui.SUB, Ui.semibold(this));
        sub.setLetterSpacing(0.22f);
        titles.addView(sub);
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        headerChip = new Ui.Chip(this, "…", Ui.TEXT, 0xFF1C1C20, Ui.SUB);
        head.addView(headerChip);
        add(head, 0);

        LinearLayout hero = card();
        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        heroChip = new Ui.Chip(this, "…", Ui.GREEN, Ui.GREEN_BG, Ui.GREEN);
        chips.addView(heroChip);
        View spacer = new View(this);
        chips.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        String lang = Prefs.str(this, Prefs.LANG);
        chips.addView(new Ui.Chip(this, (lang.equals("fr_en") ? "FR · EN" : lang.toUpperCase()) + "  " + Prefs.str(this, Prefs.LAYOUT).toUpperCase(),
                Ui.TEXT, 0xFF222226, 0));
        hero.addView(chips);
        TextView lbl = text("ESSAYER", 16, Ui.SUB, Ui.semibold(this));
        lbl.setLetterSpacing(0.22f);
        lbl.setPadding(0, dp(22), 0, dp(4));
        hero.addView(lbl);
        test = new EditText(this);
        test.setHint("Tapez ici…");
        test.setHintTextColor(0xFF4A4A52);
        test.setTextColor(Ui.TEXT);
        test.setTypeface(Ui.heavy(this));
        test.setTextSize(30);
        test.setBackground(null);
        test.setPadding(0, 0, 0, dp(6));
        test.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        hero.addView(test);

        FrameLayout strip = new FrameLayout(this);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0xFF0E0E10);
        sbg.setCornerRadius(dp(24));
        sbg.setStroke(dp(1), Ui.BORDER);
        strip.setBackground(sbg);
        strip.setPadding(dp(14), dp(14), dp(14), dp(14));
        Ui.TypingKeyboard demo = new Ui.TypingKeyboard(this,
                new String[]{"Salut, ça va ?", "J'arrive dans 5 min", "Bonne soirée !", "Merci beaucoup"}, true, true, 20);
        strip.addView(demo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)));
        demo.start(2200);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(10);
        hero.addView(strip, slp);

        cta = new Ui.GoldButton(this, "…");
        cta.setOnClickListener(v -> onCta());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(16);
        hero.addView(cta, clp);
        add(hero, 0);

        section("APPARENCE");
        add(pills(Prefs.THEME, new String[]{"auto", "light", "dark"}, new String[]{"◐  Auto", "☀  Clair", "☾  Sombre"}), 0);
        add(pills(Prefs.LAYOUT, new String[]{"azerty", "qwerty"}, new String[]{"AZERTY", "QWERTY"}), 10);
        add(pills(Prefs.LANG, new String[]{"fr_en", "fr", "en"}, new String[]{"FR + EN", "Français", "English"}), 10);

        section("AUTRES CLAVIERS");
        grid(new String[][]{{"Turc (Türkçe)", Prefs.KB_TR}, {"Hébreu (עברית)", Prefs.KB_HE}});
        TextView kbTip = text("Une touche 🌐 apparaît dans le clavier pour passer d'une langue à l'autre (appui long : choisir un autre clavier).", 14, Ui.SUB, Ui.regular(this));
        kbTip.setPadding(dp(6), dp(12), dp(6), 0);
        add(kbTip, 0);

        section("SAISIE");
        grid(new String[][]{{"Correction auto", Prefs.AUTOCORRECT}, {"Prédictions", Prefs.PREDICT},
                {"Balayer pour écrire", Prefs.SWIPE}, {"Majuscules auto", Prefs.AUTOCAP},
                {"Verr. majuscules", Prefs.CAPS_LOCK}});

        section("SONS & DICTÉE");
        grid(new String[][]{{"Son des touches", Prefs.SOUND}, {"Vibrations", Prefs.HAPTIC},
                {"Dictée (micro)", Prefs.DICTATION}, {"Globe & micro en bas", Prefs.BOTTOM_BAR}});
        TextView tip = text("Astuce : appui long sur 😊 dans le clavier pour les réglages rapides.", 14, Ui.SUB, Ui.regular(this));
        tip.setPadding(dp(6), dp(12), dp(6), 0);
        add(tip, 0);

        section("AVANCÉ");
        grid(new String[][]{{"Sons iPhone", Prefs.IOS_SOUND}, {"Vibrations iPhone", Prefs.IOS_HAPTIC},
                {"Frappe intelligente", Prefs.SMART_TOUCH}, {"Effet verre", Prefs.GLASS},
                {"Animations", Prefs.ANIMS}, {"Mot suivant", Prefs.NEXT_WORD},
                {"Emoji suggérés", Prefs.EMOJI_SUGGEST}, {"Espace avant ? ! :", Prefs.FR_SPACING},
                {"Remplacements", Prefs.SHORTCUTS}, {"Glisser ↓ = chiffre", Prefs.SWIPE_DOWN},
                {"Secouer = annuler", Prefs.SHAKE_UNDO}});
        TextView oh = text("CLAVIER À UNE MAIN", 13, Ui.SUB, Ui.semibold(this));
        oh.setLetterSpacing(0.18f);
        oh.setPadding(dp(6), dp(18), 0, dp(10));
        add(oh, 0);
        add(pills(Prefs.ONE_HAND, new String[]{"left", "off", "right"}, new String[]{"◧  Gauche", "Normal", "Droite  ◨"}), 0);

        section("REMPLACEMENTS DE TEXTE");
        shortcutCard = card();
        add(shortcutCard, 0);
        fillShortcuts();

        section("DONNÉES");
        LinearLayout data = card();
        TextView del = link("Effacer les mots appris", Ui.RED);
        del.setGravity(Gravity.START);
        del.setOnClickListener(v -> {
            Dictionary.clearLearned(this);
            getSharedPreferences("emoji", MODE_PRIVATE).edit().clear().apply();
            toast("Mots appris effacés");
        });
        data.addView(del);
        TextView mic = link("Autoriser le micro", Ui.GOLD);
        mic.setGravity(Gravity.START);
        mic.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC));
        data.addView(mic);
        add(data, 0);

        TextView ver = text("Clavier 1.5 · Outfit & Inter (OFL)", 13, 0xFF4A4A52, Ui.regular(this));
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(28), 0, 0);
        add(ver, 0);
    }

    // ---------- Intro: a keyboard typing the title ----------

    private void playIntro(FrameLayout root) {
        for (View v : blocks) { v.setAlpha(0); v.setTranslationY(dp(40)); }
        FrameLayout splash = new FrameLayout(this);
        splash.setBackgroundColor(Ui.BG);
        splash.setClickable(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.TypingKeyboard kb = new Ui.TypingKeyboard(this, new String[]{"Clavier iOS"}, false, true, 40);
        col.addView(kb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));
        TextView tag = text("ÉCRIVEZ COMME SUR IPHONE", 14, Ui.GOLD, Ui.semibold(this));
        tag.setLetterSpacing(0.22f);
        tag.setAlpha(0);
        tag.setPadding(0, dp(18), 0, 0);
        col.addView(tag);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        lp.leftMargin = lp.rightMargin = dp(22);
        splash.addView(col, lp);
        root.addView(splash, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        kb.setAlpha(0);
        kb.setTranslationY(dp(60));
        kb.animate().alpha(1).translationY(0).setDuration(500).setInterpolator(new DecelerateInterpolator(2f)).start();
        boolean[] gone = {false};
        Runnable finish = () -> {
            if (gone[0]) return;
            gone[0] = true;
            splash.animate().alpha(0).setDuration(420).withEndAction(() -> root.removeView(splash)).start();
            col.animate().translationY(-dp(80)).scaleX(0.92f).scaleY(0.92f).setDuration(420).start();
            for (int i = 0; i < blocks.size(); i++)
                blocks.get(i).animate().alpha(1).translationY(0).setStartDelay(150 + i * 50L)
                        .setDuration(520).setInterpolator(new DecelerateInterpolator(2.2f)).start();
        };
        kb.setOnDone(() -> {
            tag.animate().alpha(1).setDuration(300).start();
            splash.postDelayed(finish, 700);
        });
        splash.setOnClickListener(v -> finish.run());
        kb.start(550);
    }

    // ---------- State ----------

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f && cta != null) refresh();
    }

    private void refresh() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        boolean enabled = false;
        for (InputMethodInfo i : imm.getEnabledInputMethodList())
            if (i.getPackageName().equals(getPackageName())) enabled = true;
        String cur = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean selected = cur != null && cur.startsWith(getPackageName() + "/");
        state = !enabled ? 0 : !selected ? 1 : 2;
        if (state == 2) {
            headerChip.set("ACTIF", Ui.GREEN, Ui.GREEN_BG, Ui.GREEN);
            heroChip.set("PRÊT", Ui.GREEN, Ui.GREEN_BG, Ui.GREEN);
            cta.setLabel("▶  TESTER LE CLAVIER");
        } else {
            headerChip.set(state == 0 ? "INACTIF" : "À CHOISIR", Ui.ORANGE, Ui.ORANGE_BG, Ui.ORANGE);
            heroChip.set(state == 0 ? "ÉTAPE 1/2" : "ÉTAPE 2/2", Ui.ORANGE, Ui.ORANGE_BG, Ui.ORANGE);
            cta.setLabel(state == 0 ? "▶  ACTIVER LE CLAVIER" : "▶  CHOISIR LE CLAVIER");
        }
    }

    private void onCta() {
        if (state == 0) startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        else if (state == 1) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();
        else {
            test.requestFocus();
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(test, InputMethodManager.SHOW_IMPLICIT);
        }
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

    /** Copies a .ttf, or extracts the emoji font from a .zip (e.g. a Magisk emoji module). */

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

    private TextView text(String s, float size, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    private TextView link(String s, int color) {
        TextView t = text(s, 17, color, Ui.semibold(this));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(4), dp(14), dp(4), dp(10));
        t.setOnTouchListener((v, e) -> { Ui.pressBounce(v, e); return false; });
        return t;
    }

    private void add(View v, int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMargin);
        box.addView(v, lp);
        blocks.add(v);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Ui.CARD_TOP, Ui.CARD_BOTTOM});
        bg.setCornerRadius(dp(30));
        bg.setStroke(dp(1), Ui.BORDER);
        c.setBackground(bg);
        c.setPadding(dp(22), dp(22), dp(22), dp(16));
        return c;
    }

    private void section(String s) {
        TextView t = text(s, 15, Ui.SUB, Ui.semibold(this));
        t.setLetterSpacing(0.22f);
        t.setPadding(dp(6), dp(30), 0, dp(12));
        add(t, 0);
    }

    private View pills(String key, String[] values, String[] labels) {
        String cur = Prefs.str(this, key);
        int sel = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) sel = i;
        Ui.Pills p = new Ui.Pills(this, labels, sel);
        p.setListener(i -> Prefs.get(this).edit().putString(key, values[i]).apply());
        return p;
    }

    private void grid(String[][] items) {
        for (int i = 0; i < items.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            for (int j = i; j < Math.min(i + 2, items.length); j++) {
                String key = items[j][1];
                Ui.Tile t = new Ui.Tile(this, items[j][0], Prefs.bool(this, key));
                t.setListener(on -> Prefs.get(this).edit().putBoolean(key, on).apply());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                if (j > i) lp.leftMargin = dp(12);
                row.addView(t, lp);
            }
            add(row, i == 0 ? 0 : 12);
        }
    }
}
