package io.github.com.peanut0k.system;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import io.github.com.peanut0k.component.BoardMotion;
import io.github.com.peanut0k.component.Transform;

public final class BoardMotionSystem extends IteratingSystem {
    public BoardMotionSystem() {
        super(Family.all(BoardMotion.class, Transform.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        BoardMotion motion = BoardMotion.MAPPER.get(entity);
        float progress = motion.advance(deltaTime);
        float eased = progress * progress * (3f - 2f * progress);
        Transform.MAPPER.get(entity).getPosition()
            .set(motion.getStart()).lerp(motion.getTarget(), eased);
        if (progress >= 1f) entity.remove(BoardMotion.class);
    }
}
