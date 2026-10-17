package io.github.com.quillraven.system;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import io.github.com.quillraven.component.ChessPiece;
import io.github.com.quillraven.component.Facing;
import io.github.com.quillraven.component.Facing.FacingDirection;
import io.github.com.quillraven.component.Graphic;
import io.github.com.quillraven.component.Physic;
import io.github.com.quillraven.component.Player;
import io.github.com.quillraven.component.Transform;
import io.github.com.quillraven.input.Command;
import io.github.com.quillraven.ldtk.LdtkMap;
import io.github.com.quillraven.ui.model.GameViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ChessPieceSystem extends EntitySystem {
    private static final int BOARD_SIZE = 8;
    private static final float CELL_SIZE = 16f;
    private static final int[][] ROOK_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] BISHOP_DIRECTIONS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] QUEEN_DIRECTIONS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    private static final int[][] KNIGHT_MOVES = {
        {1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
    };

    private final GameViewModel viewModel;
    private final TextureRegion predictionRegion;
    private final Consumer<Boolean> onCombatFinished;
    private final Vector2 target = new Vector2();
    private final Vector2 delta = new Vector2();
    private ImmutableArray<Entity> pieces;
    private ImmutableArray<Entity> players;
    private LdtkMap map;
    private float boardLeft;
    private float boardBottom;
    private int nextEnemyTurn;
    private boolean finished;
    private boolean lostLifeThisTurn;
    private int lives = 2;
    private Entity predictionMarker;
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
        pendingThreat = false;
        this.map = map;
        boardLeft = (map.getWidth() - BOARD_SIZE * CELL_SIZE) * 0.5f;
        boardBottom = (map.getHeight() - BOARD_SIZE * CELL_SIZE) * 0.5f;
        nextEnemyTurn = 0;
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
                return;
            }
            pendingThreat = false;
        }

        PlannedMove plannedMove = findEnemyMove(player, capturedPiece);
        if (plannedMove == null) {
            viewModel.setEnemyPrediction("Enemy move: no legal move");
            clearPredictionMarker();
            return;
        }
        nextEnemyTurn = (plannedMove.pieceIndex + 1) % pieces.size();
        if (plannedMove.destination.column == plannedMove.playerColumn
            && plannedMove.destination.row == plannedMove.playerRow) {
            pendingThreat = true;
            pendingCaptureColumn = plannedMove.playerColumn;
            pendingCaptureRow = plannedMove.playerRow;
            viewModel.setGameMessage("A black piece threatens your square. Move to escape!");
            return;
        }
        moveEntity(plannedMove.piece, plannedMove.destination.column, plannedMove.destination.row);
    }

    private PlannedMove findEnemyMove(Entity player, Entity capturedPiece) {
        if (pieces.size() == 0) return null;
        Vector2 playerPosition = entityCenter(player);
        int playerColumn = cell(playerPosition.x, boardLeft);
        int playerRow = cell(playerPosition.y, boardBottom);
        PlannedMove firstAvailableMove = null;
        for (int offset = 0; offset < pieces.size(); offset++) {
            int index = (nextEnemyTurn + offset) % pieces.size();
            Entity enemy = pieces.get(index);
            if (enemy == capturedPiece) continue;
            Vector2 enemyPosition = entityCenter(enemy);
            int column = cell(enemyPosition.x, boardLeft);
            int row = cell(enemyPosition.y, boardBottom);
            List<Square> moves = legalMoves(enemy, column, row, playerColumn, playerRow);
            Square bestMove = chooseMove(moves, playerColumn, playerRow);
            if (bestMove != null) {
                PlannedMove plannedMove = new PlannedMove(enemy, index, bestMove, playerColumn, playerRow);
                if (bestMove.column == playerColumn && bestMove.row == playerRow) return plannedMove;
                if (firstAvailableMove == null) firstAvailableMove = plannedMove;
            }
        }
        return firstAvailableMove;
    }

    private Square chooseMove(List<Square> moves, int playerColumn, int playerRow) {
        Square bestMove = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Square move : moves) {
            if (move.column == playerColumn && move.row == playerRow) return move;
            int distance = Math.abs(move.column - playerColumn) + Math.abs(move.row - playerRow);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMove = move;
            }
        }
        return bestMove;
    }

    private List<Square> legalMoves(Entity piece, int column, int row, int playerColumn, int playerRow) {
        List<Square> moves = new ArrayList<>();
        ChessPiece.Type type = ChessPiece.MAPPER.get(piece).getType();
        switch (type) {
            case PAWN -> {
                int forwardRow = row - 1;
                boolean forwardOpen = isOpen(column, forwardRow)
                    && pieceAt(column, forwardRow) == null
                    && !(column == playerColumn && forwardRow == playerRow);
                if (forwardOpen) {
                    moves.add(new Square(column, forwardRow));
                    int doubleStepRow = row - 2;
                    if (row == BOARD_SIZE - 2 && isOpen(column, doubleStepRow)
                        && pieceAt(column, doubleStepRow) == null
                        && !(column == playerColumn && doubleStepRow == playerRow)) {
                        moves.add(new Square(column, doubleStepRow));
                    }
                }
                addPlayerCapture(moves, column - 1, forwardRow, playerColumn, playerRow);
                addPlayerCapture(moves, column + 1, forwardRow, playerColumn, playerRow);
            }
            case KNIGHT -> {
                for (int[] offset : KNIGHT_MOVES) {
                    addStep(moves, column + offset[0], row + offset[1], playerColumn, playerRow);
                }
            }
            case KING -> addSteps(moves, column, row, playerColumn, playerRow, QUEEN_DIRECTIONS);
            case ROOK -> addSlidingMoves(moves, column, row, playerColumn, playerRow, ROOK_DIRECTIONS);
            case BISHOP -> addSlidingMoves(moves, column, row, playerColumn, playerRow, BISHOP_DIRECTIONS);
            case QUEEN -> addSlidingMoves(moves, column, row, playerColumn, playerRow, QUEEN_DIRECTIONS);
        }
        return moves;
    }

    private void addSteps(List<Square> moves, int column, int row, int playerColumn, int playerRow, int[][] directions) {
        for (int[] direction : directions) {
            addStep(moves, column + direction[0], row + direction[1], playerColumn, playerRow);
        }
    }

    private void addStep(List<Square> moves, int column, int row, int playerColumn, int playerRow) {
        if (!isOpen(column, row)) return;
        if (column == playerColumn && row == playerRow) {
            moves.add(new Square(column, row));
        } else if (pieceAt(column, row) == null) {
            moves.add(new Square(column, row));
        }
    }

    private void addPlayerCapture(List<Square> moves, int column, int row, int playerColumn, int playerRow) {
        if (isOpen(column, row) && column == playerColumn && row == playerRow) {
            moves.add(new Square(column, row));
        }
    }

    private void addSlidingMoves(
        List<Square> moves,
        int column,
        int row,
        int playerColumn,
        int playerRow,
        int[][] directions
    ) {
        for (int[] direction : directions) {
            int nextColumn = column + direction[0];
            int nextRow = row + direction[1];
            while (isOpen(nextColumn, nextRow)) {
                if (nextColumn == playerColumn && nextRow == playerRow) {
                    moves.add(new Square(nextColumn, nextRow));
                    break;
                }
                if (pieceAt(nextColumn, nextRow) != null) break;
                moves.add(new Square(nextColumn, nextRow));
                nextColumn += direction[0];
                nextRow += direction[1];
            }
        }
    }

    private void updatePrediction() {
        if (finished || players == null || players.size() == 0) return;
        if (pendingThreat) {
            updatePredictionMarker(pendingCaptureColumn, pendingCaptureRow, true);
            viewModel.setEnemyPrediction("Danger: enemy threatens your square!");
            viewModel.setGameMessage("A black piece threatens your square. Move to escape!");
            return;
        }
        PlannedMove move = findEnemyMove(players.first(), null);
        if (move == null) {
            viewModel.setEnemyPrediction("Enemy move: no legal move");
            clearPredictionMarker();
            return;
        }
        ChessPiece.Type type = ChessPiece.MAPPER.get(move.piece).getType();
        String pieceName = type.name().toLowerCase();
        String squareName = String.valueOf((char) ('a' + move.destination.column))
            + (move.destination.row + 1);
        boolean threat = move.destination.column == move.playerColumn && move.destination.row == move.playerRow;
        updatePredictionMarker(move.destination.column, move.destination.row, threat);
        viewModel.setEnemyPrediction((threat ? "Danger: " : "Next: ") + pieceName + " to " + squareName);
    }

    private void updatePredictionMarker(int column, int row, boolean threat) {
        if (predictionMarker == null) {
            predictionMarker = getEngine().createEntity();
            predictionMarker.add(new Transform(
                new Vector2(boardLeft + column * CELL_SIZE, boardBottom + row * CELL_SIZE),
                3,
                new Vector2(CELL_SIZE, CELL_SIZE),
                new Vector2(1f, 1f),
                0f,
                0f
            ));
            predictionMarker.add(new Graphic(
                predictionRegion,
                threat ? new Color(1f, 0.08f, 0.08f, 0.5f) : new Color(1f, 0.78f, 0.12f, 0.42f)
            ));
            getEngine().addEntity(predictionMarker);
            return;
        }
        Transform transform = Transform.MAPPER.get(predictionMarker);
        transform.getPosition().set(boardLeft + column * CELL_SIZE, boardBottom + row * CELL_SIZE);
        Graphic.MAPPER.get(predictionMarker).getColor().set(
            threat ? new Color(1f, 0.08f, 0.08f, 0.5f) : new Color(1f, 0.78f, 0.12f, 0.42f)
        );
    }

    private void clearPredictionMarker() {
        if (predictionMarker == null) return;
        getEngine().removeEntity(predictionMarker);
        predictionMarker = null;
    }

    private Entity findThreateningPiece(int column, int row, Entity capturedPiece) {
        for (int i = 0; i < pieces.size(); i++) {
            Entity piece = pieces.get(i);
            if (piece == capturedPiece) continue;
            Vector2 position = entityCenter(piece);
            int pieceColumn = cell(position.x, boardLeft);
            int pieceRow = cell(position.y, boardBottom);
            List<Square> moves = legalMoves(piece, pieceColumn, pieceRow, column, row);
            for (Square move : moves) {
                if (move.column == column && move.row == row) return piece;
            }
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

    private record Square(int column, int row) {
    }

    private record PlannedMove(Entity piece, int pieceIndex, Square destination, int playerColumn, int playerRow) {
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
