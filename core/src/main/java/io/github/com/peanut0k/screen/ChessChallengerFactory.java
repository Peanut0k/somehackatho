package io.github.com.peanut0k.screen;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Disposable;
import io.github.com.peanut0k.component.ChessChallenger;
import io.github.com.peanut0k.component.Graphic;
import io.github.com.peanut0k.component.Physic;
import io.github.com.peanut0k.component.Transform;
import io.github.com.peanut0k.component.Trigger;

import java.util.Set;

public final class ChessChallengerFactory implements Disposable {
    private static final float GRID_SIZE = 16f;
    private static final Color QUEST_COLOR = new Color(1f, 0.72f, 0.16f, 1f);
    private static final ChallengerSpec[] CHALLENGERS = {
        new ChallengerSpec("tactician", "The Tactician",
            -48f, 0f, new Color(0.55f, 0.78f, 1f, 1f)),
        new ChallengerSpec("guardian", "The Guardian",
            48f, 0f, new Color(1f, 0.68f, 0.45f, 1f)),
        new ChallengerSpec("master", "The Master",
            0f, 48f, new Color(0.83f, 0.68f, 1f, 1f))
    };

    private final Texture duelistTexture;
    private final TextureRegion region;
    private final Texture indicatorTexture;
    private final TextureRegion questRegion;
    private final TextureRegion completedRegion;

    public ChessChallengerFactory() {
        if (!Gdx.files.internal("jitka.png").exists()) {
            throw new GdxRuntimeException("Jitka2 duelist texture is missing: jitka.png");
        }
        duelistTexture = new Texture(Gdx.files.internal("jitka.png"));
        duelistTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        region = new TextureRegion(duelistTexture);
        indicatorTexture = createIndicatorTexture();
        questRegion = new TextureRegion(indicatorTexture, 0, 0, 16, 16);
        completedRegion = new TextureRegion(indicatorTexture, 16, 0, 16, 16);
    }

    public void create(Engine engine, com.badlogic.gdx.physics.box2d.World world,
                       Vector2 playerPosition, Set<String> defeatedChallengers) {
        for (ChallengerSpec spec : CHALLENGERS) {
            Entity entity = engine.createEntity();
            Vector2 position = playerPosition.cpy().add(spec.offsetX, spec.offsetY);
            entity.add(new Transform(position, 2, new Vector2(GRID_SIZE, GRID_SIZE),
                new Vector2(1f, 1f), 0f, 0f));
            entity.add(new Graphic(region, spec.color.cpy()));
            ChessChallenger challenger =
                new ChessChallenger(spec.id, spec.name, defeatedChallengers.contains(spec.id));
            entity.add(challenger);
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
            addCompletionMarker(engine, entity, challenger);
        }
    }

    public void addCompletionMarker(Engine engine, Entity challengerEntity, ChessChallenger challenger) {
        if (challenger == null || challengerEntity == null) {
            throw new IllegalArgumentException("Challenger entity and data are required");
        }
        if (challenger.hasCompletionMarker()) return;
        if (challengerEntity.getComponent(Transform.class) == null) {
            throw new IllegalStateException("Chess challenger has no transform");
        }
        Transform transform = Transform.MAPPER.get(challengerEntity);
        Vector2 position = transform.getPosition();
        Vector2 size = transform.getSize();
        Entity marker = engine.createEntity();
        marker.add(new Transform(
            new Vector2(position.x + (size.x - 12f) * 0.5f, position.y + size.y + 2f),
            3,
            new Vector2(12f, 12f),
            new Vector2(1f, 1f),
            0f,
            0f
        ));
        marker.add(new Graphic(questRegion, Color.WHITE.cpy()));
        engine.addEntity(marker);
        challenger.setCompletionMarker(marker, questRegion, completedRegion);
    }

    private static Texture createIndicatorTexture() {
        Pixmap pixmap = new Pixmap(32, 16, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        pixmap.setColor(new Color(0.04f, 0.05f, 0.08f, 1f));
        pixmap.fillCircle(8, 8, 7);
        pixmap.fillCircle(24, 8, 7);
        pixmap.setColor(QUEST_COLOR);
        pixmap.fillCircle(8, 8, 5);
        pixmap.setColor(new Color(0.35f, 1f, 0.42f, 1f));
        pixmap.fillCircle(24, 8, 5);

        pixmap.setColor(new Color(0.12f, 0.08f, 0.02f, 1f));
        pixmap.fillRectangle(8, 6, 1, 6);
        pixmap.fillRectangle(8, 3, 1, 2);
        pixmap.setColor(new Color(0.04f, 0.08f, 0.04f, 1f));
        pixmap.drawLine(20, 8, 23, 11);
        pixmap.drawLine(23, 11, 28, 5);
        pixmap.setColor(Color.WHITE);
        pixmap.drawLine(21, 8, 23, 10);
        pixmap.drawLine(23, 10, 27, 5);

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    @Override
    public void dispose() {
        duelistTexture.dispose();
        indicatorTexture.dispose();
    }

    private record ChallengerSpec(
        String id,
        String name,
        float offsetX,
        float offsetY,
        Color color
    ) {
    }
}
