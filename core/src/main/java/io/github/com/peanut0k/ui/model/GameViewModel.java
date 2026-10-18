package io.github.com.peanut0k.ui.model;

import io.github.com.peanut0k.GdxGame;

public final class GameViewModel extends ViewModel {
    public static final String GAME_MESSAGE = "gameMessage";
    public static final String ENEMY_PREDICTION = "enemyPrediction";
    public static final String LIVES = "lives";
    public static final String CONTROLS = "controls";
    public static final String PUZZLE_MOVES = "puzzleMoves";

    private String gameMessage = "Explore the hub, challenge chess players, and find the pawn portal.";
    private String enemyPrediction = "Enemy move: enter the arena";
    private String controls = "HUB  |  WASD / ARROWS MOVE  |  SHIFT SPRINT\nE CHALLENGE OR ENTER ARENA  |  CLICK A DUELIST";
    private String puzzleMoves = "";
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

    public void setPuzzleMoves(int remaining, int total) {
        String value = "MOVES LEFT: " + remaining + " / " + total;
        if (puzzleMoves.equals(value)) return;
        propertyChangeSupport.firePropertyChange(PUZZLE_MOVES, puzzleMoves, value);
        puzzleMoves = value;
    }

    public String getPuzzleMoves() {
        return puzzleMoves;
    }
}
