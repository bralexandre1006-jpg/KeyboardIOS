package com.nomad.ioskeyboard;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

/** Thin wrapper around ML Kit on-device translation. */
final class Translate {
    interface Callback { void done(String result, String error); }

    static final String[] CODES = {"en", "he", "tr", "ar", "es", "de", "it", "pt", "ru"};
    static final String[] NAMES = {"Anglais", "Hébreu", "Turc", "Arabe", "Espagnol", "Allemand", "Italien", "Portugais", "Russe"};

    private static Translator translator;
    private static String key = "";
    private static boolean ready;

    static String name(String code) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return NAMES[i];
        return code;
    }

    static synchronized void translate(String src, String tgt, String text, Callback cb) {
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
