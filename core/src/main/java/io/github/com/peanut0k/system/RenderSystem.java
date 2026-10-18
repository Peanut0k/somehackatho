package io.github.com.peanut0k.system;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.SortedIteratingSystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.github.com.peanut0k.component.Graphic;
import io.github.com.peanut0k.component.Ldtk;
import io.github.com.peanut0k.component.Transform;
import io.github.com.peanut0k.ldtk.LdtkMap;

import java.util.Comparator;

public class RenderSystem extends SortedIteratingSystem implements Disposable {
    private final Batch batch;
    private final OrthographicCamera camera;
    private final Viewport viewport;
    private LdtkMap map;

    public RenderSystem(Batch batch, Viewport viewport, OrthographicCamera camera) {
        super(
            Family.all(Transform.class, Graphic.class).get(),
            Comparator.comparing(Transform.MAPPER::get)
        );

        this.batch = batch;
        this.viewport = viewport;
        this.camera = camera;
    }

    /**
     * Renders the scene with background, entities, and foreground layers.
     */
    @Override
    public void update(float deltaTime) {
        viewport.apply();
        camera.update();
        batch.setProjectionMatrix(camera.combined);

        if (map != null) {
            Gdx.gl.glClearColor(map.getBackgroundColor().r, map.getBackgroundColor().g,
                map.getBackgroundColor().b, map.getBackgroundColor().a);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        }
        batch.begin();
        batch.setColor(Color.WHITE);
        if (map != null) {
            drawLayeredMap(map);
        } else {
            forceSort();
            super.update(deltaTime);
        }

        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawLayeredMap(LdtkMap map) {
        forceSort();
        // LDtk stores layers from front to back; render from back to front.
        for (int layerIndex = map.getLayerCount() - 1; layerIndex >= 0; layerIndex--) {
            drawMapLayerTiles(map, layerIndex);
            drawMapLayerEntities(layerIndex);
        }
        drawUnlayeredEntities();
    }

    private void drawMapLayerTiles(LdtkMap map, int layerIndex) {
        for (LdtkMap.Tile tile : map.getTiles()) {
            if (tile.layerIndex() == layerIndex) {
                batch.draw(tile.region(), tile.x(), tile.y(), tile.size(), tile.size());
            }
        }
    }

    private void drawMapLayerEntities(int layerIndex) {
        for (Entity entity : getEntities()) {
            Ldtk ldtk = Ldtk.MAPPER.get(entity);
            if (ldtk != null && ldtk.getLayerIndex() == layerIndex) {
                drawEntity(entity);
            }
        }
    }

    private void drawUnlayeredEntities() {
        for (Entity entity : getEntities()) {
            Ldtk ldtk = Ldtk.MAPPER.get(entity);
            if (ldtk == null || ldtk.getLayerIndex() < 0) {
                drawEntity(entity);
            }
        }
    }

    /**
     * Renders a single entity with its transform and graphic components.
     */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        drawEntity(entity);
    }

    private void drawEntity(Entity entity) {
        Transform transform = Transform.MAPPER.get(entity);
        Graphic graphic = Graphic.MAPPER.get(entity);
        if (graphic.getRegion() == null) {
            return;
        }

        Vector2 position = transform.getPosition();
        Vector2 scaling = transform.getScaling();
        Vector2 size = transform.getSize();
        batch.setColor(graphic.getColor());
        batch.draw(
            graphic.getRegion(),
            position.x - (1f - scaling.x) * size.x * 0.5f,
            position.y - (1f - scaling.y) * size.y * 0.5f,
            size.x * 0.5f, size.y * 0.5f,
            size.x, size.y,
            scaling.x, scaling.y,
            transform.getRotationDeg()
        );
    }

    /**
     * Sets up the map and organizes layers into background and foreground.
     */
    public void setMap(LdtkMap map) {
        this.map = map;
    }

    @Override
    public void dispose() {
    }
}
