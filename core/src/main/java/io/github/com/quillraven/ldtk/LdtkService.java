package io.github.com.quillraven.ldtk;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import io.github.com.quillraven.GdxGame;
import io.github.com.quillraven.asset.MapAsset;

import java.util.function.Consumer;

public final class LdtkService implements Disposable {
    private final World physicsWorld;
    private LdtkMap currentMap;
    private Consumer<LdtkMap> mapChangeConsumer;
    private Consumer<LdtkMap.EntityInstance> loadEntityConsumer;

    public LdtkService(World physicsWorld) {
        this.physicsWorld = physicsWorld;
    }

    public LdtkMap loadMap(MapAsset asset) {
        return LdtkMap.load(asset);
    }

    public LdtkMap loadMap(MapAsset asset, String levelIdentifier) {
        return LdtkMap.load(asset, levelIdentifier);
    }

    public void setMap(LdtkMap map) {
        if (currentMap != null) {
            currentMap.dispose();
            Array<Body> bodies = new Array<>();
            physicsWorld.getBodies(bodies);
            for (Body body : bodies) {
                if ("environment".equals(body.getUserData())) physicsWorld.destroyBody(body);
            }
        }
        currentMap = map;
        createIntGridCollision(map);
        createMapBoundary(map);
        if (loadEntityConsumer != null) {
            for (LdtkMap.EntityInstance entity : map.getEntities()) loadEntityConsumer.accept(entity);
        }
        if (mapChangeConsumer != null) mapChangeConsumer.accept(map);
    }

    private void createIntGridCollision(LdtkMap map) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        Body body = physicsWorld.createBody(bodyDef);
        body.setUserData("environment");
        float tileSize = map.getGridSize() * GdxGame.UNIT_SCALE;
        for (int x = 0; x < map.getPixelWidth() / map.getGridSize(); x++) {
            for (int y = 0; y < map.getPixelHeight() / map.getGridSize(); y++) {
                if (!map.isSolid(x, y)) continue;
                PolygonShape shape = new PolygonShape();
                float centerX = (x + 0.5f) * tileSize;
                float centerY = map.getHeight() - (y + 0.5f) * tileSize;
                shape.setAsBox(tileSize * 0.5f, tileSize * 0.5f, new Vector2(centerX, centerY), 0f);
                body.createFixture(shape, 0f).setFriction(0f);
                shape.dispose();
            }
        }
    }

    private void createMapBoundary(LdtkMap map) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        Body body = physicsWorld.createBody(bodyDef);
        body.setUserData("environment");
        float halfWidth = map.getWidth() * 0.5f;
        float halfHeight = map.getHeight() * 0.5f;
        float thickness = 0.5f;
        addBoundaryFixture(body, thickness, halfHeight, -thickness, halfHeight);
        addBoundaryFixture(body, thickness, halfHeight, map.getWidth() + thickness, halfHeight);
        addBoundaryFixture(body, halfWidth, thickness, halfWidth, -thickness);
        addBoundaryFixture(body, halfWidth, thickness, halfWidth, map.getHeight() + thickness);
    }

    private static void addBoundaryFixture(Body body, float halfWidth, float halfHeight, float x, float y) {
        PolygonShape shape = new PolygonShape();
        shape.setAsBox(halfWidth, halfHeight, new Vector2(x, y), 0f);
        body.createFixture(shape, 0f).setFriction(0f);
        shape.dispose();
    }

    public void setMapChangeConsumer(Consumer<LdtkMap> consumer) {
        mapChangeConsumer = consumer;
    }

    public void setLoadEntityConsumer(Consumer<LdtkMap.EntityInstance> consumer) {
        loadEntityConsumer = consumer;
    }

    @Override
    public void dispose() {
        if (currentMap != null) currentMap.dispose();
    }
}
