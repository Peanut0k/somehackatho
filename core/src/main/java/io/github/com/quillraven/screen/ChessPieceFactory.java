package io.github.com.quillraven.screen;

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
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Disposable;
import io.github.com.quillraven.component.ChessPiece;
import io.github.com.quillraven.component.Facing;
import io.github.com.quillraven.component.Graphic;
import io.github.com.quillraven.component.Physic;
import io.github.com.quillraven.component.Transform;
import io.github.com.quillraven.ldtk.LdtkMap;
import io.github.com.quillraven.chessengine.Side;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class ChessPieceFactory implements Disposable {
    private static final int BOARD_SIZE = 8;
    private static final int PIECE_SIZE = 16;
    private static final Color DARK = new Color(0.68f, 0.72f, 0.82f, 1f);

    private final Texture boardTexture;
    private final Texture predictionTexture;
    private final Map<PieceType, Texture> pieces = new EnumMap<>(PieceType.class);
    private final Map<String, Texture> pieceTextures = new HashMap<>();

    public ChessPieceFactory() {
        boardTexture = createBoardTexture();
        predictionTexture = createPredictionTexture();
        for (PieceType type : PieceType.values()) {
            String path = pieceTexturePath(type);
            Texture texture = pieceTextures.computeIfAbsent(path, ChessPieceFactory::loadPieceTexture);
            pieces.put(type, texture);
        }
    }

    public TextureRegion getPawnRegion() {
        return new TextureRegion(pieces.get(PieceType.PAWN));
    }

    public TextureRegion getPredictionRegion() {
        return new TextureRegion(predictionTexture);
    }

    public TextureRegion getBoardRegion() {
        return new TextureRegion(boardTexture);
    }

    public TextureRegion getPieceRegion(io.github.com.quillraven.chessengine.PieceType type) {
        return new TextureRegion(pieces.get(PieceType.valueOf(type.name())));
    }

    public Color getPieceColor(Side side) {
        return side == Side.WHITE ? Color.WHITE.cpy() : DARK.cpy();
    }

    public void createStartingPosition(Engine engine, World world, LdtkMap map, Entity player) {
        int cell = PIECE_SIZE;
        float left = (map.getWidth() - BOARD_SIZE * cell) * 0.5f;
        float bottom = (map.getHeight() - BOARD_SIZE * cell) * 0.5f;
        Vector2 boardSize = new Vector2(BOARD_SIZE * cell, BOARD_SIZE * cell);
        Entity board = engine.createEntity();
        board.add(new Transform(new Vector2(left, bottom), 0, boardSize, new Vector2(1f, 1f), 0f, 0f));
        board.add(new Graphic(new TextureRegion(boardTexture), Color.WHITE.cpy()));
        engine.addEntity(board);

        setPosition(player, left + 4.5f * cell, bottom + 1.5f * cell);
        Facing.MAPPER.get(player).setDirection(Facing.FacingDirection.UP);
        for (int column = 0; column < BOARD_SIZE; column++) {
            addPiece(engine, world, left, bottom, column, 6, PieceType.PAWN);
        }
        addRow(engine, world, left, bottom, 7, new PieceType[]{
            PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
            PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
        });
    }

    private void addRow(
        Engine engine,
        World world,
        float left,
        float bottom,
        int row,
        PieceType[] pieces
    ) {
        for (int column = 0; column < pieces.length; column++) {
            addPiece(engine, world, left, bottom, column, row, pieces[column]);
        }
    }

    private void addPiece(
        Engine engine,
        World world,
        float left,
        float bottom,
        int column,
        int row,
        PieceType type
    ) {
        float x = left + column * PIECE_SIZE;
        float y = bottom + row * PIECE_SIZE;
        Entity entity = engine.createEntity();
        entity.add(new Transform(new Vector2(x, y), 2,
            new Vector2(PIECE_SIZE, PIECE_SIZE), new Vector2(1f, 1f), 0f, 0f));
        entity.add(new Graphic(new TextureRegion(pieces.get(type)), getPieceColor(Side.BLACK)));
        entity.add(new ChessPiece(toChessPieceType(type)));
        entity.add(new Facing(Facing.FacingDirection.DOWN));

        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.set(x, y);
        Body body = world.createBody(bodyDef);
        body.setUserData(entity);
        PolygonShape shape = new PolygonShape();
        shape.setAsBox(5f, 5f, new Vector2(PIECE_SIZE * 0.5f, PIECE_SIZE * 0.5f), 0f);
        body.createFixture(shape, 0f);
        shape.dispose();
        entity.add(new Physic(body, new Vector2(body.getPosition())));
        engine.addEntity(entity);
    }

    private static ChessPiece.Type toChessPieceType(PieceType type) {
        return ChessPiece.Type.valueOf(type.name());
    }

    private static void setPosition(Entity entity, float centerX, float centerY) {
        Body body = Physic.MAPPER.get(entity).getBody();
        Vector2 center = body.getWorldCenter();
        Vector2 position = body.getPosition();
        body.setTransform(position.x + centerX - center.x, position.y + centerY - center.y, body.getAngle());
        body.setLinearVelocity(0f, 0f);
        body.setAngularVelocity(0f);
        Physic.MAPPER.get(entity).getPrevPosition().set(body.getPosition());
        Transform.MAPPER.get(entity).getPosition().set(body.getPosition());
    }

    private static Texture createBoardTexture() {
        Pixmap pixmap = new Pixmap(BOARD_SIZE * PIECE_SIZE, BOARD_SIZE * PIECE_SIZE, Pixmap.Format.RGBA8888);
        Color light = new Color(0.78f, 0.72f, 0.58f, 1f);
        Color dark = new Color(0.25f, 0.34f, 0.31f, 1f);
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int column = 0; column < BOARD_SIZE; column++) {
                pixmap.setColor((row + column) % 2 == 0 ? light : dark);
                pixmap.fillRectangle(column * PIECE_SIZE, row * PIECE_SIZE, PIECE_SIZE, PIECE_SIZE);
            }
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    private static Texture createPredictionTexture() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    private static Texture loadPieceTexture(String path) {
        if (!Gdx.files.internal(path).exists()) {
            throw new GdxRuntimeException("Chess piece texture is missing: " + path);
        }
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    private static String pieceTexturePath(PieceType type) {
        return switch (type) {
            case PAWN -> "pawn.png";
            case KNIGHT -> "horse.png";
            case ROOK, QUEEN -> "rook.png";
            case BISHOP -> "rock.png";
            case KING -> "pesak.png";
        };
    }

    @Override
    public void dispose() {
        boardTexture.dispose();
        predictionTexture.dispose();
        for (Texture texture : pieceTextures.values()) texture.dispose();
    }

    private enum PieceType {
        PAWN, ROOK, KNIGHT, BISHOP, QUEEN, KING
    }
}
