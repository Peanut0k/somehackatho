package io.github.com.quillraven.ui.view;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.com.quillraven.ui.model.GameViewModel;

public final class GameView extends View<GameViewModel> {
    private final InfernalUi infernalUi;
    private Label messageLabel;
    private Label predictionLabel;

    public GameView(Stage stage, Skin skin, GameViewModel viewModel, InfernalUi infernalUi) {
        super(stage, skin, viewModel);
        this.infernalUi = infernalUi;
        initializeUI();
    }

    @Override
    protected void setupPropertyChanges() {
        viewModel.onPropertyChange(GameViewModel.GAME_MESSAGE, String.class, messageLabel::setText);
        viewModel.onPropertyChange(GameViewModel.ENEMY_PREDICTION, String.class, predictionLabel::setText);
    }

    @Override
    protected void setupUI() {
        setFillParent(true);
        top().left();

        Table panel = new Table();
        panel.setBackground(infernalUi.panel());
        panel.pad(2.5f, 3.5f, 2.5f, 3.5f);
        Label title = new Label("PAWN WORLD", skin, "small");
        title.setFontScale(0.3f);
        title.setColor(InfernalUi.GOLD);
        panel.add(title).left().row();

        messageLabel = new Label("", skin, "small");
        messageLabel.setFontScale(0.225f);
        messageLabel.setColor(InfernalUi.EMBER);
        messageLabel.setText(viewModel.getGameMessage());
        panel.add(messageLabel).left().padTop(1f).row();

        predictionLabel = new Label(viewModel.getEnemyPrediction(), skin, "small");
        predictionLabel.setFontScale(0.2f);
        predictionLabel.setColor(InfernalUi.GOLD);
        panel.add(predictionLabel).left().padTop(1f).row();

        Label controls = new Label("ARENA: A/D TURN, W/S MOVE\nQ/E CAPTURE DIAGONALLY", skin, "small");
        controls.setFontScale(0.2f);
        controls.setColor(InfernalUi.PALE);
        panel.add(controls).left().padTop(1.5f);

        add(panel).left().top().pad(2.5f);
    }
}
