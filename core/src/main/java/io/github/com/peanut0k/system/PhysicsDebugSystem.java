package io.github.com.peanut0k.system;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Disposable;

public final class PhysicsDebugSystem implements Disposable {
    private final Box2DDebugRenderer renderer = new Box2DDebugRenderer();
    private final World world;
    private final OrthographicCamera camera;
    private boolean enabled;

    public PhysicsDebugSystem(World world, OrthographicCamera camera) {
        this.world = world;
        this.camera = camera;
    }

    public void toggle() {
        enabled = !enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void render() {
        if (enabled) renderer.render(world, camera.combined);
    }

    @Override
    public void dispose() {
        renderer.dispose();
    }
}
