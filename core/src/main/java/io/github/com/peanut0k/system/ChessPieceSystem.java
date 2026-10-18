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
import io.github.com.peanut0k.chessengine.ChessEngine;
import io.github.com.peanut0k.chessengine.Move;
import io.github.com.peanut0k.chessengine.Piece;
import io.github.com.peanut0k.chessengine.PieceType;
import io.github.com.peanut0k.chessengine.Position;
import io.github.com.peanut0k.chessengine.Side;
import io.github.com.peanut0k.component.ChessPiece;
import io.github.com.peanut0k.component.BoardMotion;
import io.github.com.peanut0k.component.Facing;
import io.github.com.peanut0k.component.Facing.FacingDirection;
import io.github.com.peanut0k.component.Graphic;
import io.github.com.peanut0k.component.Physic;
import io.github.com.peanut0k.component.Player;
import io.github.com.peanut0k.component.Transform;
import io.github.com.peanut0k.input.Command;
import io.github.com.peanut0k.ldtk.LdtkMap;
import io.github.com.peanut0k.screen.ChessPieceFactory;
import io.github.com.peanut0k.ui.model.GameViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ChessPieceSystem extends EntitySystem {
    private static final int BOARD_SIZE = 8;
    private static final float CELL_SIZE = ChessPieceFactory.ARENA_CELL_SIZE;
    private static final float ENEMY_MOVE_DELAY = 1f;

    private final GameViewModel viewModel;
    private final TextureRegion predictionRegion;
    private final Consumer<Boolean> onCombatFinished;
    private final ChessEngine enemyEngine = new ChessEngine();
    private final Vector2 target = new Vector2();
    private final List<Entity> mouseDestinationMarkers = new ArrayList<>();
    private ImmutableArray<Entity> pieces;
    private ImmutableArray<Entity> players;
    private LdtkMap map;
    private float boardLeft;
    private float boardBottom;
    private boolean finished;
    private boolean lostLifeThisTurn;
    private boolean enemyTurnPending;
    private float enemyMoveDelay;
    private int enemySearchDepth = 3;
    private Entity pendingEnemyTurnPlayer;
    private Entity pendingCapturedPiece;
    private int lives = 2;
    private Entity predictionMarker;
    private Entity predictionSourceMarker;
    private Entity hintMarker;
    private PlannedMove predictedMove;
    private Entity mouseSelectedMarker;
    private int mouseSelectedSquare = -1;
    private boolean arenaActive;
    private boolean hintEarned;
    private boolean hintAvailable;
    private boolean doubleMoveEarned;
    private boolean doubleMoveAvailable;
    private int doubleMoveActionsRemaining;

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
        clearMouseSelection();
        clearPredictionMarker();
        clearHintMarker();
        predictedMove = null;
        enemyTurnPending = false;
        enemyMoveDelay = 0f;
        pendingEnemyTurnPlayer = null;
        pendingCapturedPiece = null;
        this.map = map;
        boardLeft = ChessPieceFactory.ARENA_BOARD_LEFT;
        boardBottom = ChessPieceFactory.ARENA_BOARD_BOTTOM;
        finished = false;
        lostLifeThisTurn = false;
        hintAvailable = hintEarned;
        doubleMoveAvailable = doubleMoveEarned;
        doubleMoveActionsRemaining = 0;
        viewModel.setLives(lives);
        viewModel.setGameMessage("Capture the black king.");
        viewModel.setEnemyPrediction("Enemy move: preparing board");
    }

    public void setArenaActive(boolean active) {
        arenaActive = active;
        if (!active) {
            clearMouseSelection();
            clearHintMarker();
        }
    }

    public boolean isArenaActive() {
        return arenaActive && !finished;
    }

    public boolean isHintEarned() {
        return hintEarned;
    }

    public boolean isHintAvailable() {
        return hintAvailable;
    }

    public boolean isDoubleMoveEarned() {
        return doubleMoveEarned;
    }

    public boolean isDoubleMoveAvailable() {
        return doubleMoveAvailable;
    }

    public void unlockHint() {
        hintEarned = true;
    }

    public void unlockDoubleMove() {
        doubleMoveEarned = true;
    }

    public boolean activateAbility(ChessDuelAbility ability) {
        if (!isArenaActive() || enemyTurnPending
            || getEngine().getEntitiesFor(Family.all(BoardMotion.class).get()).size() > 0) return false;
        switch (ability) {
            case HINT -> {
                if (!hintAvailable) return false;
                hintAvailable = false;
                showBestPlayerMoveHint();
            }
            case DOUBLE_MOVE -> {
                if (!doubleMoveAvailable) return false;
                doubleMoveAvailable = false;
                doubleMoveActionsRemaining = 2;
                viewModel.setGameMessage("Double Move ready: make two actions before the enemy responds.");
            }
            case UNDO -> {
                return false;
            }
        }
        return true;
    }

    public void setOpponentElo(int elo) {
        enemySearchDepth = Math.min(3, ChessEngine.searchDepthForElo(elo));
    }

    @Override
    public void update(float deltaTime) {
        if (!enemyTurnPending || finished) return;
        enemyMoveDelay -= deltaTime;
        if (enemyMoveDelay > 0f) return;

        Entity player = pendingEnemyTurnPlayer;
        Entity capturedPiece = pendingCapturedPiece;
        enemyTurnPending = false;
        enemyMoveDelay = 0f;
        pendingEnemyTurnPlayer = null;
        pendingCapturedPiece = null;
        if (player == null || !isArenaActive()) return;

        takeEnemyTurn(player, capturedPiece);
        if (!finished) {
            if (!lostLifeThisTurn) viewModel.setGameMessage("Your turn.");
            updatePrediction();
        }
    }

    public void handleMouseClick(float worldX, float worldY) {
        if (!isArenaActive() || enemyTurnPending
            || getEngine().getEntitiesFor(Family.all(BoardMotion.class).get()).size() > 0
            || players.size() == 0) return;
        int square = squareAt(worldX, worldY);
        if (square < 0) {
            clearMouseSelection();
            return;
        }

        Entity player = players.first();
        Vector2 playerPosition = entityCenter(player);
        int playerColumn = cell(playerPosition.x, boardLeft);
        int playerRow = cell(playerPosition.y, boardBottom);
        int playerSquare = playerRow * BOARD_SIZE + playerColumn;

        if (mouseSelectedSquare < 0) {
            if (square != playerSquare) {
                viewModel.setGameMessage("Click your pawn to select it.");
                return;
            }
            selectMousePawn(playerSquare);
            return;
        }

        if (square == playerSquare) {
            clearMouseSelection();
            return;
        }
        Command action = mouseActionForSquare(player, playerColumn, playerRow, square);
        if (action == null) {
            viewModel.setGameMessage("Choose a highlighted square.");
            return;
        }

        clearMouseSelection();
        movePlayer(action);
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
        if (finished || enemyTurnPending || map == null || players.size() == 0) return false;
        if (getEngine().getEntitiesFor(Family.all(BoardMotion.class).get()).size() > 0) return false;
        clearMouseSelection();

        Entity player = players.first();
        Facing facing = Facing.MAPPER.get(player);
        FacingDirection direction = facing.getDirection();
        if (action == Command.LEFT || action == Command.RIGHT) {
            clearHintMarker();
            facing.setDirection(rotate(direction, action == Command.RIGHT));
            completePlayerAction(player, null);
            return true;
        }

        int[] forward = vector(direction);
        int[] left = {-forward[1], forward[0]};
        Vector2 playerPosition = entityCenter(player);
        int column = cell(playerPosition.x, boardLeft);
        int row = cell(playerPosition.y, boardBottom);
        if (action == Command.DOWN) {
            viewModel.setGameMessage("You cannot move backward in the arena.");
            return false;
        }
        int nextColumn;
        int nextRow;
        Entity capturedPiece = null;

        if (action == Command.UP) {
            nextColumn = column + forward[0];
            nextRow = row + forward[1];
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

        clearHintMarker();
        if (capturedPiece == null) moveEntity(player, nextColumn, nextRow);
        completePlayerAction(player, capturedPiece);
        return true;
    }

    private void completePlayerAction(Entity player, Entity capturedPiece) {
        if (doubleMoveActionsRemaining > 0) {
            doubleMoveActionsRemaining--;
            if (doubleMoveActionsRemaining > 0) {
                predictedMove = null;
                clearPredictionMarker();
                viewModel.setGameMessage("Double Move: one more action before the enemy responds.");
                viewModel.setEnemyPrediction("Make your second action.");
                return;
            }
        }
        scheduleEnemyTurn(player, capturedPiece);
        viewModel.setGameMessage("Opponent is thinking...");
    }

    private void scheduleEnemyTurn(Entity player, Entity capturedPiece) {
        enemyTurnPending = true;
        enemyMoveDelay = ENEMY_MOVE_DELAY;
        pendingEnemyTurnPlayer = player;
        pendingCapturedPiece = capturedPiece;
        viewModel.setEnemyPrediction("Opponent moves in 1 second...");
    }

    private void takeEnemyTurn(Entity player, Entity capturedPiece) {
        lostLifeThisTurn = false;
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
            moveEntity(plannedMove.piece, playerColumn, playerRow);
            loseLife(player, playerColumn, playerRow);
            return;
        }
        moveEntity(plannedMove.piece, plannedMove.destination.column, plannedMove.destination.row);
    }

    private void loseLife(Entity player, int column, int row) {
        lives = Math.max(0, lives - 1);
        viewModel.setLives(lives);
        lostLifeThisTurn = true;
        if (lives <= 0) {
            viewModel.setGameMessage("You were captured and lost your final life.");
            finish(false);
            return;
        }
        Square respawn = findNearestSafeSquare(column, row);
        if (respawn == null) respawn = findNearestEmptySquare(column, row);
        if (respawn != null) moveEntity(player, respawn.column, respawn.row);
        viewModel.setGameMessage("Captured immediately! Respawned with " + lives + " lives remaining.");
    }

    private PlannedMove findEnemyMove(Entity player, Entity capturedPiece) {
        Vector2 playerPosition = entityCenter(player);
        int playerColumn = cell(playerPosition.x, boardLeft);
        int playerRow = cell(playerPosition.y, boardBottom);
        Position position = createEnginePosition(playerColumn, playerRow, capturedPiece);
        Move move = enemyEngine.chooseMove(position, Side.BLACK, enemySearchDepth);
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

    private void showBestPlayerMoveHint() {
        if (players == null || players.size() == 0) {
            throw new IllegalStateException("Cannot calculate a pawn hint without a player entity");
        }
        Entity player = players.first();
        Vector2 playerPosition = entityCenter(player);
        int playerColumn = cell(playerPosition.x, boardLeft);
        int playerRow = cell(playerPosition.y, boardBottom);
        FacingDirection direction = Facing.MAPPER.get(player).getDirection();
        Position position = createPlayerHintPosition(playerColumn, playerRow, direction);
        Square enginePlayer = toEngineCoordinates(playerColumn, playerRow, direction);
        Move move = enemyEngine.chooseMove(position, Side.WHITE, enemySearchDepth, candidate -> {
            if (candidate.fromFile() != enginePlayer.column || candidate.fromRank() != enginePlayer.row
                || candidate.promotion() != null) return false;
            Square target = fromEngineCoordinates(candidate.toFile(), candidate.toRank(), direction);
            return mouseActionForSquare(
                player, playerColumn, playerRow, target.row * BOARD_SIZE + target.column) != null;
        });
        if (move == null) {
            viewModel.setGameMessage("The engine found no legal pawn move.");
            return;
        }
        Square destination = fromEngineCoordinates(move.toFile(), move.toRank(), direction);
        Command action = mouseActionForSquare(
            player, playerColumn, playerRow, destination.row * BOARD_SIZE + destination.column);
        if (action == null) {
            throw new IllegalStateException("The engine suggested a move the pawn cannot make in the arena");
        }
        hintMarker = updateMarker(hintMarker, destination.column, destination.row,
            new Color(0.2f, 1f, 0.35f, 0.65f));
        viewModel.setGameMessage("Engine's best move: " + playerActionName(action) + ".");
        viewModel.setEnemyPrediction("Green square: engine's recommended destination.");
    }

    private Position createPlayerHintPosition(int playerColumn, int playerRow, FacingDirection direction) {
        Position position = new Position();
        position.setSideToMove(Side.WHITE);
        for (int i = 0; i < pieces.size(); i++) {
            Entity entity = pieces.get(i);
            Vector2 piecePosition = entityCenter(entity);
            int column = cell(piecePosition.x, boardLeft);
            int row = cell(piecePosition.y, boardBottom);
            Square mapped = toEngineCoordinates(column, row, direction);
            ChessPiece.Type pieceType = ChessPiece.MAPPER.get(entity).getType();
            position.putPiece(mapped.column, mapped.row, new Piece(Side.BLACK, toEngineType(pieceType)));
        }
        Square mappedPlayer = toEngineCoordinates(playerColumn, playerRow, direction);
        position.putPiece(mappedPlayer.column, mappedPlayer.row, new Piece(Side.WHITE, PieceType.PAWN));
        return position;
    }

    private static Square toEngineCoordinates(int column, int row, FacingDirection direction) {
        return switch (direction) {
            case UP -> new Square(column, row);
            case RIGHT -> new Square(BOARD_SIZE - 1 - row, column);
            case DOWN -> new Square(BOARD_SIZE - 1 - column, BOARD_SIZE - 1 - row);
            case LEFT -> new Square(row, BOARD_SIZE - 1 - column);
        };
    }

    private static Square fromEngineCoordinates(int column, int row, FacingDirection direction) {
        return switch (direction) {
            case UP -> new Square(column, row);
            case RIGHT -> new Square(row, BOARD_SIZE - 1 - column);
            case DOWN -> new Square(BOARD_SIZE - 1 - column, BOARD_SIZE - 1 - row);
            case LEFT -> new Square(BOARD_SIZE - 1 - row, column);
        };
    }

    private static String playerActionName(Command action) {
        return switch (action) {
            case UP -> "move forward";
            case CAPTURE_LEFT -> "capture diagonally left";
            case CAPTURE_RIGHT -> "capture diagonally right";
            default -> throw new IllegalStateException("Unexpected engine hint action: " + action);
        };
    }

    private void clearHintMarker() {
        if (hintMarker != null) getEngine().removeEntity(hintMarker);
        hintMarker = null;
    }

    private static PieceType toEngineType(ChessPiece.Type type) {
        return PieceType.valueOf(type.name());
    }

    private void updatePrediction() {
        if (finished || players == null || players.size() == 0) return;
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

    private void selectMousePawn(int square) {
        mouseSelectedSquare = square;
        mouseSelectedMarker = createMouseMarker(square, new Color(0.2f, 0.85f, 0.35f, 0.5f));
        Entity player = players.first();
        Vector2 playerPosition = entityCenter(player);
        int column = cell(playerPosition.x, boardLeft);
        int row = cell(playerPosition.y, boardBottom);
        FacingDirection facing = Facing.MAPPER.get(player).getDirection();
        int[] forward = vector(facing);
        int[] left = {-forward[1], forward[0]};

        addMouseDestination(column + forward[0], row + forward[1], isEmpty(column + forward[0], row + forward[1]));
        addMouseDestination(column + left[0], row + left[1], isOpen(column + left[0], row + left[1]));
        addMouseDestination(column - left[0], row - left[1], isOpen(column - left[0], row - left[1]));

        Entity leftEnemy = pieceAt(column + forward[0] + left[0], row + forward[1] + left[1]);
        Entity rightEnemy = pieceAt(column + forward[0] - left[0], row + forward[1] - left[1]);
        if (leftEnemy != null) addMouseDestination(column + forward[0] + left[0], row + forward[1] + left[1], true);
        if (rightEnemy != null) addMouseDestination(column + forward[0] - left[0], row + forward[1] - left[1], true);
        viewModel.setGameMessage("Choose a highlighted square.");
    }

    private Command mouseActionForSquare(Entity player, int column, int row, int square) {
        int targetColumn = square % BOARD_SIZE;
        int targetRow = square / BOARD_SIZE;
        int deltaColumn = targetColumn - column;
        int deltaRow = targetRow - row;
        FacingDirection facing = Facing.MAPPER.get(player).getDirection();
        int[] forward = vector(facing);
        int[] left = {-forward[1], forward[0]};
        if (deltaColumn == forward[0] && deltaRow == forward[1]) return Command.UP;
        if (deltaColumn == left[0] && deltaRow == left[1]) return Command.LEFT;
        if (deltaColumn == -left[0] && deltaRow == -left[1]) return Command.RIGHT;
        if (deltaColumn == forward[0] + left[0] && deltaRow == forward[1] + left[1]
            && pieceAt(targetColumn, targetRow) != null) return Command.CAPTURE_LEFT;
        if (deltaColumn == forward[0] - left[0] && deltaRow == forward[1] - left[1]
            && pieceAt(targetColumn, targetRow) != null) return Command.CAPTURE_RIGHT;
        return null;
    }

    private void addMouseDestination(int column, int row, boolean allowed) {
        if (allowed && isOpen(column, row)) {
            mouseDestinationMarkers.add(createMouseMarker(
                row * BOARD_SIZE + column,
                new Color(0.25f, 0.75f, 1f, 0.42f)
            ));
        }
    }

    private boolean isEmpty(int column, int row) {
        return isOpen(column, row) && pieceAt(column, row) == null;
    }

    private Entity createMouseMarker(int square, Color color) {
        int column = square % BOARD_SIZE;
        int row = square / BOARD_SIZE;
        Entity marker = getEngine().createEntity();
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

    private void clearMouseSelection() {
        mouseSelectedSquare = -1;
        if (mouseSelectedMarker != null) getEngine().removeEntity(mouseSelectedMarker);
        mouseSelectedMarker = null;
        for (Entity marker : mouseDestinationMarkers) getEngine().removeEntity(marker);
        mouseDestinationMarkers.clear();
    }

    private Entity findThreateningPiece(int column, int row) {
        Position position = createEnginePosition(column, row, null);
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
                    if (requireSafe && findThreateningPiece(column, row) != null) continue;
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
        target.set(boardLeft + column * CELL_SIZE, boardBottom + row * CELL_SIZE);
        BoardMotion.move(entity, target);
    }

    public void awardLife() {
        lives++;
        viewModel.setLives(lives);
    }

    public void resetLives() {
        lives = 2;
        viewModel.setLives(lives);
    }

    public void resetProgression() {
        resetLives();
        hintEarned = false;
        hintAvailable = false;
        doubleMoveEarned = false;
        doubleMoveAvailable = false;
        doubleMoveActionsRemaining = 0;
        clearHintMarker();
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

    private int squareAt(float worldX, float worldY) {
        int column = cell(worldX, boardLeft);
        int row = cell(worldY, boardBottom);
        if (!isOpen(column, row)) return -1;
        return row * BOARD_SIZE + column;
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
            case UP -> new int[]{0, 1};
            case DOWN -> new int[]{0, -1};
            case LEFT -> new int[]{-1, 0};
            case RIGHT -> new int[]{1, 0};
        };
    }

    private void finish(boolean victory) {
        if (finished) return;
        finished = true;
        enemyTurnPending = false;
        enemyMoveDelay = 0f;
        pendingEnemyTurnPlayer = null;
        pendingCapturedPiece = null;
        onCombatFinished.accept(victory);
    }
}
