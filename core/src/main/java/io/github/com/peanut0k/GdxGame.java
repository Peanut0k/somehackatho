package io.github.com.peanut0k;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.FPSLogger;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.github.com.peanut0k.asset.AssetService;
import io.github.com.peanut0k.audio.AudioService;
import io.github.com.peanut0k.screen.GameScreen;
import io.github.com.peanut0k.screen.LoadingScreen;

import java.util.HashMap;
import java.util.Map;

public class GdxGame extends Game {
    public static final float WORLD_HEIGHT = 10f;
    public static final float WORLD_WIDTH = 16f;
    public static final float UNIT_SCALE = 1f;
    public static final float CAMERA_ZOOM = 20f;
    private static final String PREFERENCES_NAME = "the-last-square";
    private static final String OPPONENT_ELO_KEY = "opponentElo";
    private static final int DEFAULT_OPPONENT_ELO = 2000;

    private Batch batch;
    private AssetService assetService;
    private AudioService audioService;
    private OrthographicCamera camera;
    private Viewport viewport;
    private InputMultiplexer inputMultiplexer;
    private final FPSLogger fpsLogger = new FPSLogger();
    private Preferences preferences;

    private final Map<Class<? extends Screen>, Screen> screenCache = new HashMap<>();

    @Override
    public void create() {
        preferences = Gdx.app.getPreferences(PREFERENCES_NAME);
        inputMultiplexer = new InputMultiplexer();
        Gdx.input.setInputProcessor(inputMultiplexer);

        batch = new SpriteBatch();
        assetService = new AssetService(new InternalFileHandleResolver());
        audioService = new AudioService(assetService);
        camera = new OrthographicCamera();
        camera.zoom = CAMERA_ZOOM;
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);

        addScreen(new LoadingScreen(this));
        setScreen(LoadingScreen.class);
    }

    public void addScreen(Screen screen) {
        screenCache.put(screen.getClass(), screen);
    }

    public void setScreen(Class<? extends Screen> screenClass) {
        Screen screen = screenCache.get(screenClass);
        if (screen == null) {
            throw new GdxRuntimeException("Screen " + screenClass.getSimpleName() + " not found in cache");
        }
        super.setScreen(screen);
    }

    public <T extends Screen> T getScreenFor(Class<T> screenClass) {
        Screen screen = screenCache.get(screenClass);
        if (screen == null) {
            throw new GdxRuntimeException("Screen " + screenClass.getSimpleName() + " not found in cache");
        }
        return screenClass.cast(screen);
    }

    public void removeScreen(Screen screen) {
        screenCache.remove(screen.getClass());
    }

    public void restartGame() {
        Screen previousGame = screenCache.get(GameScreen.class);
        GameScreen replacement = new GameScreen(this);
        addScreen(replacement);
        setScreen(GameScreen.class);
        if (previousGame != null) previousGame.dispose();
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        super.render();
        fpsLogger.log();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        super.resize(width, height);
    }

    /**
     * Cleans up all game resources and services.
     */
    @Override
    public void dispose() {
        for (Screen screen : screenCache.values()) {
            screen.dispose();
        }
        screenCache.clear();

        batch.dispose();
        assetService.debugDiagnostics();
        assetService.dispose();
    }

    public Batch getBatch() {
        return batch;
    }

    public AssetService getAssetService() {
        return assetService;
    }

    public OrthographicCamera getCamera() {
        return camera;
    }

    public Viewport getViewport() {
        return viewport;
    }

    public void setInputProcessors(InputProcessor... processors) {
        inputMultiplexer.clear();
        if (processors == null) return;

        for (InputProcessor processor : processors) {
            inputMultiplexer.addProcessor(processor);
        }
    }

    public AudioService getAudioService() {
        return audioService;
    }

    public int getOpponentElo() {
        return preferences.getInteger(OPPONENT_ELO_KEY, DEFAULT_OPPONENT_ELO);
    }

    public void setOpponentElo(int elo) {
        if (elo < 400 || elo > 2000 || elo % 100 != 0) {
            throw new IllegalArgumentException("Opponent Elo must be a multiple of 100 from 400 to 2000");
        }
        preferences.putInteger(OPPONENT_ELO_KEY, elo);
        preferences.flush();
    }
}
