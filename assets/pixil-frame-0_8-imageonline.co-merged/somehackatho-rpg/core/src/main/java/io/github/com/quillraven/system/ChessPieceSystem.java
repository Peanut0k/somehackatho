package io.github.com.peanut0k.system;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import io.github.com.peanut0k.chessengine.ChessEngine;
import io.github.com.peanut0k.chessengine.Move;
import io.github.com.peanut0k.chessengine.Piece;
import io.github.com.peanut0k.chessengine.PieceType;
import io.github.com.peanut0k.chessengine.Position;
import io.github.com.peanut0k.chessengine.Side;
import io.github.com.peanut0k.component.ChessPiece;
import io.github.com.peanut0k.component.Facing;
import io.github.com.peanut0k.component.Facing.FacingDirection;
import io.github.com.peanut0k.component.Graphic;
import io.github.com.peanut0k.component.Physic;
import io.github.com.peanut0k.component.Player;
import io.github.com.peanut0k.component.Transform;
import io.github.com.peanut0k.input.Command;
import io.github.com.peanut0k.ldtk.LdtkMap;
import io.github.com.peanut0k.ui.model.GameViewModel;

import java.util.function.Consumer;

public final class ChessPieceSystem extends EntitySystem {
    private static final int BOARD_SIZE = 8;
    private static final float CELL_SIZE = 16f;

    private final GameViewModel viewModel;
    private final TextureRegion predictionRegion;
    private final Consumer<Boolean> onCombatFinished;
    private final ChessEngine enemyEngine = new ChessEngine();
    private final Vector2 target = new Vector2();
    private final Vector2 delta = new Vector2();
    private ImmutableArray<Entity> pieces;
    private ImmutableArray<Entity> players;
    private LdtkMap map;
    private float boardLeft;
    private float boardBottom;
    private boolean finished;
    private boolean lostLifeThisTurn;
    private int lives = 2;
    private Entity predictionMarker;
    private Entity predictionSourceMarker;
    private PlannedMove predictedMove;
    private PlannedMove pendingThreatMove;
    private boolean pendingThreat;
    private int pendingCaptureColumn;
    private int pendingCaptureRow;

    public ChessPieceSystem(
        GameViewModel viewModel,
        TextureRegion predictionRegion,
        Consumer<Boolean> onCombatFinished
    ) {
        this.viewModel = viewModel;
        this.predictionRegion = predictionRegion;
        this.onCombatFinished = onCombatFinished;
    }

    @Override
    public void addedToEngine(Engine engine) {
        pieces = engine.getEntitiesFor(Family.all(ChessPiece.class, Physic.class).get());
        players = engine.getEntitiesFor(Family.all(Player.class, Physic.class).get());
    }

    public void setMap(LdtkMap map) {
        clearPredictionMarker();
        predictedMove = null;
        pendingThreatMove = null;
        pendingThreat = false;
        this.map = map;
        boardLeft = (map.getWidth() - BOARD_SIZE * CELL_SIZE) * 0.5f;
        boardBottom = (map.getHeight() - BOARD_SIZE * CELL_SIZE) * 0.5f;
        finished = false;
        lostLifeThisTurn = false;
        lives = 2;
        viewModel.setLives(lives);
        viewModel.setGameMessage("Capture the black king.");
        viewModel.setEnemyPrediction("Enemy move: preparing board");
    }

    public void initializeBoard() {
        for (int i = 0; i < pieces.size(); i++) {
            Entity piece = pieces.get(i);
            if (ChessPiece.MAPPER.get(piece).getType() == ChessPiece.Type.KING) {
                updatePrediction();
                return;
            }
        }
        finish(true);
    }

