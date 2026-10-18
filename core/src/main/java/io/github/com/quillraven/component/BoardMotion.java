package io.github.com.quillraven.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;

public final class BoardMotion implements Component {
    public static final ComponentMapper<BoardMotion> MAPPER = ComponentMapper.getFor(BoardMotion.class);
    public static final float DURATION = 0.24f;

    private final Vector2 start;
    private final Vector2 target;
    private float elapsed;

    public BoardMotion(Vector2 start, Vector2 target) {
        this.start = start.cpy();
        this.target = target.cpy();
    }

    public static void move(Entity entity, Vector2 target) {
        Transform transform = Transform.MAPPER.get(entity);
        Vector2 start = transform.getPosition().cpy();
        if (start.epsilonEquals(target, 0.001f)) return;

        BoardMotion motion = new BoardMotion(start, target);
        entity.add(motion);
        transform.getPosition().set(target);

        Physic physic = Physic.MAPPER.get(entity);
        if (physic != null) {
            Body body = physic.getBody();
            body.setTransform(target, body.getAngle());
            body.setLinearVelocity(0f, 0f);
            body.setAngularVelocity(0f);
            physic.getPrevPosition().set(target);
        }
    }

    public Vector2 getStart() {
        return start;
    }

    public Vector2 getTarget() {
        return target;
    }

    public float advance(float deltaTime) {
        elapsed = Math.min(DURATION, elapsed + deltaTime);
        return elapsed / DURATION;
    }
}
