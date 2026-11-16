package com.nomad.ioskeyboard;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

final class Feedback {
    private static SoundPool pool;
    private static int sTap, sDel, sMod;
    private final Context ctx;
    private final View view;
    private final AudioManager audio;
    private final Vibrator vibrator;
    private boolean sound, haptic, iosSound, iosHaptic, primitives;

    Feedback(Context c, View v) {
        ctx = c; view = v;
        audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        vibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= 30 && vibrator != null)
            primitives = vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK);
        reload();
    }

    void reload() {
        sound = Prefs.bool(ctx, Prefs.SOUND);
        haptic = Prefs.bool(ctx, Prefs.HAPTIC);
        iosSound = Prefs.bool(ctx, Prefs.IOS_SOUND);
        iosHaptic = Prefs.bool(ctx, Prefs.IOS_HAPTIC);
        if (sound && iosSound && pool == null) loadSounds();
    }

    private void loadSounds() {
        pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        try {
            sTap = load("snd_tap.wav"); sDel = load("snd_del.wav"); sMod = load("snd_mod.wav");
        } catch (Exception ignored) { }
    }

    private int load(String f) throws Exception {
        try (AssetFileDescriptor fd = ctx.getAssets().openFd(f)) { return pool.load(fd, 1); }
    }

    void key(int type) {
        if (sound) {
            if (iosSound && pool != null) {
                int id = type == Key.DELETE ? sDel : (type == Key.SPACE || type == Key.RETURN || type == Key.SHIFT || type == Key.MODE_NUM) ? sMod : sTap;
                float vol = 0.3f;
                if (audio != null) {
                    int max = audio.getStreamMaxVolume(AudioManager.STREAM_SYSTEM);
                    if (max > 0) vol = 0.1f + 0.3f * audio.getStreamVolume(AudioManager.STREAM_SYSTEM) / (float) max;
                }
                pool.play(id, vol, vol, 1, 0, 1f);
            } else if (audio != null) {
                int fx;
                switch (type) {
                    case Key.DELETE: fx = AudioManager.FX_KEYPRESS_DELETE; break;
                    case Key.SPACE: fx = AudioManager.FX_KEYPRESS_SPACEBAR; break;
                    case Key.RETURN: fx = AudioManager.FX_KEYPRESS_RETURN; break;
                    default: fx = AudioManager.FX_KEYPRESS_STANDARD;
                }
                audio.playSoundEffect(fx, 0.35f);
            }
        }
        tick();
    }

    void tick() {
        if (!haptic) return;
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (iosHaptic && primitives && Build.VERSION.SDK_INT >= 30) {
                    vibrator.vibrate(VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.55f).compose());
                } else if (Build.VERSION.SDK_INT >= 29) {
                    vibrator.vibrate(VibrationEffect.createPredefined(iosHaptic ? VibrationEffect.EFFECT_CLICK : VibrationEffect.EFFECT_TICK));
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(8, 80));
                }
                return;
            }
        } catch (Exception ignored) { }
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }
}