    public boolean movePlayer(Command action) {
        if (finished || map == null || players.size() == 0) return false;

        Entity player = players.first();
        Facing facing = Facing.MAPPER.get(player);
        FacingDirection direction = facing.getDirection();
        if (action == Command.LEFT || action == Command.RIGHT) {
            facing.setDirection(rotate(direction, action == Command.RIGHT));
            takeEnemyTurn(player, null);
            if (!finished) {
                if (!lostLifeThisTurn) {
                    viewModel.setGameMessage("Facing " + facing.getDirection().name().toLowerCase() + ".");
                }
                updatePrediction();
            }
            return true;
        }

        int[] forward = vector(direction);
        int[] left = {-forward[1], forward[0]};
        Vector2 playerPosition = entityCenter(player);
        int column = cell(playerPosition.x, boardLeft);
        int row = cell(playerPosition.y, boardBottom);
        int nextColumn;
        int nextRow;
        Entity capturedPiece = null;

        if (action == Command.UP || action == Command.DOWN) {
            int distance = action == Command.UP ? 1 : -1;
            nextColumn = column + forward[0] * distance;
            nextRow = row + forward[1] * distance;
            if (!isOpen(nextColumn, nextRow) || pieceAt(nextColumn, nextRow) != null) {
                viewModel.setGameMessage("That square is blocked.");
                return false;
            }
        } else if (action == Command.CAPTURE_LEFT || action == Command.CAPTURE_RIGHT) {
            int side = action == Command.CAPTURE_LEFT ? 1 : -1;
            nextColumn = column + forward[0] + left[0] * side;
            nextRow = row + forward[1] + left[1] * side;
            capturedPiece = pieceAt(nextColumn, nextRow);
            if (capturedPiece == null) {
                viewModel.setGameMessage("There is no enemy to capture there.");
                return false;
            }
            boolean capturedKing = ChessPiece.MAPPER.get(capturedPiece).getType() == ChessPiece.Type.KING;
            getEngine().removeEntity(capturedPiece);
            moveEntity(player, nextColumn, nextRow);
            if (capturedKing) {
                viewModel.setGameMessage("The black king is captured. You win!");
                finish(true);
                return true;
            }
        } else {
            return false;
        }

        if (capturedPiece == null) moveEntity(player, nextColumn, nextRow);
        takeEnemyTurn(player, capturedPiece);
        if (!finished) {
            if (!lostLifeThisTurn) viewModel.setGameMessage("Your turn.");
            updatePrediction();
        }
        return true;
    }

    private void takeEnemyTurn(Entity player, Entity capturedPiece) {
        lostLifeThisTurn = false;
        if (pendingThreat) {
            Vector2 playerPosition = entityCenter(player);
            int playerColumn = cell(playerPosition.x, boardLeft);
            int playerRow = cell(playerPosition.y, boardBottom);
            Entity attacker = playerColumn == pendingCaptureColumn && playerRow == pendingCaptureRow
                ? findThreateningPiece(playerColumn, playerRow, capturedPiece)
                : null;
            if (attacker != null) {
                lives--;
                viewModel.setLives(lives);
                lostLifeThisTurn = true;
                moveEntity(attacker, playerColumn, playerRow);
                if (lives == 0) {
                    viewModel.setGameMessage("You lost both lives. Game over.");
                    finish(false);
                    return;
                }
                Square respawn = findNearestSafeSquare(playerColumn, playerRow);
                if (respawn == null) respawn = findNearestEmptySquare(playerColumn, playerRow);
                if (respawn != null) moveEntity(player, respawn.column, respawn.row);
                viewModel.setGameMessage("Captured! Respawned safely. " + lives + " life remaining.");
                pendingThreat = false;
                pendingThreatMove = null;
                return;
            }
            PlannedMove threatenedMove = pendingThreatMove;
            pendingThreat = false;
            pendingThreatMove = null;
            if (threatenedMove != null && threatenedMove.piece != capturedPiece
                && isOnBoard(threatenedMove.piece)) {
                Vector2 currentPlayerPosition = entityCenter(player);
                int currentPlayerColumn = cell(currentPlayerPosition.x, boardLeft);
                int currentPlayerRow = cell(currentPlayerPosition.y, boardBottom);
                if (threatenedMove.destination.column == currentPlayerColumn
                    && threatenedMove.destination.row == currentPlayerRow) {
                    pendingThreat = true;
                    pendingThreatMove = threatenedMove;
                    pendingCaptureColumn = currentPlayerColumn;
                    pendingCaptureRow = currentPlayerRow;
                    viewModel.setGameMessage("A black piece threatens your square. Move to escape!");
                    return;
                }
                Gdx.app.log("ChessEngine", "Executing predicted black move " + moveName(threatenedMove));
                moveEntity(
                    threatenedMove.piece,
                    threatenedMove.destination.column,
                    threatenedMove.destination.row
                );
                return;
            }
        }

        PlannedMove plannedMove = predictedMove;
        predictedMove = null;
        if (plannedMove != null && (plannedMove.piece == capturedPiece || !isOnBoard(plannedMove.piece))) {
            plannedMove = null;
        }
        if (plannedMove != null && pieceAt(plannedMove.destination.column, plannedMove.destination.row) != null) {
            plannedMove = null;
        }
        if (plannedMove == null) plannedMove = findEnemyMove(player, capturedPiece);
        if (plannedMove == null) {
            viewModel.setEnemyPrediction("Enemy move: no legal move");
            clearPredictionMarker();
            return;
        }
        String uci = moveName(plannedMove);
        Gdx.app.log("ChessEngine", "Executing predicted black move " + uci);
        Vector2 playerPosition = entityCenter(player);
        int playerColumn = cell(playerPosition.x, boardLeft);
        int playerRow = cell(playerPosition.y, boardBottom);
        if (plannedMove.destination.column == playerColumn
            && plannedMove.destination.row == playerRow) {
            pendingThreat = true;
            pendingThreatMove = plannedMove;
            pendingCaptureColumn = playerColumn;
            pendingCaptureRow = playerRow;
            viewModel.setGameMessage("A black piece threatens your square. Move to escape!");
            return;
        }
        moveEntity(plannedMove.piece, plannedMove.destination.column, plannedMove.destination.row);
    }

