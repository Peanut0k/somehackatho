package io.github.com.quillraven.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.github.com.quillraven.GdxGame;
import io.github.com.quillraven.asset.AssetService;
import io.github.com.quillraven.asset.AtlasAsset;
import io.github.com.quillraven.asset.SkinAsset;

public class LoadingScreen extends ScreenAdapter {
    private static final float VIEW_WIDTH = 320f;
    private static final float VIEW_HEIGHT = 180f;
    private static final Color BACKGROUND_COLOR = new Color(0.035f, 0.045f, 0.055f, 1f);
    private static final Color BAR_BACKGROUND_COLOR = new Color(0.16f, 0.18f, 0.19f, 1f);
    private static final Color BAR_COLOR = new Color(0.85f, 0.68f, 0.34f, 1f);

    private final GdxGame game;
    private final AssetService assetService;
    private final Viewport viewport = new FitViewport(VIEW_WIDTH, VIEW_HEIGHT);
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout titleLayout = new GlyphLayout(font, "LOADING...");
    private final GlyphLayout statusLayout = new GlyphLayout();
    private int lastPercentage = -1;
    private String percentageText = "0%";
    private boolean assetsReady;

    public LoadingScreen(GdxGame game) {
        this.game = game;
        this.assetService = game.getAssetService();
    }

    /**
     * Queues all required assets for loading.
     */
    @Override
    public void show() {
        for (AtlasAsset atlasAsset : AtlasAsset.values()) {
            assetService.queue(atlasAsset);
        }
        assetService.queue(SkinAsset.DEFAULT);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    /**
     * Loads the required assets while keeping the loading screen visible.
     */
    @Override
    public void render(float delta) {
        if (assetsReady) {
            Gdx.app.debug("LoadingScreen", "Finished loading assets");
            game.addScreen(new GameScreen(game));
            game.removeScreen(this);
            dispose();
            game.setScreen(GameScreen.class);
            return;
        }

        boolean loaded = assetService.update();
        drawLoadingScreen(assetService.getProgress());
        assetsReady = loaded;
    }

    private void drawLoadingScreen(float progress) {
        viewport.apply();
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(BACKGROUND_COLOR);
        shapeRenderer.rect(0f, 0f, VIEW_WIDTH, VIEW_HEIGHT);

        float barX = 105f;
        float barY = 78f;
        float barWidth = 110f;
        float barHeight = 7f;
        shapeRenderer.setColor(BAR_BACKGROUND_COLOR);
        shapeRenderer.rect(barX, barY, barWidth, barHeight);
        shapeRenderer.setColor(BAR_COLOR);
        shapeRenderer.rect(barX, barY, barWidth * Math.max(0f, Math.min(1f, progress)), barHeight);
        shapeRenderer.end();

        game.getBatch().setProjectionMatrix(viewport.getCamera().combined);
        game.getBatch().begin();
        game.getBatch().setColor(Color.WHITE);
        font.setColor(Color.WHITE);
        font.draw(game.getBatch(), titleLayout, (VIEW_WIDTH - titleLayout.width) * 0.5f, 103f);
        int percentage = (int) (progress * 100f);
        if (percentage != lastPercentage) {
            lastPercentage = percentage;
            percentageText = percentage + "%";
            statusLayout.setText(font, percentageText);
        }
        font.draw(game.getBatch(), statusLayout, (VIEW_WIDTH - statusLayout.width) * 0.5f, 65f);
        game.getBatch().end();
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        font.dispose();
    }
}
