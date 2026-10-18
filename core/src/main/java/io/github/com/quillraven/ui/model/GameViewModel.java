package io.github.com.quillraven.ui.model;

import io.github.com.quillraven.GdxGame;

public final class GameViewModel extends ViewModel {
    public static final String GAME_MESSAGE = "gameMessage";
    public static final String ENEMY_PREDICTION = "enemyPrediction";
    public static final String LIVES = "lives";
    public static final String CONTROLS = "controls";

    private String gameMessage = "Explore the hub, challenge chess players, and find the pawn portal.";
    private String enemyPrediction = "Enemy move: enter the arena";
    private String controls = "WASD MOVE  |  E INTERACT  |  CLICK CHALLENGER";
    private int lives = 2;

    public GameViewModel(GdxGame game) {
        super(game);
    }

    public void setGameMessage(String gameMessage) {
        if (this.gameMessage.equals(gameMessage)) return;
        propertyChangeSupport.firePropertyChange(GAME_MESSAGE, this.gameMessage, gameMessage);
        this.gameMessage = gameMessage;
    }

    public String getGameMessage() {
        return gameMessage;
    }

    public void setEnemyPrediction(String enemyPrediction) {
        if (this.enemyPrediction.equals(enemyPrediction)) return;
        propertyChangeSupport.firePropertyChange(ENEMY_PREDICTION, this.enemyPrediction, enemyPrediction);
        this.enemyPrediction = enemyPrediction;
    }

    public String getEnemyPrediction() {
        return enemyPrediction;
    }

    public void setLives(int lives) {
        if (this.lives == lives) return;
        propertyChangeSupport.firePropertyChange(LIVES, this.lives, lives);
        this.lives = lives;
    }

    public int getLives() {
        return lives;
    }

    public void setControls(String controls) {
        if (this.controls.equals(controls)) return;
        propertyChangeSupport.firePropertyChange(CONTROLS, this.controls, controls);
        this.controls = controls;
    }

    public String getControls() {
        return controls;
    }
}
