package io.github.com.quillraven.ui.view;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.github.com.quillraven.ui.model.GameViewModel;

public final class GameView extends View<GameViewModel> {
    private static final float HEART_SIZE = 16f;

    private final InfernalUi infernalUi;
    private final Table livesTable = new Table();
    private final Table resultToast = new Table();
    private Label arenaResultLabel;
    private float toastTimeRemaining;
    private Label directionLabel;
    private Table interactionBubble;
    private final Vector3 projectedPosition = new Vector3();
    private final Vector2 stagePosition = new Vector2();

    public GameView(Stage stage, Skin skin, GameViewModel viewModel, InfernalUi infernalUi) {
        super(stage, skin, viewModel);
        this.infernalUi = infernalUi;
        initializeUI();
        updateLives(viewModel.getLives());
    }

    @Override
    protected void setupUI() {
        setFillParent(true);
        top().left();

        Table status = new Table();
        status.add(livesTable).left().row();
        directionLabel = new Label("", skin, "small");
        directionLabel.setFontScale(0.23f);
        directionLabel.setColor(InfernalUi.GOLD);
        status.add(directionLabel).left().padTop(2f);
        add(status).top().left().pad(4f).row();

        interactionBubble = new Table();
        interactionBubble.setBackground(infernalUi.panel());
        interactionBubble.pad(4f, 6f, 4f, 6f);
        Label bubbleLabel = new Label("", skin, "small");
        bubbleLabel.setFontScale(0.28f);
        bubbleLabel.setColor(InfernalUi.PALE);
        bubbleLabel.setAlignment(Align.center);
        bubbleLabel.setWrap(true);
        interactionBubble.add(bubbleLabel).width(108f);
        interactionBubble.setVisible(false);
        addActor(interactionBubble);

        resultToast.setBackground(infernalUi.panel());
        resultToast.pad(4f, 7f, 4f, 7f);
        Label toastLabel = new Label("", skin, "small");
        toastLabel.setFontScale(0.28f);
        toastLabel.setColor(InfernalUi.PALE);
        resultToast.add(toastLabel);
        resultToast.pack();
        resultToast.setPosition((stage.getWidth() - resultToast.getWidth()) * 0.5f, 18f);
        resultToast.setVisible(false);
        addActor(resultToast);

        arenaResultLabel = new Label("", skin, "small");
        arenaResultLabel.setFontScale(0.24f);
        arenaResultLabel.setColor(InfernalUi.PALE);
        arenaResultLabel.setAlignment(Align.center);
        arenaResultLabel.setVisible(false);
        addActor(arenaResultLabel);
    }

    @Override
    protected void setupPropertyChanges() {
        viewModel.onPropertyChange(GameViewModel.LIVES, Integer.class, this::updateLives);
    }

    private void updateLives(int lives) {
        livesTable.clearChildren();
        for (int i = 0; i < lives; i++) {
            Image heart = new Image(infernalUi.heart());
            heart.setColor(InfernalUi.EMBER);
            heart.setSize(HEART_SIZE, HEART_SIZE);
            livesTable.add(heart).size(HEART_SIZE).padLeft(1f);
        }
    }

    public void setDuelActive(boolean active) {
        livesTable.setVisible(!active);
    }

    public void setInteractionBubble(String text, Vector2 worldPosition, Viewport worldViewport) {
        if (text == null || worldPosition == null) {
            interactionBubble.setVisible(false);
            return;
        }

        Label label = (Label) interactionBubble.getChild(0);
        label.setText(text);
        interactionBubble.pack();
        placeAtWorldPosition(interactionBubble, worldPosition, worldViewport);
        interactionBubble.setVisible(true);
    }

    public void setCenteredInteractionMessage(String text) {
        if (text == null) {
            interactionBubble.setVisible(false);
            return;
        }
        Label label = (Label) interactionBubble.getChild(0);
        label.setText(text);
        interactionBubble.pack();
        interactionBubble.setPosition(
            (stage.getWidth() - interactionBubble.getWidth()) * 0.5f,
            (stage.getHeight() - interactionBubble.getHeight()) * 0.5f
        );
        interactionBubble.setVisible(true);
    }

    public void setDirectionIndicator(String text) {
        directionLabel.setText(text == null ? "" : text);
    }

    public void showResultMessage(String text) {
        ((Label) resultToast.getChild(0)).setText(text);
        resultToast.pack();
        resultToast.setPosition((stage.getWidth() - resultToast.getWidth()) * 0.5f, 18f);
        toastTimeRemaining = 3f;
        resultToast.setVisible(true);
    }

    public void showArenaResult(String text) {
        arenaResultLabel.setText(text);
        arenaResultLabel.pack();
        arenaResultLabel.setPosition(
            (stage.getWidth() - arenaResultLabel.getWidth()) * 0.5f,
            stage.getHeight() - arenaResultLabel.getHeight() - 5f
        );
        arenaResultLabel.setVisible(true);
    }

    public void hideArenaResult() {
        arenaResultLabel.setVisible(false);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (!resultToast.isVisible()) return;
        toastTimeRemaining -= delta;
        if (toastTimeRemaining <= 0f) resultToast.setVisible(false);
    }

    private void placeAtWorldPosition(Table overlay, Vector2 worldPosition, Viewport worldViewport) {
        projectedPosition.set(worldPosition.x, worldPosition.y, 0f);
        worldViewport.project(projectedPosition);
        stagePosition.set(projectedPosition.x, projectedPosition.y);
        stage.getViewport().unproject(stagePosition);
        float x = stagePosition.x - overlay.getWidth() * 0.5f;
        float y = stagePosition.y + 5f;
        x = Math.max(2f, Math.min(x, stage.getWidth() - overlay.getWidth() - 2f));
        y = Math.max(2f, Math.min(y, stage.getHeight() - overlay.getHeight() - 2f));
        overlay.setPosition(x, y);
    }
}
