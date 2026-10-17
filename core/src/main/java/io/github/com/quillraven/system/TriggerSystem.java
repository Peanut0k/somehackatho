package io.github.com.quillraven.system;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import io.github.com.quillraven.component.Trigger;

public final class TriggerSystem extends IteratingSystem {
    private final Runnable onEnterPawnArena;

    public TriggerSystem(Runnable onEnterPawnArena) {
        super(Family.all(Trigger.class).get());
        this.onEnterPawnArena = onEnterPawnArena;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Trigger trigger = Trigger.MAPPER.get(entity);
        if (trigger.getTriggeringEntity() == null) return;
        if ("teleportt".equalsIgnoreCase(trigger.getName())) onEnterPawnArena.run();
        trigger.setTriggeringEntity(null);
    }
}
