package io.github.com.peanut0k.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.gdx.math.Vector2;

public class PlayerProgression implements Component {
    public static final ComponentMapper<PlayerProgression> MAPPER =
        ComponentMapper.getFor(PlayerProgression.class);

    private int level = 1;
    private int experience;
    private int experienceAwarded;
    private float moveCooldown;
    private final Vector2 moveTarget = new Vector2();
    private boolean moving;

    public int getLevel() {
        return level;
    }

    public int getExperience() {
        return experience;
    }

    public int getExperienceToNextLevel() {
        return level * 3;
    }

    public int addExperience(int amount) {
        int awarded = Math.max(0, amount);
        experienceAwarded += awarded;
        experience += awarded;
        int levelsGained = 0;
        while (experience >= getExperienceToNextLevel()) {
            experience -= getExperienceToNextLevel();
            level++;
            levelsGained++;
        }
        return levelsGained;
    }

    public int getExperienceAwarded() {
        return experienceAwarded;
    }

    public boolean canMove() {
        return moveCooldown <= 0f;
    }

    public void startMove(float x, float y, float cooldown) {
        moveTarget.set(x, y);
        moveCooldown = cooldown;
        moving = true;
    }

    public Vector2 getMoveTarget() {
        return moveTarget;
    }

    public boolean isMoving() {
        return moving;
    }

    public void finishMove() {
        moving = false;
    }

    public void resetMovement() {
        moveCooldown = 0f;
        moveTarget.setZero();
        moving = false;
    }

    public void update(float deltaTime) {
        moveCooldown = Math.max(0f, moveCooldown - deltaTime);
    }
}
