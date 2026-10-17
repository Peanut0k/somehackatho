package io.github.com.quillraven.ui.model;

import io.github.com.quillraven.GdxGame;

public final class GameViewModel extends ViewModel {
    public static final String GAME_MESSAGE = "gameMessage";
    public static final String ENEMY_PREDICTION = "enemyPrediction";

    private String gameMessage = "Explore and find the pawn arena.";
    private String enemyPrediction = "Enemy move: enter the arena";

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
}
