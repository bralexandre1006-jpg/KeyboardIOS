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
    private LinearLayout shortcutCard, corrCard;

    private void fillShortcuts() {
        if (shortcutCard != null) fillList(shortcutCard, Prefs.SHORTCUT_LIST, "Raccourci (ex : allah)", "Texte proposé (ex : الله)", "+ Ajouter un raccourci");
        if (corrCard != null) fillList(corrCard, Prefs.CORR_LIST, "Quand j'écris (ex : mas)", "Remplacer par (ex : pas)", "+ Ajouter une correction");
    }

    private void fillList(LinearLayout cardView, String key, String hintA, String hintB, String addLabel) {
        cardView.removeAllViews();
        String raw = Prefs.str(this, key);
        List<String> lines = new ArrayList<>();
        for (String l : raw.split("\n")) if (l.indexOf('\t') > 0) lines.add(l);
        for (String l : lines) {
            int t = l.indexOf('\t');
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            row.addView(text(l.substring(0, t), 18, Ui.GOLD, Ui.heavy(this)), new LinearLayout.LayoutParams(dp(100), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(text("→  " + l.substring(t + 1), 16, Ui.TEXT, Ui.regular(this)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView x = text("✕", 18, Ui.SUB, Ui.semibold(this));
            x.setPadding(dp(12), dp(4), dp(4), dp(4));
            x.setOnClickListener(v -> {
                List<String> keep = new ArrayList<>(lines);
                keep.remove(l);
                Prefs.get(this).edit().putString(key, String.join("\n", keep)).apply();
                fillShortcuts();
            });
            row.addView(x);
            cardView.addView(row);
        }
        TextView addBtn = link(addLabel, Ui.GOLD);
        addBtn.setOnClickListener(v -> addEntry(key, hintA, hintB));
        cardView.addView(addBtn);
    }

    private void addEntry(String key, String hintA, String hintB) {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(20), dp(8), dp(20), 0);
        EditText a = new EditText(this);
        a.setHint(hintA);
        EditText b = new EditText(this);
        b.setHint(hintB);
        f.addView(a);
        f.addView(b);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(key.equals(Prefs.CORR_LIST) ? "Nouvelle correction" : "Nouveau raccourci").setView(f)
                .setPositiveButton("Ajouter", (dlg, w) -> {
                    String k = a.getText().toString().trim().toLowerCase(), v = b.getText().toString().trim();
                    if (k.isEmpty() || v.isEmpty() || k.contains(" ")) return;
                    String raw = Prefs.str(this, key);
                    Prefs.get(this).edit().putString(key, (raw.isEmpty() ? "" : raw + "\n") + k + "\t" + v).apply();
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
        rootView = root;
        mainView = sv;

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
        head.setPadding(dp(2), dp(14), 0, dp(16));
        head.addView(new Ui.Logo(this), new LinearLayout.LayoutParams(dp(64), dp(32)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, 0, 0);
        titles.addView(text("Clavier", 28, Ui.TEXT, Ui.heavy(this)));
        TextView sub = text("STYLE IOS", 12, Ui.SUB, Ui.semibold(this));
        sub.setLetterSpacing(0.22f);
        titles.addView(sub);
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        headerChip = new Ui.Chip(this, "…", Ui.TEXT, 0xFF1C1C20, Ui.SUB);
        head.addView(headerChip);
        add(head, 0);

        LinearLayout hero = card();
        heroChip = new Ui.Chip(this, "…", Ui.GREEN, Ui.GREEN_BG, Ui.GREEN);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.addView(heroChip);
        hero.addView(chipRow);
        test = new EditText(this);
        test.setHint("Tapez ici pour essayer…");
        test.setHintTextColor(0xFF4A4A52);
        test.setTextColor(Ui.TEXT);
        test.setTypeface(Ui.semibold(this));
        test.setTextSize(22);
        test.setBackground(null);
        test.setPadding(0, dp(14), 0, dp(4));
        test.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        hero.addView(test);
        cta = new Ui.GoldButton(this, "…");
        cta.setOnClickListener(v -> onCta());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(8);
        hero.addView(cta, clp);
        add(hero, 0);

        section("RÉGLAGES");
        LinearLayout nav = card();
        nav.setPadding(0, dp(4), 0, dp(4));
        navRow(nav, 0xFFFF3D8B, "⌨", "Clavier", this::pageKeyboard, true);
        navRow(nav, 0xFF0A84FF, "✓", "Correction et saisie", this::pageTyping, true);
        navRow(nav, 0xFFFF453A, "♪", "Sons et vibrations", this::pageSounds, true);
        navRow(nav, 0xFFFF9F0A, "◎", "Globe, micro et emoji", this::pageIcons, false);
        add(nav, 0);

        section("OUTILS");
        LinearLayout nav2 = card();
        nav2.setPadding(0, dp(4), 0, dp(4));
        navRow(nav2, 0xFFBF5AF2, "文", "Traduction", this::pageTranslate, true);
        navRow(nav2, 0xFF30D158, "✎", "Mes corrections", this::pageCorrections, true);
        navRow(nav2, 0xFF64D2FF, "⚡", "Raccourcis", this::pageShortcuts, false);
        add(nav2, 0);

        section("PLUS");
        LinearLayout nav3 = card();
        nav3.setPadding(0, dp(4), 0, dp(4));
        navRow(nav3, 0xFF5E5CE6, "✦", "Effets et apparence", this::pageEffects, true);
        navRow(nav3, 0xFFFFD60A, "★", "Easter eggs", this::pageEggs, true);
        navRow(nav3, 0xFF8E8E93, "⋯", "Données", this::pageData, false);
        add(nav3, 0);

        TextView ver = text("Clavier 1.7", 12, 0xFF4A4A52, Ui.regular(this));
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(24), 0, 0);
        add(ver, 0);
    }

    private static final int REQ_EGG_TOKEN = 5, REQ_EGG_31 = 6;
    interface Page { void build(LinearLayout c); }
    private FrameLayout rootView;
    private View mainView, pageView;

    private void navRow(LinearLayout parent, int color, String glyph, String label, Page page, boolean sep) {
        Ui.NavRow r = new Ui.NavRow(this, color, glyph, label);
        r.setOnClickListener(v -> openPage(label, page));
        parent.addView(r);
        if (sep) {
            View line = new View(this);
            line.setBackgroundColor(0xFF26262B);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.6f)));
            lp.leftMargin = dp(58);
            parent.addView(line, lp);
        }
    }

    private void openPage(String title, Page page) {
        if (pageView != null) return;
        FrameLayout pg = new FrameLayout(this);
        pg.setBackgroundColor(Ui.BG);
        pg.setClickable(true);
        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(6), dp(18), dp(48));
        TextView back = text("‹  Réglages", 17, Ui.GOLD, Ui.semibold(this));
        back.setPadding(0, dp(12), dp(20), dp(8));
        back.setOnClickListener(v -> closePage());
        c.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView t = text(title, 30, Ui.TEXT, Ui.heavy(this));
        t.setPadding(dp(2), dp(4), 0, dp(14));
        c.addView(t);
        page.build(c);
        sv.addView(c);
        pg.addView(sv);
        rootView.addView(pg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pageView = pg;
        float w = rootView.getWidth();
        pg.setTranslationX(w);
        pg.animate().translationX(0).setDuration(260).setInterpolator(new DecelerateInterpolator(2f)).start();
        mainView.animate().translationX(-w * 0.25f).alpha(0.4f).setDuration(260).setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    private void closePage() {
        View pg = pageView;
        if (pg == null) return;
        pageView = null;
        float w = rootView.getWidth();
        pg.animate().translationX(w).setDuration(220).setInterpolator(new DecelerateInterpolator(1.5f)).withEndAction(() -> rootView.removeView(pg)).start();
        mainView.animate().translationX(0).alpha(1f).setDuration(220).setInterpolator(new DecelerateInterpolator(1.5f)).start();
        shortcutCard = null; corrCard = null;
    }

    @Override
    public void onBackPressed() {
        if (pageView != null) closePage(); else super.onBackPressed();
    }

    private void label(LinearLayout c, String s) {
        TextView t = text(s, 12.5f, Ui.SUB, Ui.semibold(this));
        t.setLetterSpacing(0.2f);
        t.setPadding(dp(8), dp(18), 0, dp(8));
        c.addView(t);
    }

    private void pageKeyboard(LinearLayout c) {
        label(c, "APPARENCE");
        c.addView(pills(Prefs.THEME, new String[]{"auto", "light", "dark"}, new String[]{"Auto", "Clair", "Sombre"}));
        label(c, "DISPOSITION");
        c.addView(pills(Prefs.LAYOUT, new String[]{"azerty", "qwerty"}, new String[]{"AZERTY", "QWERTY"}));
        label(c, "LANGUES");
        c.addView(pills(Prefs.LANG, new String[]{"fr_en", "fr", "en"}, new String[]{"FR + EN", "Français", "English"}));
        c.addView(spaced(rowCard(new String[][]{{"Clavier turc", Prefs.KB_TR}, {"Clavier hébreu", Prefs.KB_HE}})));
        label(c, "UNE MAIN");
        c.addView(pills(Prefs.ONE_HAND, new String[]{"left", "off", "right"}, new String[]{"Gauche", "Normal", "Droite"}));
    }

    private void pageTyping(LinearLayout c) {
        c.addView(rowCard(new String[][]{{"Correction automatique", Prefs.AUTOCORRECT}, {"Prédictions", Prefs.PREDICT},
                {"Orthographe a / à", Prefs.GRAMMAR}, {"Majuscules automatiques", Prefs.AUTOCAP}, {"Verrouillage majuscules", Prefs.CAPS_LOCK}}));
        label(c, "GESTES");
        c.addView(rowCard(new String[][]{{"Balayer pour écrire", Prefs.SWIPE}, {"Glisser ↓ pour un chiffre", Prefs.SWIPE_DOWN},
                {"Glisser vers le bas pour fermer", Prefs.SWIPE_CLOSE}}));
        label(c, "SUGGESTIONS");
        c.addView(rowCard(new String[][]{{"Mot suivant", Prefs.NEXT_WORD}, {"Emoji suggérés", Prefs.EMOJI_SUGGEST}, {"Espace avant ? ! :", Prefs.FR_SPACING}}));
    }

    private void pageSounds(LinearLayout c) {
        c.addView(rowCard(new String[][]{{"Son des touches", Prefs.SOUND}, {"Vibrations", Prefs.HAPTIC},
                {"Sons iPhone", Prefs.IOS_SOUND}, {"Vibrations iPhone", Prefs.IOS_HAPTIC}}));
    }

    private void pageIcons(LinearLayout c) {
        label(c, "EMPLACEMENT DU GLOBE ET DU MICRO");
        c.addView(pills(Prefs.ICON_POS, new String[]{"top", "bottom", "emoji"}, new String[]{"En haut", "En bas", "Sur 😊"}));
        c.addView(spaced(rowCard(new String[][]{{"Afficher le micro", Prefs.DICTATION}, {"Réglages rapides (appui 😊)", Prefs.QUICK_MENU}})));
        c.addView(noteView("En haut : à la place d'une suggestion. Sur 😊 : appui long sur la touche emoji. Appui long sur le micro pour la langue de dictée."));
    }

    private void pageTranslate(LinearLayout c) {
        label(c, "LANGUE DE TRADUCTION");
        c.addView(pills(Prefs.TR_TARGET, new String[]{"en", "he", "tr", "ar", "es"}, new String[]{"EN", "HE", "TR", "AR", "ES"}));
        label(c, "ACCÈS");
        c.addView(rowCard(new String[][]{{"Traduire dans le menu 😊", Prefs.TR_EMOJI}, {"Icône 文A dans la barre", Prefs.TR_BAR},
                {"Appui long sur Envoyer", Prefs.TR_RETURN}, {"Traduction en ligne (meilleure)", Prefs.TR_ONLINE}}));
        label(c, "LANGUES À L'APPUI LONG SUR ENVOYER");
        c.addView(sendLangCard());
    }

    private void pageCorrections(LinearLayout c) {
        c.addView(noteView("Tes fautes habituelles, corrigées automatiquement à l'espace (ex : mas → pas)."));
        corrCard = card();
        c.addView(spaced(corrCard));
        fillShortcuts();
    }

    private void pageShortcuts(LinearLayout c) {
        c.addView(rowCard(new String[][]{{"Activer les raccourcis", Prefs.SHORTCUTS}}));
        c.addView(noteView("Tapez le raccourci : le texte est proposé dans la barre et remplacé à l'espace (ex : allah → الله)."));
        shortcutCard = card();
        c.addView(spaced(shortcutCard));
        fillShortcuts();
    }

    private void pageEffects(LinearLayout c) {
        c.addView(rowCard(new String[][]{{"Effet verre", Prefs.GLASS}, {"Animations", Prefs.ANIMS},
                {"Frappe intelligente", Prefs.SMART_TOUCH}, {"Secouer pour annuler", Prefs.SHAKE_UNDO}}));
    }

    private void pageEggs(LinearLayout c) {
        c.addView(rowCard(new String[][]{{"Activer les easter eggs", Prefs.EASTER}}));
        c.addView(noteView("Écrivez « jeton fortnite » ou « 31 » suivi d'un espace."));
    }

    private void pageData(LinearLayout c) {
        LinearLayout data = card();
        data.setPadding(dp(18), dp(4), dp(18), dp(4));
        TextView mic = link("Autoriser le micro", Ui.GOLD);
        mic.setGravity(Gravity.START);
        mic.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC));
        data.addView(mic);
        TextView del = link("Effacer les mots appris", Ui.RED);
        del.setGravity(Gravity.START);
        del.setOnClickListener(v -> {
            Dictionary.clearLearned(this);
            getSharedPreferences("emoji", MODE_PRIVATE).edit().clear().apply();
            toast("Mots appris effacés");
        });
        data.addView(del);
        c.addView(data);
    }

    private void pickImage(int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, req);
    }

    private LinearLayout sendLangCard() {
        LinearLayout c = card();
        c.setPadding(0, dp(2), 0, dp(2));
        java.util.Set<String> sel = new java.util.LinkedHashSet<>(java.util.Arrays.asList(Prefs.str(this, Prefs.TR_SEND).split(",")));
        for (int i = 0; i < Translate.CODES.length; i++) {
            String code = Translate.CODES[i];
            Ui.ToggleRow row = new Ui.ToggleRow(this, Translate.NAMES[i], sel.contains(code));
            row.setListener(on -> {
                java.util.Set<String> cur = new java.util.LinkedHashSet<>(java.util.Arrays.asList(Prefs.str(this, Prefs.TR_SEND).split(",")));
                cur.remove("");
                if (on) cur.add(code); else cur.remove(code);
                Prefs.get(this).edit().putString(Prefs.TR_SEND, String.join(",", cur)).apply();
            });
            c.addView(row);
        }
        return c;
    }

    private View spaced(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView noteView(String s) {
        TextView t = text(s, 13, Ui.SUB, Ui.regular(this));
        t.setPadding(dp(8), dp(8), dp(8), 0);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    /** Collapsible group: header row + hidden content. */
    private LinearLayout group(String title) {
        LinearLayout head = card();
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(18), dp(15), dp(16), dp(15));
        head.addView(text(title, 16.5f, Ui.TEXT, Ui.semibold(this)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView chev = text("›", 22, Ui.SUB, Ui.semibold(this));
        head.addView(chev);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(8), 0, dp(4));
        content.setVisibility(View.GONE);
        head.setOnClickListener(v -> {
            boolean show = content.getVisibility() != View.VISIBLE;
            content.setVisibility(show ? View.VISIBLE : View.GONE);
            chev.setRotation(show ? 90 : 0);
            if (show) { content.setAlpha(0f); content.animate().alpha(1f).setDuration(120).start(); }
        });
        add(head, 8);
        add(content, 0);
        return content;
    }

    private void note(String s) {
        TextView t = text(s, 13, Ui.SUB, Ui.regular(this));
        t.setPadding(dp(8), dp(8), dp(8), 0);
        t.setLineSpacing(0, 1.15f);
        add(t, 0);
    }

    private LinearLayout rowCard(String[][] items) {
        LinearLayout c = card();
        c.setPadding(0, dp(2), 0, dp(2));
        for (int i = 0; i < items.length; i++) {
            String key = items[i][1];
            Ui.ToggleRow r = new Ui.ToggleRow(this, items[i][0], Prefs.bool(this, key));
            r.setListener(on -> Prefs.get(this).edit().putBoolean(key, on).apply());
            c.addView(r);
            if (i < items.length - 1) {
                View line = new View(this);
                line.setBackgroundColor(0xFF26262B);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.6f)));
                lp.leftMargin = dp(18);
                c.addView(line, lp);
            }
        }
        return c;
    }

    private void rows(String[][] items, int top) {
        add(rowCard(items), top);
    }

    // ---------- Intro: a keyboard typing the title ----------

    private void playIntro(FrameLayout root) {
        for (View v : blocks) { v.setAlpha(0); v.setTranslationY(dp(24)); }
        FrameLayout splash = new FrameLayout(this);
        splash.setBackgroundColor(Ui.BG);
        splash.setClickable(true);
        Ui.LogoBuild logo = new Ui.LogoBuild(this);
        splash.addView(logo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(splash, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        boolean[] gone = {false};
        Runnable finish = () -> {
            if (gone[0]) return;
            gone[0] = true;
            splash.animate().alpha(0).scaleX(1.08f).scaleY(1.08f).setDuration(260).withEndAction(() -> root.removeView(splash)).start();
            for (int i = 0; i < blocks.size(); i++)
                blocks.get(i).animate().alpha(1).translationY(0).setStartDelay(60 + i * 30L)
                        .setDuration(360).setInterpolator(new DecelerateInterpolator(2f)).start();
        };
        splash.setOnClickListener(v -> finish.run());
        logo.start(() -> splash.postDelayed(finish, 150));
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
        if ((req == REQ_EGG_TOKEN || req == REQ_EGG_31) && res == RESULT_OK && data != null && data.getData() != null) {
            try (InputStream in = getContentResolver().openInputStream(data.getData())) {
                write(in, new File(getFilesDir(), req == REQ_EGG_TOKEN ? "egg_token.png" : "egg_31.png"));
                toast("Image enregistrée");
            } catch (Exception e) { toast("Erreur de lecture"); }
            return;
        }
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
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), Ui.BORDER);
        c.setBackground(bg);
        c.setPadding(dp(18), dp(16), dp(18), dp(12));
        return c;
    }

    private void section(String s) {
        TextView t = text(s, 12.5f, Ui.SUB, Ui.semibold(this));
        t.setLetterSpacing(0.2f);
        t.setPadding(dp(8), dp(26), 0, dp(9));
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
