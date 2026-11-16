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
        a.setHint("Raccourci (ex : allah)");
        EditText b = new EditText(this);
        b.setHint("Texte proposé (ex : الله)");
        f.addView(a);
        f.addView(b);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Nouveau raccourci").setView(f)
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
        head.setPadding(dp(2), dp(14), 0, dp(16));
        head.addView(new Ui.Logo(this), new LinearLayout.LayoutParams(dp(46), dp(46)));
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

        section("ESSENTIEL");
        rows(new String[][]{{"Correction automatique", Prefs.AUTOCORRECT}, {"Prédictions", Prefs.PREDICT},
                {"Son des touches", Prefs.SOUND}, {"Vibrations", Prefs.HAPTIC}}, 0);
        add(pills(Prefs.THEME, new String[]{"auto", "light", "dark"}, new String[]{"Auto", "Clair", "Sombre"}), 10);
        add(pills(Prefs.LAYOUT, new String[]{"azerty", "qwerty"}, new String[]{"AZERTY", "QWERTY"}), 8);

        section("PLUS DE RÉGLAGES");
        LinearLayout g = group("Langues");
        g.addView(pills(Prefs.LANG, new String[]{"fr_en", "fr", "en"}, new String[]{"FR + EN", "Français", "English"}));
        g.addView(spaced(rowCard(new String[][]{{"Clavier turc", Prefs.KB_TR}, {"Clavier hébreu", Prefs.KB_HE}})));

        g = group("Globe, micro et emoji");
        g.addView(pills(Prefs.ICON_POS, new String[]{"top", "bottom", "emoji"}, new String[]{"En haut", "En bas", "Sur 😊"}));
        g.addView(spaced(rowCard(new String[][]{{"Afficher le micro", Prefs.DICTATION}, {"Réglages rapides (appui 😊)", Prefs.QUICK_MENU}})));
        g.addView(noteView("« Sur 😊 » : appui long sur la touche emoji pour le globe et le micro, sans prendre de place."));

        g = group("Traduction");
        g.addView(noteView("Langue de traduction"));
        g.addView(spaced(pills(Prefs.TR_TARGET, new String[]{"en", "he", "tr", "ar", "es"}, new String[]{"EN", "HE", "TR", "AR", "ES"})));
        g.addView(spaced(rowCard(new String[][]{{"Traduire dans le menu 😊", Prefs.TR_EMOJI}, {"Icône 文A dans la barre", Prefs.TR_BAR},
                {"Appui long sur Envoyer", Prefs.TR_RETURN}})));
        g.addView(noteView("Mode traduction : la pastille rose change la langue (appui long pour arrêter), touchez la traduction pour l'insérer. Envoyer envoie le texte traduit. La 1re fois, la langue se télécharge (~30 Mo)."));

        g = group("Saisie");
        g.addView(rowCard(new String[][]{{"Orthographe a / à", Prefs.GRAMMAR}, {"Balayer pour écrire", Prefs.SWIPE},
                {"Majuscules automatiques", Prefs.AUTOCAP}, {"Verrouillage majuscules", Prefs.CAPS_LOCK},
                {"Mot suivant", Prefs.NEXT_WORD}, {"Emoji suggérés", Prefs.EMOJI_SUGGEST}, {"Raccourcis", Prefs.SHORTCUTS},
                {"Glisser ↓ pour un chiffre", Prefs.SWIPE_DOWN}, {"Glisser vers le bas pour fermer", Prefs.SWIPE_CLOSE},
                {"Espace avant ? ! :", Prefs.FR_SPACING}}));

        g = group("Sons et effets");
        g.addView(rowCard(new String[][]{{"Sons iPhone", Prefs.IOS_SOUND}, {"Vibrations iPhone", Prefs.IOS_HAPTIC},
                {"Frappe intelligente", Prefs.SMART_TOUCH}, {"Effet verre", Prefs.GLASS}, {"Animations", Prefs.ANIMS},
                {"Secouer pour annuler", Prefs.SHAKE_UNDO}}));

        g = group("Clavier à une main");
        g.addView(pills(Prefs.ONE_HAND, new String[]{"left", "off", "right"}, new String[]{"Gauche", "Normal", "Droite"}));

        g = group("Easter eggs");
        g.addView(rowCard(new String[][]{{"Activer (jeton, 31 31 31)", Prefs.EASTER}}));
        LinearLayout eggs = card();
        eggs.setPadding(dp(18), dp(4), dp(18), dp(4));
        TextView tok = link("Choisir l'image du jeton", Ui.GOLD);
        tok.setGravity(Gravity.START);
        tok.setOnClickListener(v -> pickImage(REQ_EGG_TOKEN));
        eggs.addView(tok);
        TextView face = link("Choisir la photo « 31 »", Ui.GOLD);
        face.setGravity(Gravity.START);
        face.setOnClickListener(v -> pickImage(REQ_EGG_31));
        eggs.addView(face);
        g.addView(spaced(eggs));
        g.addView(noteView("Écrivez « jeton fortnite » ou « 31 31 31 » pour les déclencher."));

        g = group("Raccourcis");
        g.addView(noteView("Tapez le raccourci : le texte est proposé dans la barre et remplacé à l'espace (ex : allah → الله)."));
        shortcutCard = card();
        g.addView(spaced(shortcutCard));
        fillShortcuts();

        g = group("Données");
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
        g.addView(data);

        TextView ver = text("Clavier 1.6", 12, 0xFF4A4A52, Ui.regular(this));
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(24), 0, 0);
        add(ver, 0);
    }

    private static final int REQ_EGG_TOKEN = 5, REQ_EGG_31 = 6;

    private void pickImage(int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, req);
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
            chev.animate().rotation(show ? 90 : 0).setDuration(200).start();
            if (show) { content.setAlpha(0); content.setTranslationY(-dp(8)); content.animate().alpha(1).translationY(0).setDuration(220).start(); }
        });
        head.setOnTouchListener((v, e) -> { Ui.pressBounce(v, e); return false; });
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
