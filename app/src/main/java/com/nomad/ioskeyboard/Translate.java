package com.nomad.ioskeyboard;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

/** Thin wrapper around ML Kit on-device translation. */
final class Translate {
    interface Callback { void done(String result, String error); }

    static final String[] CODES = {"en", "he", "tr", "ar", "es", "de", "it", "pt", "ru", "nl", "pl", "ro", "zh", "ja", "ko", "hi", "fr"};
    static final String[] NAMES = {"Anglais", "Hébreu", "Turc", "Arabe", "Espagnol", "Allemand", "Italien", "Portugais", "Russe",
            "Néerlandais", "Polonais", "Roumain", "Chinois", "Japonais", "Coréen", "Hindi", "Français"};

    private static Translator translator;
    private static String key = "";
    private static boolean ready;

    static String name(String code) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return NAMES[i];
        return code;
    }

    static boolean online = true;
    private static final java.util.concurrent.ExecutorService NET = java.util.concurrent.Executors.newSingleThreadExecutor();

    /** Online (Google Translate web endpoint) first for better quality, on-device ML Kit as fallback. */
    static void translate(String src, String tgt, String text, Callback cb) {
        if (!online) { translateOffline(src, tgt, text, cb); return; }
        NET.execute(() -> {
            String r = fetchOnline(src, tgt, text);
            if (r != null) cb.done(r, null);
            else translateOffline(src, tgt, text, cb);
        });
    }

    private static String fetchOnline(String src, String tgt, String text) {
        java.net.HttpURLConnection c = null;
        try {
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=" + src + "&tl=" + tgt
                    + "&q=" + java.net.URLEncoder.encode(text, "UTF-8");
            c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(2500);
            c.setReadTimeout(3500);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (c.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            org.json.JSONArray parts = new org.json.JSONArray(sb.toString()).getJSONArray(0);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                Object seg = parts.getJSONArray(i).get(0);
                if (seg instanceof String) out.append((String) seg);
            }
            return out.length() > 0 ? out.toString() : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static synchronized void translateOffline(String src, String tgt, String text, Callback cb) {
        String s = TranslateLanguage.fromLanguageTag(src), t = TranslateLanguage.fromLanguageTag(tgt);
        if (s == null || t == null || s.equals(t)) { cb.done(text, null); return; }
        String k = s + ">" + t;
        if (!k.equals(key) || translator == null) {
            if (translator != null) translator.close();
            translator = Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(s).setTargetLanguage(t).build());
            key = k;
            ready = false;
        }
        final Translator tr = translator;
        if (ready) {
            tr.translate(text).addOnSuccessListener(r -> cb.done(r, null)).addOnFailureListener(e -> cb.done(null, e.getMessage()));
            return;
        }
        tr.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(v -> {
                    synchronized (Translate.class) { if (tr == translator) ready = true; }
                    tr.translate(text).addOnSuccessListener(r -> cb.done(r, null)).addOnFailureListener(e -> cb.done(null, e.getMessage()));
                })
                .addOnFailureListener(e -> cb.done(null, "Téléchargement de la langue impossible"));
    }

    static boolean isReady() { return ready; }
}
