package io.github.com.peanut0k.ui.view;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.github.com.peanut0k.system.ChessDuelAbility;
import io.github.com.peanut0k.ui.model.GameViewModel;

public final class GameView extends View<GameViewModel> {
    private static final float HEART_SIZE = 16f;
    private static final Color DISABLED_ABILITY_COLOR = new Color(0.42f, 0.39f, 0.40f, 1f);

    private final InfernalUi infernalUi;
    private final Runnable restartAction;
    private final Runnable quitAction;
    private final Table livesTable = new Table();
    private final Table resultToast = new Table();
    private final Table abilityBar = new Table();
    private final Table pauseOverlay = new Table();
    private final Table[] abilityButtons = new Table[ChessDuelAbility.values().length];
    private final Label[] abilityLabels = new Label[ChessDuelAbility.values().length];
    private Table controlsPanel;
    private Label controlsLabel;
    private String displayedControls;
    private Label puzzleMovesLabel;
    private Label arenaResultLabel;
    private float toastTimeRemaining;
    private Label directionLabel;
    private Table interactionBubble;
    private boolean duelActive;
    private boolean arenaActive;
    private boolean hintUnlocked;
    private boolean doubleMoveUnlocked;
    private final Vector3 projectedPosition = new Vector3();
    private final Vector2 stagePosition = new Vector2();

    public GameView(Stage stage, Skin skin, GameViewModel viewModel, InfernalUi infernalUi,
                    Runnable restartAction, Runnable quitAction) {
        super(stage, skin, viewModel);
        this.infernalUi = infernalUi;
        this.restartAction = restartAction;
        this.quitAction = quitAction;
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
        puzzleMovesLabel = new Label(viewModel.getPuzzleMoves(), skin, "small");
        puzzleMovesLabel.setFontScale(0.28f);
        puzzleMovesLabel.setColor(InfernalUi.GOLD);
        puzzleMovesLabel.setVisible(false);
        status.row();
        status.add(puzzleMovesLabel).left().padTop(2f);
        add(status).top().left().pad(4f).row();

        controlsPanel = new Table();
        controlsPanel.setBackground(infernalUi.panel());
        controlsPanel.pad(4f, 7f, 4f, 7f);
        controlsLabel = new Label(formatControls(viewModel.getControls()), skin, "small");
        displayedControls = viewModel.getControls();
        controlsLabel.setFontScale(0.24f);
        controlsLabel.setColor(InfernalUi.PALE);
        controlsLabel.setAlignment(Align.center);
        controlsLabel.setWrap(true);
        controlsPanel.add(controlsLabel).width(296f);
        controlsPanel.pack();
        positionControlsPanel();
        addActor(controlsPanel);

        abilityBar.setBackground(infernalUi.panel());
        abilityBar.pad(3f);
        ChessDuelAbility[] abilities = ChessDuelAbility.values();
        String[] labels = {"DOUBLE\nMOVE", "HINT", "UNDO"};
        for (int i = 0; i < abilities.length; i++) {
            Table button = new Table();
            button.setBackground(infernalUi.button());
            button.setTouchable(Touchable.enabled);
            Label label = new Label(labels[i], skin, "small");
            label.setFontScale(0.22f);
            label.setAlignment(Align.center);
            abilityLabels[i] = label;
            button.add(label).width(48f).height(23f);
            abilityButtons[i] = button;
        }
        abilityBar.setVisible(false);
        addActor(abilityBar);
        refreshAbilityBar();

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

        pauseOverlay.setFillParent(true);
        pauseOverlay.setTouchable(Touchable.enabled);
        pauseOverlay.center();
        Table pausePanel = new Table();
        pausePanel.setBackground(infernalUi.panel());
        pausePanel.pad(8f);
        Label pauseTitle = new Label("GAME MENU", skin, "small");
        pauseTitle.setFontScale(0.38f);
        pauseTitle.setColor(InfernalUi.GOLD);
        pausePanel.add(pauseTitle).padBottom(7f).row();
        TextButton restartButton = createPauseButton("Restart Game", restartAction);
        TextButton quitButton = createPauseButton("Quit to Desktop", quitAction);
        pausePanel.add(restartButton).width(112f).height(22f).padBottom(4f).row();
        pausePanel.add(quitButton).width(112f).height(22f);
        Label resumeHint = new Label("ESC to resume", skin, "small");
        resumeHint.setFontScale(0.22f);
        resumeHint.setColor(InfernalUi.PALE);
        pausePanel.row();
        pausePanel.add(resumeHint).padTop(5f);
        pauseOverlay.add(pausePanel);
        pauseOverlay.setVisible(false);
        addActor(pauseOverlay);
    }