    private PlannedMove findEnemyMove(Entity player, Entity capturedPiece) {
        Vector2 playerPosition = entityCenter(player);
        int playerColumn = cell(playerPosition.x, boardLeft);
        int playerRow = cell(playerPosition.y, boardBottom);
        Position position = createEnginePosition(playerColumn, playerRow, capturedPiece);
        Move move = enemyEngine.chooseMove(position, Side.BLACK, 3);
        if (move == null) return null;
        Entity movedEntity = pieceAt(move.fromFile(), move.fromRank());
        if (movedEntity == null || movedEntity == capturedPiece) return null;
        return new PlannedMove(
            movedEntity,
            new Square(move.fromFile(), move.fromRank()),
            new Square(move.toFile(), move.toRank()),
            playerColumn,
            playerRow
        );
    }

    private Position createEnginePosition(int playerColumn, int playerRow, Entity capturedPiece) {
        Position position = new Position();
        position.setSideToMove(Side.BLACK);
        for (int i = 0; i < pieces.size(); i++) {
            Entity entity = pieces.get(i);
            if (entity == capturedPiece) continue;
            Vector2 piecePosition = entityCenter(entity);
            int column = cell(piecePosition.x, boardLeft);
            int row = cell(piecePosition.y, boardBottom);
            ChessPiece.Type pieceType = ChessPiece.MAPPER.get(entity).getType();
            position.putPiece(column, row, new Piece(Side.BLACK, toEngineType(pieceType)));
        }
        position.putPiece(playerColumn, playerRow, new Piece(Side.WHITE, PieceType.PAWN));
        return position;
    }

    private static PieceType toEngineType(ChessPiece.Type type) {
        return PieceType.valueOf(type.name());
    }

    private void updatePrediction() {
        if (finished || players == null || players.size() == 0) return;
        if (pendingThreat) {
            updatePredictionMarker(pendingCaptureColumn, pendingCaptureRow, true);
            clearPredictionSourceMarker();
            viewModel.setEnemyPrediction("Danger: enemy threatens your square!");
            viewModel.setGameMessage("A black piece threatens your square. Move to escape!");
            return;
        }
        predictedMove = findEnemyMove(players.first(), null);
        if (predictedMove == null) {
            viewModel.setEnemyPrediction("Enemy move: no legal move");
            clearPredictionMarker();
            return;
        }
        PlannedMove move = predictedMove;
        ChessPiece.Type type = ChessPiece.MAPPER.get(move.piece).getType();
        String pieceName = type.name().toLowerCase();
        boolean threat = move.destination.column == move.playerColumn && move.destination.row == move.playerRow;
        updatePredictionMarker(move.destination.column, move.destination.row, threat);
        updatePredictionSourceMarker(move.piece);
        String description = (threat ? "Danger: " : "Next: ") + pieceName + " " + moveName(move);
        viewModel.setEnemyPrediction(description);
        Gdx.app.log("ChessEngine", "Predicted black move " + moveName(move)
            + " (" + pieceName + ", searched " + enemyEngine.searchedNodes() + " nodes)");
    }

    private void updatePredictionMarker(int column, int row, boolean threat) {
        Color color = threat ? new Color(1f, 0.08f, 0.08f, 0.5f) : new Color(1f, 0.78f, 0.12f, 0.42f);
        predictionMarker = updateMarker(predictionMarker, column, row, color);
    }

    private void updatePredictionSourceMarker(Entity piece) {
        Vector2 position = entityCenter(piece);
        int column = cell(position.x, boardLeft);
        int row = cell(position.y, boardBottom);
        predictionSourceMarker = updateMarker(
            predictionSourceMarker,
            column,
            row,
            new Color(0.12f, 0.8f, 1f, 0.36f)
        );
    }

    private Entity updateMarker(Entity marker, int column, int row, Color color) {
        if (marker == null) {
            marker = getEngine().createEntity();
            marker.add(new Transform(
                new Vector2(boardLeft + column * CELL_SIZE, boardBottom + row * CELL_SIZE),
                3,
                new Vector2(CELL_SIZE, CELL_SIZE),
                new Vector2(1f, 1f),
                0f,
                0f
            ));
            marker.add(new Graphic(predictionRegion, color));
            getEngine().addEntity(marker);
            return marker;
        }
        Transform transform = Transform.MAPPER.get(marker);
        transform.getPosition().set(boardLeft + column * CELL_SIZE, boardBottom + row * CELL_SIZE);
        Graphic.MAPPER.get(marker).getColor().set(color);
        return marker;
    }

