package com.morkeriget.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ViewportEvent;

// Shakes the camera a little on big hits, slams and kills. add() puts in "trauma" from 0 to 1, which fades out by
// itself, and the shake is the trauma squared, so small hits barely move the camera and big ones really do. It follows
// Minecraft's "Screen Effect Scale" setting, so players who turn that down get less of it, or none. Client only
public final class CameraShake {
    private static final float FADE_PER_SECOND = 1.6f;
    private static final float MAX_YAW = 2.5f;
    private static final float MAX_PITCH = 2.5f;
    private static final float MAX_ROLL = 2.0f;

    private static float trauma;
    private static long lastNanos = System.nanoTime();

    private CameraShake() {
    }

    public static void add(float amount) {
        trauma = Mth.clamp(trauma + amount, 0, 1);
    }

    public static void reset() {
        trauma = 0;
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        long now = System.nanoTime();
        float seconds = Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        Minecraft minecraft = Minecraft.getInstance();
        if (trauma <= 0 || minecraft.isPaused()) {
            return;
        }
        trauma = Math.max(0, trauma - FADE_PER_SECOND * seconds);
        float shake = trauma * trauma * minecraft.options.screenEffectScale().get().floatValue();
        if (shake <= 0) {
            return;
        }
        // Waves of different speeds added up look like a random shake, but move smoothly from frame to frame
        float time = now / 1e9f;
        event.setYaw(event.getYaw() + MAX_YAW * shake * wave(time, 23, 0));
        event.setPitch(event.getPitch() + MAX_PITCH * shake * wave(time, 27, 1.7f));
        event.setRoll(event.getRoll() + MAX_ROLL * shake * wave(time, 19, 3.1f));
    }

    // A value from about -1 to 1
    private static float wave(float time, float speed, float offset) {
        return 0.6f * Mth.sin(time * speed + offset) + 0.4f * Mth.sin(time * speed * 2.3f + offset * 2);
    }
}
