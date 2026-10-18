package io.github.com.peanut0k.system;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.com.peanut0k.GdxGame;
import io.github.com.peanut0k.component.CameraFollow;
import io.github.com.peanut0k.component.Transform;
import io.github.com.peanut0k.ldtk.LdtkMap;

public class CameraSystem extends IteratingSystem {
    private static final float CAM_OFFSET_Y = 1f; // make the camera look up 'X' additional tiles
    private static final float BASE_SMOOTHING_FACTOR = 0.4f;
    private static final float DEAD_ZONE_RATIO = 0.3f;

    private final OrthographicCamera camera;
    private final Vector2 targetPosition;
    private float smoothingFactor;
    private float mapW;
    private float mapH;

    public CameraSystem(OrthographicCamera camera) {
        super(Family.all(CameraFollow.class, Transform.class).get());
        this.camera = camera;
        this.targetPosition = new Vector2();
    }

    /**
     * Updates camera position with smooth following and boundary constraints.
     */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Transform transform = Transform.MAPPER.get(entity);
        calcDeadZoneTarget(transform.getPosition());

        // Apply smoothing using linear interpolation (LERP)
        float progress = 1f - (float) Math.exp(-smoothingFactor * deltaTime);
        float smoothedX = MathUtils.lerp(camera.position.x, this.targetPosition.x, progress);
        float smoothedY = MathUtils.lerp(camera.position.y, this.targetPosition.y, progress);
        camera.position.set(smoothedX, smoothedY, camera.position.z);
    }

    /**
     * Moves the camera only when the followed entity leaves the central dead zone.
     */
    private void calcDeadZoneTarget(Vector2 entityPosition) {
        float camHalfW = camera.viewportWidth * camera.zoom * 0.5f;
        float camHalfH = camera.viewportHeight * camera.zoom * 0.5f;
        float deadZoneHalfW = camHalfW * DEAD_ZONE_RATIO;
        float deadZoneHalfH = camHalfH * DEAD_ZONE_RATIO;

        float targetX = camera.position.x;
        float deltaX = entityPosition.x - targetX;
        if (Math.abs(deltaX) > deadZoneHalfW) {
            targetX += deltaX - Math.copySign(deadZoneHalfW, deltaX);
        }

        float targetY = camera.position.y;
        float deltaY = entityPosition.y + CAM_OFFSET_Y - targetY;
        if (Math.abs(deltaY) > deadZoneHalfH) {
            targetY += deltaY - Math.copySign(deadZoneHalfH, deltaY);
        }

        targetX = clampCameraAxis(targetX, mapW, camHalfW);
        targetY = clampCameraAxis(targetY, mapH, camHalfH);

        this.targetPosition.set(targetX, targetY);
    }

    private static float clampCameraAxis(float position, float mapSize, float cameraHalfSize) {
        if (mapSize <= cameraHalfSize * 2f) {
            return mapSize * 0.5f;
        }
        return MathUtils.clamp(position, cameraHalfSize, mapSize - cameraHalfSize);
    }

    /**
     * Sets up camera for a new map and positions it at the target entity.
     */
    public void setMap(LdtkMap map) {
        mapW = map.getWidth();
        mapH = map.getHeight();
        smoothingFactor = BASE_SMOOTHING_FACTOR * map.getGridSize() * GdxGame.UNIT_SCALE;

        Entity camEntity = getEntities().first();
        if (camEntity == null) {
            return;
        }

        calcDeadZoneTarget(Transform.MAPPER.get(camEntity).getPosition());
        camera.position.set(targetPosition.x, targetPosition.y, camera.position.z);
        camera.update();
    }
}