    private void clearPredictionMarker() {
        if (predictionMarker != null) getEngine().removeEntity(predictionMarker);
        predictionMarker = null;
        clearPredictionSourceMarker();
    }

    private void clearPredictionSourceMarker() {
        if (predictionSourceMarker != null) getEngine().removeEntity(predictionSourceMarker);
        predictionSourceMarker = null;
    }

    private Entity findThreateningPiece(int column, int row, Entity capturedPiece) {
        Position position = createEnginePosition(column, row, capturedPiece);
        int targetSquare = row * BOARD_SIZE + column;
        for (Move move : position.legalMoves(Side.BLACK)) {
            if (move.to() == targetSquare) return pieceAt(move.fromFile(), move.fromRank());
        }
        return null;
    }

    private Square findNearestSafeSquare(int originColumn, int originRow) {
        return findNearestSquare(originColumn, originRow, true);
    }

    private Square findNearestEmptySquare(int originColumn, int originRow) {
        return findNearestSquare(originColumn, originRow, false);
    }

    private Square findNearestSquare(int originColumn, int originRow, boolean requireSafe) {
        for (int distance = 0; distance < BOARD_SIZE * 2; distance++) {
            for (int row = 0; row < BOARD_SIZE; row++) {
                for (int column = 0; column < BOARD_SIZE; column++) {
                    if (Math.abs(column - originColumn) + Math.abs(row - originRow) != distance) continue;
                    if (pieceAt(column, row) != null) continue;
                    if (requireSafe && findThreateningPiece(column, row, null) != null) continue;
                    return new Square(column, row);
                }
            }
        }
        return null;
    }

    private Entity pieceAt(int column, int row) {
        for (int i = 0; i < pieces.size(); i++) {
            Entity piece = pieces.get(i);
            Vector2 position = entityCenter(piece);
            if (cell(position.x, boardLeft) == column && cell(position.y, boardBottom) == row) return piece;
        }
        return null;
    }

    private boolean isOpen(int column, int row) {
        return column >= 0 && column < BOARD_SIZE && row >= 0 && row < BOARD_SIZE;
    }

    private boolean isOnBoard(Entity piece) {
        for (int i = 0; i < pieces.size(); i++) {
            if (pieces.get(i) == piece) return true;
        }
        return false;
    }

    private static String moveName(PlannedMove move) {
        return squareName(move.source.column, move.source.row) + squareName(move.destination.column, move.destination.row);
    }

    private static String squareName(int file, int rank) {
        return String.valueOf((char) ('a' + file)) + (rank + 1);
    }

    private record Square(int column, int row) {
    }

    private record PlannedMove(
        Entity piece,
        Square source,
        Square destination,
        int playerColumn,
        int playerRow
    ) {
    }

    private void moveEntity(Entity entity, int column, int row) {
        Physic physic = Physic.MAPPER.get(entity);
        Body body = physic.getBody();
        Transform transform = Transform.MAPPER.get(entity);
        target.set(boardLeft + column * CELL_SIZE, boardBottom + row * CELL_SIZE);
        delta.set(target).sub(transform.getPosition());
        Vector2 position = body.getPosition();
        body.setTransform(position.x + delta.x, position.y + delta.y, body.getAngle());
        body.setLinearVelocity(0f, 0f);
        body.setAngularVelocity(0f);
        physic.getPrevPosition().set(body.getPosition());
        transform.getPosition().set(body.getPosition());
    }

    private Vector2 entityCenter(Entity entity) {
        Transform transform = Transform.MAPPER.get(entity);
        return target.set(transform.getPosition()).add(
            transform.getSize().x * 0.5f,
            transform.getSize().y * 0.5f
        );
    }

    private int cell(float position, float boardOrigin) {
        return (int) Math.floor((position - boardOrigin) / CELL_SIZE);
    }

    private static FacingDirection rotate(FacingDirection direction, boolean clockwise) {
        return switch (direction) {
            case UP -> clockwise ? FacingDirection.RIGHT : FacingDirection.LEFT;
            case RIGHT -> clockwise ? FacingDirection.DOWN : FacingDirection.UP;
            case DOWN -> clockwise ? FacingDirection.LEFT : FacingDirection.RIGHT;
            case LEFT -> clockwise ? FacingDirection.UP : FacingDirection.DOWN;
        };
    }

    private static int[] vector(FacingDirection direction) {
        return switch (direction) {
            case UP -> new int[] {0, 1};
            case DOWN -> new int[] {0, -1};
            case LEFT -> new int[] {-1, 0};
            case RIGHT -> new int[] {1, 0};
        };
    }

    private void finish(boolean victory) {
        if (finished) return;
        finished = true;
        onCombatFinished.accept(victory);
    }
}
