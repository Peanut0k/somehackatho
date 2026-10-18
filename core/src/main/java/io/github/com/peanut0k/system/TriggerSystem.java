package io.github.com.peanut0k.system;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import io.github.com.peanut0k.component.ChessChallenger;
import io.github.com.peanut0k.component.Trigger;

import java.util.function.Consumer;

public final class TriggerSystem extends IteratingSystem {
    private final Runnable onEnterPawnArena;
    private final Consumer<Entity> onChallengerChanged;
    private final Consumer<Entity> onArenaPortalChanged;
    private Entity activeChallenger;
    private Entity activeArenaPortal;

    public TriggerSystem(Runnable onEnterPawnArena, Consumer<Entity> onChallengerChanged,
                         Consumer<Entity> onArenaPortalChanged) {
        super(Family.all(Trigger.class).get());
        this.onEnterPawnArena = onEnterPawnArena;
        this.onChallengerChanged = onChallengerChanged;
        this.onArenaPortalChanged = onArenaPortalChanged;
    }

    @Override
    public void update(float deltaTime) {
        Entity challenger = null;
        Entity arenaPortal = null;
        for (Entity entity : getEntities()) {
            Trigger trigger = Trigger.MAPPER.get(entity);
            ChessChallenger chessChallenger = ChessChallenger.MAPPER.get(entity);
            if (trigger.getTriggeringEntity() != null) {
                if (chessChallenger != null && !chessChallenger.isDefeated() && challenger == null) {
                    challenger = entity;
                }
                if (arenaPortal == null && "teleportt".equalsIgnoreCase(trigger.getName())) {
                    arenaPortal = entity;
                }
            }
        }
        if (challenger != activeChallenger) {
            activeChallenger = challenger;
            onChallengerChanged.accept(challenger);
        }
        if (arenaPortal != activeArenaPortal) {
            activeArenaPortal = arenaPortal;
            onArenaPortalChanged.accept(arenaPortal);
        }
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
    }

    public void interactWithArenaPortal() {
        if (activeArenaPortal != null) onEnterPawnArena.run();
    }
}
