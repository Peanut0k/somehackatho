package io.github.com.quillraven.screen;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.com.quillraven.asset.AssetService;
import io.github.com.quillraven.asset.AtlasAsset;
import io.github.com.quillraven.component.ChessChallenger;
import io.github.com.quillraven.component.ChessChallenger.Ability;
import io.github.com.quillraven.component.ChessChallenger.RewardType;
import io.github.com.quillraven.component.Graphic;
import io.github.com.quillraven.component.Physic;
import io.github.com.quillraven.component.Transform;
import io.github.com.quillraven.component.Trigger;

import java.util.Set;

public final class ChessChallengerFactory {
    private static final float GRID_SIZE = 16f;
    private static final ChallengerSpec[] CHALLENGERS = {
        new ChallengerSpec("tactician", "The Tactician", 1, RewardType.LIFE, Ability.NONE,
            -48f, 0f, new Color(0.55f, 0.78f, 1f, 1f)),
        new ChallengerSpec("guardian", "The Guardian", 2, RewardType.ABILITY, Ability.DOUBLE_MOVE,
            48f, 0f, new Color(1f, 0.68f, 0.45f, 1f)),
        new ChallengerSpec("master", "The Master", 3, RewardType.LIFE, Ability.NONE,
            0f, 48f, new Color(0.83f, 0.68f, 1f, 1f))
    };

    private final TextureRegion region;

    public ChessChallengerFactory(AssetService assetService) {
        TextureAtlas atlas = assetService.get(AtlasAsset.OBJECTS);
        var frames = atlas.findRegions("training_dummy/idle_down");
        if (frames.isEmpty()) {
            throw new GdxRuntimeException("Chess challenger sprite is missing from objects.atlas");
        }
        region = frames.first();
    }

    public void create(Engine engine, com.badlogic.gdx.physics.box2d.World world,
                       Vector2 playerPosition, Set<String> defeatedChallengers) {
        for (ChallengerSpec spec : CHALLENGERS) {
            Entity entity = engine.createEntity();
            Vector2 position = playerPosition.cpy().add(spec.offsetX, spec.offsetY);
            entity.add(new Transform(position, 2, new Vector2(GRID_SIZE, GRID_SIZE),
                new Vector2(1f, 1f), 0f, 0f));
            entity.add(new Graphic(region, spec.color.cpy()));
            entity.add(new ChessChallenger(
                spec.id, spec.name, spec.searchDepth, spec.rewardType, spec.ability,
                defeatedChallengers.contains(spec.id)));
            entity.add(new Trigger("chess-challenger"));

            BodyDef bodyDef = new BodyDef();
            bodyDef.type = BodyDef.BodyType.StaticBody;
            bodyDef.position.set(position);
            Body body = world.createBody(bodyDef);
            body.setUserData(entity);
            PolygonShape shape = new PolygonShape();
            shape.setAsBox(10f, 10f, new Vector2(GRID_SIZE * 0.5f, GRID_SIZE * 0.5f), 0f);
            body.createFixture(shape, 0f).setSensor(true);
            shape.dispose();
            entity.add(new Physic(body, body.getPosition().cpy()));
            engine.addEntity(entity);
        }
    }

    private record ChallengerSpec(
        String id,
        String name,
        int searchDepth,
        RewardType rewardType,
        Ability ability,
        float offsetX,
        float offsetY,
        Color color
    ) {
    }
}
