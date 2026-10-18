package io.github.com.peanut0k.ldtk;

import box2dLight.ConeLight;
import box2dLight.DirectionalLight;
import box2dLight.Light;
import box2dLight.PointLight;
import box2dLight.RayHandler;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Disposable;
import io.github.com.peanut0k.GdxGame;
import io.github.com.peanut0k.asset.AssetService;
import io.github.com.peanut0k.asset.AtlasAsset;
import io.github.com.peanut0k.component.Animation2D;
import io.github.com.peanut0k.component.CameraFollow;
import io.github.com.peanut0k.component.ChessChallenger;
import io.github.com.peanut0k.component.ChessPiece;
import io.github.com.peanut0k.component.Controller;
import io.github.com.peanut0k.component.Facing;
import io.github.com.peanut0k.component.Facing.FacingDirection;
import io.github.com.peanut0k.component.Fsm;
import io.github.com.peanut0k.component.Graphic;
import io.github.com.peanut0k.component.Ldtk;
import io.github.com.peanut0k.component.Lighting;
import io.github.com.peanut0k.component.Move;
import io.github.com.peanut0k.component.Physic;
import io.github.com.peanut0k.component.Player;
import io.github.com.peanut0k.component.Transform;
import io.github.com.peanut0k.component.Trigger;

public final class LdtkAshleyConfigurator implements Disposable {
    private final Engine engine;
    private final World physicsWorld;
    private final AssetService assetService;
    private final RayHandler rayHandler;
    private final Texture portalTexture;
    private Texture chessPawnTexture;

    public LdtkAshleyConfigurator(Engine engine, World physicsWorld, AssetService assetService, RayHandler rayHandler) {
        this.engine = engine;
        this.physicsWorld = physicsWorld;
        this.assetService = assetService;
        this.rayHandler = rayHandler;
        this.portalTexture = createPortalTexture();
    }

    public void onLoadEntity(LdtkMap.EntityInstance instance) {
        String id = instance.identifier();
        String lowerId = id.toLowerCase();
        if (isLightEntity(instance, lowerId)) {
            createLightEntity(instance, lowerId);
            return;
        }
        if (lowerId.equals("chesschallenger") || lowerId.equals("kupec")) {
            createChessChallenger(instance);
            return;
        }
        boolean chessPawn = lowerId.equals("chesspawn");
        String triggerName = instance.getString("triggerName", instance.getString("trigger", ""));
        boolean isSensor = instance.getBoolean("sensor", false);
        boolean isTrigger = lowerId.contains("trigger") || !triggerName.isBlank() || isSensor;
        boolean isPlayer = "player".equalsIgnoreCase(id);
        if (isPlayer && engine.getEntitiesFor(Family.all(Player.class).get()).size() > 0) return;

        Entity entity = engine.createEntity();
        float width = Math.max(instance.width(), GdxGame.UNIT_SCALE * 8f);
        float height = Math.max(instance.height(), GdxGame.UNIT_SCALE * 8f);
        float colliderWidth = instance.getFloat("colliderWidth", instance.width() / GdxGame.UNIT_SCALE)
            * GdxGame.UNIT_SCALE;
        float colliderHeight = instance.getFloat("colliderHeight", instance.height() / GdxGame.UNIT_SCALE)
            * GdxGame.UNIT_SCALE;
        float colliderCenterX = instance.width() * 0.5f
            + instance.getFloat("colliderOffsetX", 0f) * GdxGame.UNIT_SCALE;
        float colliderCenterY = instance.height() * 0.5f
            + instance.getFloat("colliderOffsetY", 0f) * GdxGame.UNIT_SCALE;
        Vector2 position = new Vector2(instance.x(), instance.y());
        entity.add(new Transform(position, (int) instance.getFloat("z", 1f),
            new Vector2(width, height), new Vector2(
            instance.getFloat("scaleX", 1f),
            instance.getFloat("scaleY", 1f)
        ), instance.getFloat("rotation", 0f),
            instance.getFloat("sortOffsetY", 0f) * GdxGame.UNIT_SCALE));
        entity.add(new Ldtk(instance.iid(), id, instance.layerIndex()));

        if (isTrigger) {
            String name = triggerName.isBlank()
                ? isSensor || lowerId.startsWith("teleport") ? "teleportt" : id
                : triggerName;
            entity.add(new Trigger(name));
            if (lowerId.startsWith("teleport")) {
                entity.add(new Graphic(new TextureRegion(portalTexture), Color.WHITE.cpy()));
            }
            createBody(entity, position, colliderWidth, colliderHeight, colliderCenterX,
                colliderCenterY, BodyDef.BodyType.StaticBody, true);
            engine.addEntity(entity);
            return;
        }

        TextureRegion region = getRegion(instance);
        if (region == null && chessPawn) region = getChessPawnRegion();
        if (region != null) {
            entity.add(new Graphic(region, Color.WHITE.cpy()));
        }
        if (chessPawn) {
            entity.add(new ChessPiece(ChessPiece.Type.PAWN));
        } else {
            addAnimation(instance, entity);
            addMovement(instance, entity);
            entity.add(new Facing(FacingDirection.DOWN));
            entity.add(new Fsm(entity));
        }

        if (isPlayer) {
            entity.add(new Player());
            entity.add(new Controller());
            entity.add(new CameraFollow());
            if (Move.MAPPER.get(entity) == null) {
                entity.add(new Move(3f * 16f * GdxGame.UNIT_SCALE));
            }
        } else {
            if (instance.getBoolean("controller", false)) entity.add(new Controller());
            if (instance.getBoolean("camFollow", false)) entity.add(new CameraFollow());
        }

        String bodyTypeName = instance.getString("bodyType", chessPawn
            || "Prop".equalsIgnoreCase(instance.getString("type", "")) ? "StaticBody" : "DynamicBody");
        if (!instance.getBoolean("disableCollider", false)) {
            try {
                float cutoutWidth = instance.getFloat("colliderCutoutWidth", 0f) * GdxGame.UNIT_SCALE;
                float cutoutHeight = instance.getFloat("colliderCutoutHeight", 0f) * GdxGame.UNIT_SCALE;
                float cutoutCenterX = colliderCenterX
                    + instance.getFloat("colliderCutoutOffsetX", 0f) * GdxGame.UNIT_SCALE;
                float cutoutCenterY = colliderCenterY
                    + instance.getFloat("colliderCutoutOffsetY", 0f) * GdxGame.UNIT_SCALE;
                createBody(entity, position, colliderWidth, colliderHeight,
                    colliderCenterX, colliderCenterY, cutoutWidth, cutoutHeight,
                    cutoutCenterX, cutoutCenterY,
                    BodyDef.BodyType.valueOf(bodyTypeName), instance.getBoolean("sensor", false));
            } catch (IllegalArgumentException exception) {
                throw new GdxRuntimeException("Unsupported LDtk bodyType '" + bodyTypeName + "' on " + id, exception);
            }
        }
        engine.addEntity(entity);
    }

