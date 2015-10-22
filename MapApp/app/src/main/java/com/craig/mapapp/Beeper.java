package com.craig.mapapp;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.util.Pair;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by jarrad on 22/10/15.
 */
public class Beeper {
    private MediaPlayer mediaPlayer;
    private Activity activity;
    private Timer timer;

    private float volume = 1.0f;
    private float angle = 0;
    private float distance = 1;

    private static final long BASE_DELAY_MS = 500;
    private static final long DELAY_PER_M_MS = 100;

    private static boolean beeping = false;

    private class Beep extends TimerTask {

        @Override
        public void run() {
            float distanceVolume = volume / (distance+1);
            Pair<Float, Float> volumes = calculateVolume(distanceVolume, angle);
            Beeper.this.mediaPlayer.setVolume(volumes.first, volumes.second);
            Beeper.this.timer.cancel();
            if (Beeper.this.beeping) {
                Beeper.this.mediaPlayer.start();
                Beeper.this.timer = new Timer();
                Beeper.this.timer.schedule(new Beep(), Beeper.this.calculateBeepDelay());
            }
        }
    }

    private static final float invsqrt2 = (float)Math.sqrt(2)/2.0f;

    // angle is in radians!
    public Pair<Float, Float> calculateVolume(float baseVolume, float angle) {
        float volumeLeft = invsqrt2 * (float)(Math.cos(angle)+Math.sin(angle));
        float volumeRight = invsqrt2 * (float)(Math.cos(angle)-Math.sin(angle));
        return new Pair<Float, Float>(volumeLeft, volumeRight);
    }

    private long calculateBeepDelay() {
        return (long) (DELAY_PER_M_MS * this.distance + BASE_DELAY_MS);
    }

    public Beeper(Activity activity) {
        this.activity = activity;
        this.mediaPlayer = new MediaPlayer();
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        this.mediaPlayer.setAudioAttributes(attributes);
        //TODO: this.mediaPlayer.setDataSource();
    }

    public void setAngle(float newAngle) {
        this.angle = newAngle;
    }

    public void setDistance(float newDistance) {
        this.distance = newDistance;
    }


    public void startBeeping() {
        if (this.beeping) {
            return;
        }
        this.beeping = true;
        new Beep().run();
    }

    public void stopBeeping() {
        if (!this.beeping) {
            return;
        }

        this.beeping = false;
    }

}
