package io.github.com.quillraven.system;

import box2dLight.RayHandler;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Disposable;
import io.github.com.quillraven.component.Lighting;
import io.github.com.quillraven.component.Transform;

public final class LightingSystem extends IteratingSystem implements EntityListener, Disposable {
    private final RayHandler rayHandler;
    private final OrthographicCamera camera;

    public LightingSystem(World world, OrthographicCamera camera) {
        super(Family.all(Lighting.class, Transform.class).get());
        this.camera = camera;
        this.rayHandler = new RayHandler(world);
        this.rayHandler.setAmbientLight(LightingSettings.AMBIENT_COLOR);
        this.rayHandler.setShadows(LightingSettings.SHADOWS_ENABLED);
        this.rayHandler.setBlurNum(LightingSettings.BLUR_PASSES);
        this.rayHandler.setBlur(LightingSettings.BLUR_PASSES > 0);
        this.rayHandler.setCulling(true);
        RayHandler.setGammaCorrection(LightingSettings.GAMMA_CORRECTION_ENABLED);
        RayHandler.useDiffuseLight(LightingSettings.DIFFUSE_LIGHT_ENABLED);
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        engine.addEntityListener(getFamily(), this);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        engine.removeEntityListener(this);
        super.removedFromEngine(engine);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Lighting lighting = Lighting.MAPPER.get(entity);
        if (!lighting.followsTransform()) return;

        Transform transform = Transform.MAPPER.get(entity);
        Vector2 position = transform.getPosition();
        Vector2 size = transform.getSize();
        lighting.getLight().setPosition(position.x + size.x * 0.5f, position.y + size.y * 0.5f);
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        rayHandler.setCombinedMatrix(camera);
        rayHandler.updateAndRender();
    }

    public void resize(int width, int height) {
        rayHandler.resizeFBO(width, height);
    }

    public RayHandler getRayHandler() {
        return rayHandler;
    }

    @Override
    public void entityAdded(Entity entity) {
    }

    @Override
    public void entityRemoved(Entity entity) {
        Lighting lighting = Lighting.MAPPER.get(entity);
        if (lighting != null) lighting.getLight().remove();
    }

    @Override
    public void dispose() {
        rayHandler.dispose();
    }
}