    private void createChessChallenger(LdtkMap.EntityInstance instance) {
        boolean merchant = "kupec".equalsIgnoreCase(instance.identifier());
        String name = instance.getString("challengerName", merchant ? "The Merchant" : instance.identifier());
        String challengerId = instance.getString("challengerId",
            merchant ? instance.iid() : name);

        Entity entity = engine.createEntity();
        Vector2 position = new Vector2(instance.x(), instance.y());
        Vector2 size = new Vector2(
            Math.max(instance.width(), GdxGame.UNIT_SCALE * 16f),
            Math.max(instance.height(), GdxGame.UNIT_SCALE * 16f)
        );
        entity.add(new Transform(position, (int) instance.getFloat("z", 2f), size,
            new Vector2(1f, 1f), 0f, 0f));
        entity.add(new Ldtk(instance.iid(), instance.identifier(), instance.layerIndex()));
        TextureRegion region = instance.region();
        if (region == null) {
            TextureAtlas atlas = assetService.get(AtlasAsset.OBJECTS);
            var frames = atlas.findRegions(instance.getString("sprite", "training_dummy/idle_down"));
            if (frames.isEmpty()) {
                throw new GdxRuntimeException("Chess challenger sprite is missing from objects.atlas: " + name);
            }
            region = frames.first();
        }
        String colorValue = instance.getString("color", "ffffff").replace("#", "");
        Color color;
        try {
            color = Color.valueOf(colorValue);
        } catch (IllegalArgumentException exception) {
            throw new GdxRuntimeException("Invalid chess challenger color '" + colorValue + "' on " + name, exception);
        }
        entity.add(new Graphic(region, color));
        entity.add(new ChessChallenger(challengerId, name, false));
        entity.add(new Trigger("chess-challenger"));
        createBody(entity, position, size.x * 0.6f, size.y * 0.6f,
            size.x * 0.5f, size.y * 0.5f, BodyDef.BodyType.StaticBody, true);
        engine.addEntity(entity);
    }

