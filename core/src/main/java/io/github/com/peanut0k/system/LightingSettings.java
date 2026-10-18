package io.github.com.peanut0k.system;

import com.badlogic.gdx.graphics.Color;

public final class LightingSettings {
    public static final Color AMBIENT_COLOR = new Color(0.11f, 0.09f, 0.16f, 1f);
    public static final boolean SHADOWS_ENABLED = false;
    public static final int BLUR_PASSES = 2;
    public static final boolean GAMMA_CORRECTION_ENABLED = true;
    public static final boolean DIFFUSE_LIGHT_ENABLED = true;

    private LightingSettings() {
    }
}
