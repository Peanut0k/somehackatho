package io.github.com.quillraven.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import io.github.com.quillraven.GdxGame;

/**
 * Launches the desktop (LWJGL3) application.
 */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
        createApplication();
    }

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new GdxGame(), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("mystictutorial");
        configuration.useVsync(false);
        configuration.setForegroundFPS(60);
        configuration.setIdleFPS(30);
        configuration.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        //configuration.setWindowedMode((int) (GdxGame.WORLD_WIDTH * 70), (int) (GdxGame.WORLD_HEIGHT * 70));
        // You can change these files; they are in lwjgl3/src/main/resources/ .
        // They can also be loaded from the root of assets/ .
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}