    private static Texture createPortalTexture() {
        Pixmap pixmap = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0.08f, 0.12f, 0.3f, 1f);
        pixmap.fillCircle(8, 8, 7);
        pixmap.setColor(0.25f, 0.65f, 1f, 1f);
        pixmap.fillCircle(8, 8, 5);
        pixmap.setColor(0.15f, 0.22f, 0.5f, 1f);
        pixmap.fillCircle(8, 8, 3);
        pixmap.setColor(0.65f, 0.9f, 1f, 1f);
        pixmap.fillCircle(8, 8, 1);
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    private TextureRegion getChessPawnRegion() {
        if (chessPawnTexture == null) {
            if (!Gdx.files.internal("pawn.png").exists()) {
                throw new GdxRuntimeException("ChessPawn has no LDtk tile and pawn.png is missing");
            }
            chessPawnTexture = new Texture(Gdx.files.internal("pawn.png"));
            chessPawnTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        return new TextureRegion(chessPawnTexture);
    }

    private static boolean isLightEntity(LdtkMap.EntityInstance instance, String lowerId) {
        return instance.fields().containsKey("lightType")
            || lowerId.equals("light")
            || lowerId.equals("pointlight")
            || lowerId.equals("conelight")
            || lowerId.equals("directionallight");
    }

    private void createLightEntity(LdtkMap.EntityInstance instance, String lowerId) {
        String defaultType = switch (lowerId) {
            case "conelight" -> "cone";
            case "directionallight" -> "directional";
            default -> "point";
        };
        String lightType = instance.getString("lightType", defaultType).toLowerCase();
        String colorValue = instance.getString("color", "#ffc878").replace("#", "");
        Color color;
        try {
            color = Color.valueOf(colorValue);
        } catch (IllegalArgumentException exception) {
            throw new GdxRuntimeException("Invalid LDtk light color '" + colorValue + "'", exception);
        }

        int rays = MathUtils.clamp((int) instance.getFloat("rays", 64f), 8, 128);
        float distance = instance.getFloat("distance", 120f);
        if (distance <= 0f) {
            throw new GdxRuntimeException("LDtk light distance must be greater than zero on " + instance.identifier());
        }
        float direction = instance.getFloat("direction", 0f);
        Vector2 position = new Vector2(instance.x(), instance.y());
        Vector2 size = new Vector2(instance.width(), instance.height());
        float centerX = position.x + size.x * 0.5f;
        float centerY = position.y + size.y * 0.5f;
        Light light;
        switch (lightType) {
            case "point" -> light = new PointLight(
                rayHandler, rays, color, distance, centerX, centerY);
            case "cone" -> light = new ConeLight(
                rayHandler, rays, color, distance, centerX, centerY,
                direction, MathUtils.clamp(instance.getFloat("coneAngle", 45f), 1f, 180f));
            case "directional" -> light = new DirectionalLight(
                rayHandler, rays, color, direction);
            default -> throw new GdxRuntimeException(
                "Unsupported LDtk lightType '" + lightType + "' on " + instance.identifier());
        }
        light.setSoft(instance.getBoolean("soft", true));
        light.setSoftnessLength(Math.max(0f, instance.getFloat("softnessLength", 2f)));
        light.setActive(instance.getBoolean("active", true));
        boolean staticLight = instance.getBoolean("staticLight", false);
        light.setStaticLight(staticLight);

        Entity entity = engine.createEntity();
        entity.add(new Transform(position, 0, size, new Vector2(1f, 1f), 0f, 0f));
        entity.add(new Ldtk(instance.iid(), instance.identifier(), instance.layerIndex()));
        entity.add(new Lighting(light, !staticLight && !"directional".equals(lightType)));
        engine.addEntity(entity);
    }

    public void createDefaultPlayer(LdtkMap map) {
        ImmutableArray<Entity> players = engine.getEntitiesFor(Family.all(Player.class, Physic.class).get());
        if (players.size() > 0) {
            positionExistingPlayer(players.first(), map);
            return;
        }
        for (LdtkMap.EntityInstance entity : map.getEntities()) {
            if ("player".equalsIgnoreCase(entity.identifier())) return;
        }
        float[] spawn = map.findOpenSpawn();
        TextureAtlas atlas = assetService.get(AtlasAsset.OBJECTS);
        var idleFrames = atlas.findRegions("player/idle_down");
        TextureRegion region = idleFrames.isEmpty() ? null : idleFrames.first();
        if (region == null) throw new GdxRuntimeException("Player idle animation is missing from objects.atlas");
        Vector2 size = new Vector2(
            region.getRegionWidth() * GdxGame.UNIT_SCALE,
            region.getRegionHeight() * GdxGame.UNIT_SCALE
        );
        Entity entity = engine.createEntity();
        Vector2 position = new Vector2(spawn[0] - size.x * 0.25f, spawn[1] - size.y * 0.25f);
        entity.add(new Transform(position, 1, size, new Vector2(1f, 1f), 0f, 0f));
        entity.add(new Graphic(region, Color.WHITE.cpy()));
        entity.add(new Animation2D(AtlasAsset.OBJECTS, "player", Animation2D.AnimationType.IDLE,
            Animation.PlayMode.LOOP, 1f));
        entity.add(new Facing(FacingDirection.DOWN));
        entity.add(new Fsm(entity));
        entity.add(new Player());
        entity.add(new Controller());
        entity.add(new CameraFollow());
        entity.add(new Move(3f * map.getGridSize() * GdxGame.UNIT_SCALE));
        createBody(entity, position, size.x * 0.275f, size.y * 0.275f,
            size.x * 0.5f, size.y * 0.5f, BodyDef.BodyType.DynamicBody, false);
        engine.addEntity(entity);
    }

    public void createDefaultArenaPortalIfMissing(LdtkMap map) {
        ImmutableArray<Entity> triggers = engine.getEntitiesFor(Family.all(Trigger.class).get());
        for (Entity entity : triggers) {
            if ("teleportt".equalsIgnoreCase(Trigger.MAPPER.get(entity).getName())) return;
        }

        float[] spawn = map.findOpenSpawn();
        Vector2 size = new Vector2(16f, 16f);
        Vector2 position = new Vector2(spawn[0] - size.x * 0.5f, spawn[1] - size.y * 0.5f);
        Entity portal = engine.createEntity();
        portal.add(new Transform(position, 2, size, new Vector2(1f, 1f), 0f, 0f));
        portal.add(new Ldtk("default-arena-portal", "Teleportt"));
        portal.add(new Trigger("teleportt"));
        portal.add(new Graphic(new TextureRegion(portalTexture), Color.WHITE.cpy()));
        createBody(portal, position, size.x, size.y, size.x * 0.5f, size.y * 0.5f,
            BodyDef.BodyType.StaticBody, true);
        engine.addEntity(portal);
    }

    private static void positionExistingPlayer(Entity player, LdtkMap map) {
        Physic physic = Physic.MAPPER.get(player);
        Body body = physic.getBody();
        LdtkMap.EntityInstance spawn = null;
        for (LdtkMap.EntityInstance instance : map.getEntities()) {
            if ("player".equalsIgnoreCase(instance.identifier())) {
                spawn = instance;
                break;
            }
        }

        Vector2 target = new Vector2();
        if (spawn != null) {
            target.set(spawn.x(), spawn.y());
        } else {
            float[] openSpawn = map.findOpenSpawn();
            target.set(openSpawn[0], openSpawn[1]);
            target.sub(body.getWorldCenter()).add(body.getPosition());
        }
        body.setTransform(target, body.getAngle());
        body.setLinearVelocity(0f, 0f);
        body.setAngularVelocity(0f);
        physic.getPrevPosition().set(target);
        Transform.MAPPER.get(player).getPosition().set(target);
        Move move = Move.MAPPER.get(player);
        if (move != null) {
            move.getDirection().setZero();
            move.setSprinting(false);
        }
        Facing facing = Facing.MAPPER.get(player);
        if (facing != null) facing.setDirection(FacingDirection.DOWN);
        Controller controller = Controller.MAPPER.get(player);
        if (controller != null) {
            controller.getPressedCommands().clear();
            controller.getReleasedCommands().clear();
        }
    }

    private TextureRegion getRegion(LdtkMap.EntityInstance instance) {
        if (instance.region() != null) return instance.region();
        String sprite = instance.getString("sprite", "");
        if (sprite.isBlank()) return null;
        String atlasName = instance.getString("atlasAsset", "OBJECTS");
        try {
            TextureAtlas atlas = assetService.get(AtlasAsset.valueOf(atlasName));
            TextureRegion region = atlas.findRegion(sprite);
            if (region == null) region = atlas.findRegion(sprite + "/" + sprite);
            return region;
        } catch (IllegalArgumentException exception) {
            throw new GdxRuntimeException("Unsupported LDtk atlasAsset '" + atlasName + "'", exception);
        }
    }

    private void addAnimation(LdtkMap.EntityInstance instance, Entity entity) {
        String animation = instance.getString("animation", "");
        if (animation.isBlank()) return;
        try {
            AtlasAsset atlasAsset = AtlasAsset.valueOf(instance.getString("atlasAsset", "OBJECTS"));
            String atlasKey = instance.getString("atlasKey", instance.getString("sprite", instance.identifier().toLowerCase()));
            Animation2D.AnimationType type = Animation2D.AnimationType.valueOf(animation);
            entity.add(new Animation2D(atlasAsset, atlasKey, type, Animation.PlayMode.LOOP,
                instance.getFloat("animationSpeed", 1f)));
        } catch (IllegalArgumentException exception) {
            throw new GdxRuntimeException("Invalid LDtk animation fields on " + instance.identifier(), exception);
        }
    }

    private void addMovement(LdtkMap.EntityInstance instance, Entity entity) {
        float speed = instance.getFloat("speed", 0f);
        if (speed > 0f) entity.add(new Move(speed));
    }

    private void createBody(Entity entity, Vector2 position, float width, float height,
                            float centerX, float centerY, BodyDef.BodyType bodyType, boolean sensor) {
        createBody(entity, position, width, height, centerX, centerY, 0f, 0f,
            centerX, centerY, bodyType, sensor);
    }

    private void createBody(Entity entity, Vector2 position, float width, float height,
                            float centerX, float centerY, float cutoutWidth, float cutoutHeight,
                            float cutoutCenterX, float cutoutCenterY,
                            BodyDef.BodyType bodyType, boolean sensor) {
        if (width <= 0f || height <= 0f) {
            throw new GdxRuntimeException("Collider dimensions must be positive");
        }
        boolean hasCutout = cutoutWidth > 0f || cutoutHeight > 0f;
        if (hasCutout && (cutoutWidth <= 0f || cutoutHeight <= 0f)) {
            throw new GdxRuntimeException("Collider cutout width and height must both be positive");
        }
        float left = centerX - width * 0.5f;
        float right = centerX + width * 0.5f;
        float bottom = centerY - height * 0.5f;
        float top = centerY + height * 0.5f;
        float cutoutLeft = cutoutCenterX - cutoutWidth * 0.5f;
        float cutoutRight = cutoutCenterX + cutoutWidth * 0.5f;
        float cutoutBottom = cutoutCenterY - cutoutHeight * 0.5f;
        float cutoutTop = cutoutCenterY + cutoutHeight * 0.5f;

        BodyDef bodyDef = new BodyDef();
        bodyDef.type = bodyType;
        bodyDef.position.set(position);
        bodyDef.fixedRotation = true;
        Body body = physicsWorld.createBody(bodyDef);
        body.setUserData(entity);
        float density = bodyType == BodyDef.BodyType.DynamicBody ? 1f : 0f;
        if (!hasCutout) {
            addColliderFixture(body, width, height, centerX, centerY, density, sensor);
        } else {
            float openingLeft = Math.max(left, cutoutLeft);
            float openingRight = Math.min(right, cutoutRight);
            float openingBottom = Math.max(bottom, cutoutBottom);
            float openingTop = Math.min(top, cutoutTop);
            if (openingLeft >= openingRight || openingBottom >= openingTop) {
                addColliderFixture(body, width, height, centerX, centerY, density, sensor);
            } else {
                float openingHeight = openingTop - openingBottom;
                float openingCenterY = (openingBottom + openingTop) * 0.5f;
                addColliderFixture(body, width, top - openingTop, centerX,
                    (top + openingTop) * 0.5f, density, sensor);
                addColliderFixture(body, width, openingBottom - bottom, centerX,
                    (bottom + openingBottom) * 0.5f, density, sensor);
                addColliderFixture(body, openingLeft - left, openingHeight,
                    (left + openingLeft) * 0.5f, openingCenterY, density, sensor);
                addColliderFixture(body, right - openingRight, openingHeight,
                    (openingRight + right) * 0.5f, openingCenterY, density, sensor);
            }
        }
        entity.add(new Physic(body, new Vector2(body.getPosition())));
    }

    private static void addColliderFixture(Body body, float width, float height,
                                           float centerX, float centerY, float density, boolean sensor) {
        if (width <= 0f || height <= 0f) return;
        PolygonShape shape = new PolygonShape();
        shape.setAsBox(width * 0.5f, height * 0.5f, new Vector2(centerX, centerY), 0f);
        var fixture = body.createFixture(shape, density);
        fixture.setSensor(sensor);
        fixture.setUserData(sensor ? "trigger" : null);
        shape.dispose();
    }

    @Override
    public void dispose() {
        portalTexture.dispose();
        if (chessPawnTexture != null) chessPawnTexture.dispose();
    }
}
