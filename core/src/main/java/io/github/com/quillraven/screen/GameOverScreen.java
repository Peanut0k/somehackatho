package io.github.com.quillraven.screen;

import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.github.com.quillraven.GdxGame;
import io.github.com.quillraven.asset.MusicAsset;
import io.github.com.quillraven.asset.SkinAsset;
import io.github.com.quillraven.input.KeyboardController;
import io.github.com.quillraven.input.UiControllerState;
import io.github.com.quillraven.input.UiEvent;
import io.github.com.quillraven.ui.view.InfernalUi;

public class GameOverScreen extends ScreenAdapter {
    private final GdxGame game;
    private final Stage stage;
    private final Skin skin;
    private final Viewport viewport;
    private final KeyboardController keyboardController;
    private final InfernalUi infernalUi;
    private boolean victory;
    private int selectedOption;
    private TextButton retryButton;
    private TextButton menuButton;

    public GameOverScreen(GdxGame game) {
        this.game = game;
        this.viewport = new FitViewport(320f, 180f);
        this.stage = new Stage(viewport, game.getBatch());
        this.skin = game.getAssetService().get(SkinAsset.DEFAULT);
        this.keyboardController = new KeyboardController(UiControllerState.class, null, stage);
        this.infernalUi = new InfernalUi();
        stage.addListener(this::handleUiEvent);
    }

    public void setVictory(boolean victory) {
        this.victory = victory;
    }

    @Override
    public void show() {
        game.setInputProcessors(stage, keyboardController);
        keyboardController.setActiveState(UiControllerState.class);
        game.getAudioService().playMusic(MusicAsset.MENU);
        stage.addActor(infernalUi.createBackdrop());
        buildView();
    }

    private void buildView() {
        Table root = new Table();
        root.setFillParent(true);
        root.align(Align.center);
        Table panel = new Table();
        panel.setBackground(infernalUi.panel());
        panel.pad(8f);

        Label title = new Label(victory ? "RUIN CHALLENGE COMPLETE" : "GAME OVER", skin);
        title.setFontScale(0.65f);
        title.setColor(victory ? new Color(0.91f, 0.78f, 0.43f, 1f) : new Color(0.85f, 0.38f, 0.34f, 1f));
        panel.add(title).padBottom(4f).row();

        Label subtitle = new Label(
            victory ? "Challenge completed." : "You ran out of lives.",
            skin, "small");
        subtitle.setFontScale(0.7f);
        subtitle.setColor(skin.getColor("white"));
        panel.add(subtitle).padBottom(6f).row();

        retryButton = new TextButton("Retry", skin);
        menuButton = new TextButton("Menu", skin);
        styleButton(retryButton);
        styleButton(menuButton);
        panel.add(retryButton).width(82f).height(18f).padRight(4f);
        panel.add(menuButton).width(82f).height(18f);

        retryButton.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                retry();
            }
        });
        menuButton.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                game.setScreen(MenuScreen.class);
            }
        });
        root.add(panel);
        stage.addActor(root);
        selectedOption = 0;
        updateSelection();
    }

    private boolean handleUiEvent(Event event) {
        if (!(event instanceof UiEvent uiEvent)) return false;
        switch (uiEvent.getCommand()) {
            case UP, DOWN, LEFT, RIGHT -> {
                selectedOption = 1 - selectedOption;
                updateSelection();
            }
            case SELECT -> activateSelection();
            case CANCEL -> game.setScreen(MenuScreen.class);
        }
        return true;
    }

    private void updateSelection() {
        retryButton.getStyle().up = selectedOption == 0
            ? infernalUi.selectedButton() : infernalUi.button();
        menuButton.getStyle().up = selectedOption == 1
            ? infernalUi.selectedButton() : infernalUi.button();
        retryButton.getLabel().setColor(selectedOption == 0
            ? InfernalUi.GOLD : InfernalUi.PALE);
        menuButton.getLabel().setColor(selectedOption == 1
            ? InfernalUi.GOLD : InfernalUi.PALE);
    }

    private void styleButton(TextButton button) {
        com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle style =
            new com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle(button.getStyle());
        style.up = infernalUi.button();
        style.down = infernalUi.selectedButton();
        button.setStyle(style);
        button.getLabel().setFontScale(0.8f);
    }

    private void activateSelection() {
        if (selectedOption == 0) retry();
        else game.setScreen(MenuScreen.class);
    }

    private void retry() {
        game.restartGame();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void render(float delta) {
        viewport.apply();
        stage.getBatch().setColor(Color.WHITE);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void hide() {
        stage.clear();
    }

    @Override
    public void dispose() {
        stage.dispose();
        infernalUi.dispose();
    }
}
