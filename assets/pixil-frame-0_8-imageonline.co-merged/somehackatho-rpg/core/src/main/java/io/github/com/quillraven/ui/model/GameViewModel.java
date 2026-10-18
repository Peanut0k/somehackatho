package io.github.com.quillraven.ui.model;

import io.github.com.quillraven.GdxGame;

public final class GameViewModel extends ViewModel {
    public static final String GAME_MESSAGE = "gameMessage";
    public static final String ENEMY_PREDICTION = "enemyPrediction";
    public static final String LIVES = "lives";
    public static final String RPG_STATUS = "rpgStatus";

    private String gameMessage = "Talk to the villager and explore the forest.";
    private String enemyPrediction = "Enemy move: enter the arena";
    private String rpgStatus = "LV 1  ·  XP 0/3  ·  Souls 0";
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

    public void setRpgStatus(String rpgStatus) {
        if (this.rpgStatus.equals(rpgStatus)) return;
        propertyChangeSupport.firePropertyChange(RPG_STATUS, this.rpgStatus, rpgStatus);
        this.rpgStatus = rpgStatus;
    }

    public String getRpgStatus() {
        return rpgStatus;
    }
}
