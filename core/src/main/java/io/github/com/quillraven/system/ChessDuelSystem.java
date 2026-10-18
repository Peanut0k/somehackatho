package io.github.com.quillraven.system;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import io.github.com.quillraven.chessengine.ChessEngine;
import io.github.com.quillraven.chessengine.Move;
import io.github.com.quillraven.chessengine.Piece;
import io.github.com.quillraven.chessengine.PieceType;
import io.github.com.quillraven.chessengine.Position;
import io.github.com.quillraven.chessengine.Side;
import io.github.com.quillraven.component.BoardMotion;
import io.github.com.quillraven.component.Graphic;
import io.github.com.quillraven.component.Transform;
import io.github.com.quillraven.input.Command;
import io.github.com.quillraven.screen.ChessPieceFactory;
import io.github.com.quillraven.ui.model.GameViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ChessDuelSystem {
    private static final int BOARD_SIZE = 8;
    private static final float CELL_SIZE = 16f;
    private static final int BOARD_Z = 4;
    private static final int PIECE_Z = 5;
    private static final int MARKER_Z = 6;
    private static final Color CURSOR_COLOR = new Color(1f, 0.85f, 0.2f, 0.48f);
    private static final Color SELECTED_COLOR = new Color(0.2f, 0.85f, 0.35f, 0.5f);
    private static final Color DESTINATION_COLOR = new Color(0.25f, 0.75f, 1f, 0.42f);

    private final ChessPieceFactory pieceFactory;
    private final GameViewModel viewModel;
    private final CameraSystem cameraSystem;
    private final Consumer<Result> onFinished;
    private final ChessEngine opponent = new ChessEngine();
    private final Entity[] pieces = new Entity[BOARD_SIZE * BOARD_SIZE];
    private final List<Entity> destinationMarkers = new ArrayList<>();

    private Engine engine;
    private Position position;
    private Entity boardEntity;
    private Entity cursorMarker;
    private Entity selectedMarker;
    private int cursorSquare = 12;
    private int selectedSquare = -1;
    private int searchDepth;
    private String challengerName;
    private float boardLeft;
    private float boardBottom;
    private boolean active;
    private boolean doubleMoveAvailable;
    private boolean doubleMoveArmed;
    private boolean bonusMovePending;

    public ChessDuelSystem(ChessPieceFactory pieceFactory, GameViewModel viewModel,
                           CameraSystem cameraSystem,
                           Consumer<Result> onFinished) {
        this.pieceFactory = pieceFactory;
        this.viewModel = viewModel;
        this.cameraSystem = cameraSystem;
        this.onFinished = onFinished;
    }

    public void start(Engine engine, Vector2 playerPosition, String challengerName,
                      int searchDepth, boolean doubleMoveUnlocked) {
        if (active) throw new IllegalStateException("A chess duel is already active");
        this.engine = engine;
        this.challengerName = challengerName;
        this.searchDepth = searchDepth;
        position = Position.standard();
        cursorSquare = 12;
        selectedSquare = -1;
        doubleMoveAvailable = doubleMoveUnlocked;
        doubleMoveArmed = false;
        bonusMovePending = false;
        active = true;

        Vector2 center = playerPosition.cpy().add(CELL_SIZE, CELL_SIZE);
        boardLeft = center.x - BOARD_SIZE * CELL_SIZE * 0.5f;
        boardBottom = center.y - BOARD_SIZE * CELL_SIZE * 0.5f;
        cameraSystem.setFixedTarget(center);

        boardEntity = engine.createEntity();
        boardEntity.add(new Transform(new Vector2(boardLeft, boardBottom), BOARD_Z,
            new Vector2(BOARD_SIZE * CELL_SIZE, BOARD_SIZE * CELL_SIZE),
            new Vector2(1f, 1f), 0f, 0f));
        boardEntity.add(new Graphic(pieceFactory.getBoardRegion(), Color.WHITE.cpy()));
        engine.addEntity(boardEntity);

        for (int square = 0; square < pieces.length; square++) {
            Piece piece = position.pieceAt(square);
            if (piece != null) createPiece(square, piece);
        }
        cursorMarker = createMarker(cursorSquare, CURSOR_COLOR);
        viewModel.setControls(doubleMoveAvailable
            ? "DUEL: LEFT-CLICK PIECE, THEN DESTINATION\nRIGHT-CLICK: DOUBLE MOVE ONCE"
            : "DUEL: LEFT-CLICK PIECE, THEN DESTINATION");
        viewModel.setGameMessage(challengerName + " challenges you. You play white.");
        viewModel.setEnemyPrediction("Your turn: select a piece.");
    }

    public boolean isActive() {
        return active;
    }

    public void handleCommand(Command command) {
        if (!active) return;
        if (command == Command.CANCEL) clearSelection();
    }

    public void handleMouseMove(float worldX, float worldY) {
        if (!active || piecesAreMoving()) return;
        int square = squareAt(worldX, worldY);
        if (square >= 0) {
            cursorSquare = square;
            setMarkerSquare(cursorMarker, square);
        }
    }

    public void handleMouseClick(float worldX, float worldY) {
        if (!active || piecesAreMoving()) return;
        int square = squareAt(worldX, worldY);
        if (square < 0) {
            clearSelection();
            return;
        }
        cursorSquare = square;
        setMarkerSquare(cursorMarker, square);
        selectOrMove(square);
    }

    public void handleAbilityClick() {
        if (!active || piecesAreMoving()) return;
        if (!doubleMoveAvailable) {
            viewModel.setEnemyPrediction("Win a DOUBLE_MOVE reward to unlock this ability.");
            return;
        }
        if (doubleMoveArmed) {
            viewModel.setEnemyPrediction("Double move is armed for your next move.");
            return;
        }
        doubleMoveArmed = true;
        viewModel.setEnemyPrediction("Double move armed for your next turn.");
    }

    private boolean piecesAreMoving() {
        for (Entity piece : pieces) {
            if (piece != null && BoardMotion.MAPPER.get(piece) != null) return true;
        }
        return false;
    }

    public void end(Result result) {
        if (!active) return;
        active = false;
        clearSelection();
        if (cursorMarker != null) engine.removeEntity(cursorMarker);
        cursorMarker = null;
        doubleMoveAvailable = false;
        doubleMoveArmed = false;
        bonusMovePending = false;
        if (result == Result.LOSS) {
            onFinished.accept(result);
            return;
        }

        removeDuelBoard();
        cameraSystem.clearFixedTarget();
        this.onFinished.accept(result);
    }

    public void dismissFinishedDuel() {
        if (active) return;
        removeDuelBoard();
        cameraSystem.clearFixedTarget();
    }

    private void removeDuelBoard() {
        for (Entity piece : pieces) {
            if (piece != null) engine.removeEntity(piece);
        }
        if (boardEntity != null) engine.removeEntity(boardEntity);
        for (Entity marker : destinationMarkers) engine.removeEntity(marker);
        destinationMarkers.clear();
        java.util.Arrays.fill(pieces, null);
        boardEntity = null;
        selectedMarker = null;
    }

    private void selectOrMove(int square) {
        if (selectedSquare < 0) {
            Piece piece = position.pieceAt(square);
            if (piece == null || piece.side() != Side.WHITE) {
                viewModel.setEnemyPrediction("Select one of your white pieces.");
                return;
            }
            selectedSquare = square;
            selectedMarker = createMarker(selectedSquare, SELECTED_COLOR);
            showLegalDestinations(selectedSquare);
            viewModel.setEnemyPrediction("Choose a highlighted destination.");
            return;
        }

        for (Move move : position.legalMoves()) {
            if (move.from() == selectedSquare && move.to() == square) {
                makeMove(move);
                return;
            }
        }

        Piece piece = position.pieceAt(square);
        if (piece != null && piece.side() == Side.WHITE) {
            clearSelection();
            selectOrMove(square);
        } else {
            viewModel.setEnemyPrediction("That is not a legal move.");
        }
    }

    private void makeMove(Move move) {
        applyMove(move);
        clearSelection();
        if (finishIfGameOver()) return;

        if (doubleMoveArmed && !bonusMovePending) {
            doubleMoveAvailable = false;
            doubleMoveArmed = false;
            bonusMovePending = true;
            position.setSideToMove(Side.WHITE);
            viewModel.setEnemyPrediction("Double move: choose your second move before black.");
            return;
        }
        bonusMovePending = false;

        viewModel.setEnemyPrediction(challengerName + " is thinking...");
        Move response = opponent.chooseMove(position, Side.BLACK, searchDepth);
        if (response == null) {
            finishIfGameOver();
            return;
        }
        applyMove(response);
        if (finishIfGameOver()) return;
        viewModel.setEnemyPrediction(position.isInCheck(Side.WHITE)
            ? "Your king is in check. Your turn."
            : "Your turn.");
    }

    private void applyMove(Move move) {
        Piece movingPiece = position.pieceAt(move.from());
        if (movingPiece == null) throw new IllegalStateException("Legal move has no piece on its source square");
        int capturedSquare = move.to();
        if (movingPiece.type() == PieceType.PAWN
            && move.to() == position.enPassantSquare()
            && move.fromFile() != move.toFile()
            && position.pieceAt(move.to()) == null) {
            capturedSquare -= movingPiece.side().pawnDirection() * BOARD_SIZE;
        }
        removePiece(capturedSquare);

        Entity movingEntity = pieces[move.from()];
        if (movingEntity == null)
            throw new IllegalStateException("Chess piece entity is missing from its source square");
        pieces[move.from()] = null;
        pieces[move.to()] = movingEntity;
        BoardMotion.move(movingEntity, squarePosition(move.to()));

        if (move.promotion() != null) {
            Graphic.MAPPER.get(movingEntity)
                .setRegion(pieceFactory.getPieceRegion(move.promotion(), movingPiece.side()));
        }

        if (movingPiece.type() == PieceType.KING && Math.abs(move.toFile() - move.fromFile()) == 2) {
            int rank = move.fromRank();
            int rookFrom = rank * BOARD_SIZE + (move.toFile() > move.fromFile() ? 7 : 0);
            int rookTo = rank * BOARD_SIZE + (move.toFile() > move.fromFile() ? 5 : 3);
            Entity rook = pieces[rookFrom];
            if (rook == null) throw new IllegalStateException("Castling move has no rook on its starting square");
            pieces[rookFrom] = null;
            pieces[rookTo] = rook;
            BoardMotion.move(rook, squarePosition(rookTo));
        }

        position = position.apply(move);
    }

    private boolean finishIfGameOver() {
        List<Move> legalMoves = position.legalMoves();
        if (legalMoves.isEmpty()) {
            if (position.isInCheck(position.sideToMove())) {
                boolean playerWon = position.sideToMove() == Side.BLACK;
                viewModel.setGameMessage(playerWon
                    ? "Checkmate! You defeated " + challengerName + "."
                    : "Checkmate. " + challengerName + " wins.");
                end(playerWon ? Result.WIN : Result.LOSS);
            } else {
                viewModel.setGameMessage("Draw by stalemate.");
                end(Result.DRAW);
            }
            return true;
        }
        if (position.isDrawByFiftyMoveRule() || position.isInsufficientMaterial()) {
            viewModel.setGameMessage("The game is a draw.");
            end(Result.DRAW);
            return true;
        }
        return false;
    }

    private void createPiece(int square, Piece piece) {
        Entity entity = engine.createEntity();
        entity.add(new Transform(squarePosition(square), PIECE_Z,
            new Vector2(CELL_SIZE, CELL_SIZE), new Vector2(1f, 1f), 0f, 0f));
        entity.add(new Graphic(
            pieceFactory.getPieceRegion(piece.type(), piece.side()),
            pieceFactory.getPieceColor(piece.side())
        ));
        engine.addEntity(entity);
        pieces[square] = entity;
    }

    private Entity createMarker(int square, Color color) {
        Entity marker = engine.createEntity();
        marker.add(new Transform(squarePosition(square), MARKER_Z,
            new Vector2(CELL_SIZE, CELL_SIZE), new Vector2(1f, 1f), 0f, 0f));
        marker.add(new Graphic(pieceFactory.getPredictionRegion(), color.cpy()));
        engine.addEntity(marker);
        return marker;
    }

    private void showLegalDestinations(int from) {
        for (Move move : position.legalMoves()) {
            if (move.from() == from) destinationMarkers.add(createMarker(move.to(), DESTINATION_COLOR));
        }
    }

    private void clearSelection() {
        selectedSquare = -1;
        if (selectedMarker != null) engine.removeEntity(selectedMarker);
        selectedMarker = null;
        for (Entity marker : destinationMarkers) engine.removeEntity(marker);
        destinationMarkers.clear();
    }

    private void removePiece(int square) {
        Entity captured = pieces[square];
        if (captured == null) return;
        engine.removeEntity(captured);
        pieces[square] = null;
    }

    private void setMarkerSquare(Entity marker, int square) {
        if (marker != null) Transform.MAPPER.get(marker).getPosition().set(squarePosition(square));
    }

    private int squareAt(float worldX, float worldY) {
        int file = (int) Math.floor((worldX - boardLeft) / CELL_SIZE);
        int rank = (int) Math.floor((worldY - boardBottom) / CELL_SIZE);
        if (file < 0 || file >= BOARD_SIZE || rank < 0 || rank >= BOARD_SIZE) return -1;
        return rank * BOARD_SIZE + file;
    }

    private Vector2 squarePosition(int square) {
        int file = square % BOARD_SIZE;
        int rank = square / BOARD_SIZE;
        return new Vector2(
            boardLeft + file * CELL_SIZE,
            boardBottom + rank * CELL_SIZE
        );
    }

    public enum Result {
        WIN, LOSS, DRAW
    }
}
