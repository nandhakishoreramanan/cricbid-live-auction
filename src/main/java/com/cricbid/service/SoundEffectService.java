package com.cricbid.service;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.awt.GraphicsEnvironment;

/**
 * Synthesizes real-time broadcast audio effects (Bid Chime, Countdown Warning Beep,
 * and SOLD Hammer Chord) using Java's standard javax.sound.sampled API with zero external WAV dependencies.
 */
public class SoundEffectService {

    private static volatile boolean soundEnabled = true;
    private static final float SAMPLE_RATE = 44100f;

    public static boolean isSoundEnabled() {
        return soundEnabled;
    }

    public static void setSoundEnabled(boolean enabled) {
        soundEnabled = enabled;
    }

    /**
     * Ascending two-tone broadcast chime when a bid is placed.
     */
    public static void playBidChime() {
        if (!soundEnabled || GraphicsEnvironment.isHeadless()) return;
        runAsync(() -> {
            playTone(587.33, 65, 0.32); // D5
            playTone(880.00, 110, 0.38); // A5
        });
    }

    /**
     * Short urgent tick during the final 3 seconds of the round countdown.
     */
    public static void playTimerWarningBeep() {
        if (!soundEnabled || GraphicsEnvironment.isHeadless()) return;
        runAsync(() -> playTone(1046.50, 55, 0.25)); // C6 short tick
    }

    /**
     * Double gavel strike + triumphant broadcast fanfare when a player is SOLD.
     */
    public static void playSoldHammer() {
        if (!soundEnabled || GraphicsEnvironment.isHeadless()) return;
        runAsync(() -> {
            playTone(196.00, 70, 0.50); // Low gavel strike 1
            sleepQuietly(45);
            playTone(196.00, 90, 0.55); // Low gavel strike 2
            sleepQuietly(35);
            playTone(523.25, 85, 0.38); // C5
            playTone(659.25, 85, 0.40); // E5
            playTone(783.99, 160, 0.45); // G5
        });
    }

    /**
     * Descending two-tone buzz when a player goes UNSOLD.
     */
    public static void playUnsoldTone() {
        if (!soundEnabled || GraphicsEnvironment.isHeadless()) return;
        runAsync(() -> {
            playTone(392.00, 100, 0.30); // G4
            playTone(293.66, 150, 0.30); // D4
        });
    }

    private static void runAsync(Runnable r) {
        Thread t = new Thread(r, "CricBid-SoundFX");
        t.setDaemon(true);
        t.start();
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void playTone(double freqHz, int durationMs, double volume) {
        try {
            int totalSamples = (int) ((durationMs / 1000.0) * SAMPLE_RATE);
            byte[] buf = new byte[totalSamples * 2]; // 16-bit mono PCM

            int attackSamples = Math.min(totalSamples / 6, 350);
            int releaseSamples = Math.min(totalSamples / 3, 1200);

            for (int i = 0; i < totalSamples; i++) {
                double angle = 2.0 * Math.PI * i * freqHz / SAMPLE_RATE;
                // Blend fundamental sine + soft octave harmonic for warm broadcast bell tone
                double wave = 0.78 * Math.sin(angle) + 0.22 * Math.sin(2.0 * angle);

                double env = 1.0;
                if (i < attackSamples) {
                    env = (double) i / attackSamples;
                } else if (i > totalSamples - releaseSamples) {
                    env = (double) (totalSamples - i) / releaseSamples;
                }

                short sample = (short) (wave * env * volume * Short.MAX_VALUE);
                buf[2 * i] = (byte) (sample & 0xFF);
                buf[2 * i + 1] = (byte) ((sample >> 8) & 0xFF);
            }

            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format, buf.length);
                line.start();
                line.write(buf, 0, buf.length);
                line.drain();
            }
        } catch (Exception ignored) {
            // Audio device unavailable or muted at OS level
        }
    }
}