    private TextButton createPauseButton(String text, Runnable action) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle(
            skin.get(TextButton.TextButtonStyle.class));
        style.up = infernalUi.button();
        style.down = infernalUi.selectedButton();
        style.over = infernalUi.selectedButton();
        TextButton button = new TextButton(text, style);
        button.getLabel().setFontScale(0.28f);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                action.run();
            }
        });
        return button;
    }

    @Override
    protected void setupPropertyChanges() {
        viewModel.onPropertyChange(GameViewModel.LIVES, Integer.class, this::updateLives);
        viewModel.onPropertyChange(GameViewModel.CONTROLS, String.class, this::updateControls);
        viewModel.onPropertyChange(GameViewModel.PUZZLE_MOVES, String.class, this::updatePuzzleMoves);
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
        if (duelActive == active) return;
        duelActive = active;
        livesTable.setVisible(!active);
        puzzleMovesLabel.setVisible(active);
        refreshAbilityBar();
    }

    private void updatePuzzleMoves(String moves) {
        puzzleMovesLabel.setText(moves);
    }

    public void setArenaActive(boolean active) {
        if (arenaActive == active) return;
        arenaActive = active;
        refreshAbilityBar();
    }

    public void updateAbilityState(boolean hintEarned, boolean hintAvailable,
                                   boolean doubleMoveEarned, boolean doubleMoveAvailable,
                                   boolean undoAvailable, boolean undoSpent) {
        hintUnlocked = hintEarned;
        doubleMoveUnlocked = doubleMoveEarned;
        boolean changed = setAbilityLabel(ChessDuelAbility.DOUBLE_MOVE,
            !doubleMoveEarned ? "DOUBLE\nLOCKED" : doubleMoveAvailable ? "DOUBLE\nMOVE" : "DOUBLE\nUSED",
            doubleMoveAvailable);
        changed |= setAbilityLabel(ChessDuelAbility.HINT,
            !hintEarned ? "HINT\nLOCKED" : hintAvailable ? "HINT" : "HINT\nUSED", hintAvailable);
        changed |= setAbilityLabel(ChessDuelAbility.UNDO,
            undoSpent ? "UNDO\nUSED" : "UNDO", undoAvailable);
        if (changed) refreshAbilityBar();
    }

    private void refreshAbilityBar() {
        boolean hasVisibleButton = false;
        abilityBar.clearChildren();
        for (ChessDuelAbility ability : ChessDuelAbility.values()) {
            boolean visible = duelActive
                ? ability == ChessDuelAbility.UNDO
                : arenaActive && switch (ability) {
                    case DOUBLE_MOVE -> doubleMoveUnlocked;
                    case HINT -> hintUnlocked;
                    case UNDO -> false;
                };
            abilityButtons[ability.ordinal()].setVisible(visible);
            if (!visible) continue;
            abilityBar.add(abilityButtons[ability.ordinal()]).width(50f).height(25f).row();
            hasVisibleButton = true;
        }
        abilityBar.pack();
        abilityBar.setVisible(hasVisibleButton);
        positionAbilityBar();
    }

    public ChessDuelAbility abilityAtScreenPosition(float screenX, float screenY) {
        if (!abilityBar.isVisible()) return null;
        Vector2 stageCoordinates = stage.getViewport().unproject(new Vector2(screenX, screenY));
        Vector2 localCoordinates = abilityBar.stageToLocalCoordinates(stageCoordinates);
        Actor hit = abilityBar.hit(localCoordinates.x, localCoordinates.y, true);
        while (hit != null && hit.getParent() != abilityBar) hit = hit.getParent();
        if (hit == null) return null;
        for (ChessDuelAbility ability : ChessDuelAbility.values()) {
            if (abilityButtons[ability.ordinal()] == hit) return ability;
        }
        return null;
    }

    private boolean setAbilityLabel(ChessDuelAbility ability, String text, boolean available) {
        Label label = abilityLabels[ability.ordinal()];
        Color color = available ? InfernalUi.GOLD : DISABLED_ABILITY_COLOR;
        if (label.getText().toString().equals(text) && label.getColor().equals(color)) return false;
        label.setText(text);
        label.setColor(color);
        return true;
    }

    private void updateControls(String controls) {
        if (!controls.equals(displayedControls)) controlsPanel.setVisible(true);
        displayedControls = controls;
        controlsLabel.setText(formatControls(controls));
        controlsPanel.pack();
        positionControlsPanel();
    }

    private static String formatControls(String controls) {
        return controls + "\nCLICK THIS GUIDE TO HIDE";
    }

    public boolean dismissControlsAtScreenPosition(float screenX, float screenY) {
        if (!controlsPanel.isVisible()) return false;
        Vector2 stageCoordinates = stage.getViewport().unproject(new Vector2(screenX, screenY));
        if (controlsPanel.hit(stageCoordinates.x, stageCoordinates.y, true) == null) return false;
        controlsPanel.setVisible(false);
        return true;
    }

    private void positionControlsPanel() {
        controlsPanel.setPosition(
            (stage.getWidth() - controlsPanel.getWidth()) * 0.5f,
            4f
        );
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
        resultToast.setPosition(
            (stage.getWidth() - resultToast.getWidth()) * 0.5f,
            controlsPanel.getY() + controlsPanel.getHeight() + 6f
        );
        toastTimeRemaining = 3f;
        resultToast.setVisible(true);
    }

    public void showArenaResult(String text) {
        arenaResultLabel.setFontScale(0.24f);
        arenaResultLabel.setText(text);
        arenaResultLabel.setColor(InfernalUi.PALE);
        arenaResultLabel.pack();
        arenaResultLabel.setPosition(
            (stage.getWidth() - arenaResultLabel.getWidth()) * 0.5f,
            stage.getHeight() - arenaResultLabel.getHeight() - 5f
        );
        arenaResultLabel.setVisible(true);
    }

    public void showGameOver() {
        showGameOver(null);
    }

    public void showGameOver(String reason) {
        arenaResultLabel.setFontScale(0.24f);
        arenaResultLabel.setText(reason == null || reason.isBlank()
            ? "GAME OVER\nPress any key"
            : reason + "\nPress any key");
        arenaResultLabel.setColor(Color.RED);
        arenaResultLabel.pack();
        arenaResultLabel.setPosition(
            (stage.getWidth() - arenaResultLabel.getWidth()) * 0.5f,
            (stage.getHeight() - arenaResultLabel.getHeight()) * 0.5f
        );
        arenaResultLabel.setVisible(true);
    }

    public void hideArenaResult() {
        arenaResultLabel.setVisible(false);
    }

    public void setPauseMenuVisible(boolean visible) {
        pauseOverlay.setVisible(visible);
    }

    public boolean isPauseMenuVisible() {
        return pauseOverlay.isVisible();
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

    public void resize() {
        positionControlsPanel();
        positionAbilityBar();
        pauseOverlay.setFillParent(true);
        if (resultToast.isVisible()) {
            resultToast.setPosition(
                (stage.getWidth() - resultToast.getWidth()) * 0.5f,
                controlsPanel.getY() + controlsPanel.getHeight() + 6f
            );
        }
    }

    private void positionAbilityBar() {
        abilityBar.setPosition(stage.getWidth() - abilityBar.getWidth() - 4f,
            (stage.getHeight() - abilityBar.getHeight()) * 0.5f);
    }
}
