package io.github.com.peanut0k.system;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import io.github.com.peanut0k.chessengine.ChessEngine;
import io.github.com.peanut0k.chessengine.Move;
import io.github.com.peanut0k.chessengine.Piece;
import io.github.com.peanut0k.chessengine.PieceType;
import io.github.com.peanut0k.chessengine.Position;
import io.github.com.peanut0k.chessengine.Side;
import io.github.com.peanut0k.component.BoardMotion;
import io.github.com.peanut0k.component.Graphic;
import io.github.com.peanut0k.component.Transform;
import io.github.com.peanut0k.input.Command;
import io.github.com.peanut0k.screen.ChessPieceFactory;
import io.github.com.peanut0k.ui.model.GameViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class ChessDuelSystem {
    public static final int PUZZLE_COUNT = 3;
    private static final int BOARD_SIZE = 8;
    private static final float CELL_SIZE = 16f;
    private static final int BOARD_Z = 4;
    private static final int PIECE_Z = 5;
    private static final int MARKER_Z = 6;
    private static final float OPPONENT_MOVE_DELAY = 1f;
    private static final String PIECE_GUIDE = String.join("\n",
        "PIECES (White moves up)",
        "Pawn: forward 1 (2 at start), captures diagonally; promotes.",
        "Knight: L-jump over pieces. Bishop: diagonal. Rook: straight.",
        "Queen: bishop + rook moves. King: one safe square; castling uses a rook.");
    private static final List<List<Puzzle>> PUZZLES = List.of(
        puzzleVariants(
            new Puzzle("8/8/8/8/8/8/8/k1KQ4 w - - 0 1", 2, "MATE (EASY)"),
            new Puzzle("8/8/8/8/8/8/8/k1K1Q3 w - - 0 1", 2, "MATE (EASY)"),
            new Puzzle("8/8/8/8/8/8/8/k1K2Q2 w - - 0 1", 2, "MATE (EASY)")
        ),
        puzzleVariants(
            new Puzzle("6k1/8/8/8/8/8/8/KQ3R2 w - - 0 1", 2, "MATE (MEDIUM)"),
            new Puzzle("8/8/8/7k/8/8/8/KQ4R1 w - - 0 1", 2, "MATE (MEDIUM)"),
            new Puzzle("7k/8/8/8/8/8/8/KQ4R1 w - - 0 1", 2, "MATE (MEDIUM)")
        ),
        puzzleVariants(
            new Puzzle("8/8/8/8/8/8/8/k1KQ4 w - - 0 1", 3, "MATE (CHALLENGE)"),
            new Puzzle("8/8/8/8/8/8/8/k1K1Q3 w - - 0 1", 3, "MATE (CHALLENGE)"),
            new Puzzle("8/8/8/8/8/8/8/k1K2Q2 w - - 0 1", 3, "MATE (CHALLENGE)")
        )
    );
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
    private final int[] lastPuzzleVariant = {-1, -1, -1};

    private Engine engine;
    private Position position;
    private Entity boardEntity;
    private Entity cursorMarker;
    private Entity selectedMarker;
    private int cursorSquare = 12;
    private int selectedSquare = -1;
    private int playerMovesUsed;
    private int movesBeforeUndo;
    private float opponentMoveDelay;
    private Puzzle puzzle;
    private int puzzleIndex;
    private String undoFen;
    private float boardLeft;
    private float boardBottom;
    private boolean active;
    private boolean undoAvailable;
    private boolean turnInProgress;
    private boolean hasUndoSnapshot;

    public ChessDuelSystem(ChessPieceFactory pieceFactory, GameViewModel viewModel,
                           CameraSystem cameraSystem,
                           Consumer<Result> onFinished) {
        this.pieceFactory = pieceFactory;
        this.viewModel = viewModel;
        this.cameraSystem = cameraSystem;
        this.onFinished = onFinished;
    }

    public void start(Engine engine, Vector2 playerPosition, String challengerName,
                      int puzzleIndex) {
        if (active) throw new IllegalStateException("A chess duel is already active");
        if (puzzleIndex < 0 || puzzleIndex >= PUZZLES.size()) {
            throw new IllegalArgumentException("No chess puzzle is configured at progression index " + puzzleIndex);
        }
        List<Puzzle> variants = PUZZLES.get(puzzleIndex);
        int previousVariant = lastPuzzleVariant[puzzleIndex];
        int variant = ThreadLocalRandom.current().nextInt(
            previousVariant < 0 ? variants.size() : variants.size() - 1);
        if (previousVariant >= 0 && variant >= previousVariant) variant++;
        lastPuzzleVariant[puzzleIndex] = variant;
        puzzle = variants.get(variant);
        this.engine = engine;
        this.puzzleIndex = puzzleIndex;
        opponentMoveDelay = 0f;
        playerMovesUsed = 0;
        movesBeforeUndo = 0;
        undoFen = null;
        undoAvailable = true;
        turnInProgress = false;
        hasUndoSnapshot = false;
        position = Position.fromFen(puzzle.fen);
        cursorSquare = 12;
        selectedSquare = -1;
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
        viewModel.setControls(puzzleGuide());
        viewModel.setGameMessage(challengerName + " has a chess puzzle for you.");
        viewModel.setEnemyPrediction("Checkmate Black in " + puzzle.maxMoves + " moves.");
    }

    public boolean isActive() {
        return active;
    }

    public int getPuzzleIndex() {
        return puzzleIndex;
    }

    public boolean isUndoAvailable() {
        return undoAvailable && hasUndoSnapshot;
    }

    public boolean isUndoSpent() {
        return !undoAvailable;
    }

    public boolean activateAbility(ChessDuelAbility ability) {
        if (!active) return false;
        switch (ability) {
            case DOUBLE_MOVE, HINT -> {
                return false;
            }
            case UNDO -> {
                if (!isUndoAvailable()) return false;
                undoAvailable = false;
                restoreUndoPosition();
            }
        }
        return true;
    }

    public void handleCommand(Command command) {
        if (!active) return;
        if (command == Command.CANCEL) {
            clearSelection();
        } else if (command == Command.SURRENDER) {
            viewModel.setGameMessage("You surrendered the duel.");
            end(Result.LOSS);
        }
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
        if (!active || opponentMoveDelay > 0f || piecesAreMoving()) return;
        int square = squareAt(worldX, worldY);
        if (square < 0) {
            clearSelection();
            return;
        }
        cursorSquare = square;
        setMarkerSquare(cursorMarker, square);
        selectOrMove(square);
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
        opponentMoveDelay = 0f;
        clearSelection();
        if (cursorMarker != null) engine.removeEntity(cursorMarker);
        cursorMarker = null;
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
                viewModel.setEnemyPrediction("Choose a White piece.");
                return;
            }
            selectedSquare = square;
            selectedMarker = createMarker(selectedSquare, SELECTED_COLOR);
            showLegalDestinations(selectedSquare);
            viewModel.setEnemyPrediction("Choose a highlighted destination.");
            return;
        }

        Move attemptedMove = position.legalMoves().stream()
            .filter(move -> move.from() == selectedSquare && move.to() == square)
            .findFirst()
            .orElse(null);
        if (attemptedMove != null) {
            makeMove(attemptedMove);
            return;
        }

        Piece piece = position.pieceAt(square);
        if (piece != null && piece.side() == Side.WHITE) {
            clearSelection();
            selectOrMove(square);
        } else {
            viewModel.setEnemyPrediction("That move is not legal. Try again.");
        }
    }

    private void makeMove(Move move) {
        if (!turnInProgress) {
            undoFen = position.toFen();
            movesBeforeUndo = playerMovesUsed;
            hasUndoSnapshot = true;
            turnInProgress = true;
        }
        applyMove(move);
        clearSelection();
        if (finishIfTerminal()) return;
        playerMovesUsed++;
        if (playerMovesUsed >= puzzle.maxMoves) {
            viewModel.setGameMessage("Puzzle failed: no checkmate in " + puzzle.maxMoves + " moves. Try again.");
            end(Result.LOSS);
            return;
        }
        viewModel.setEnemyPrediction("Black is thinking...");
        opponentMoveDelay = OPPONENT_MOVE_DELAY;
    }

    public void update(float delta) {
        if (!active || opponentMoveDelay <= 0f) return;
        opponentMoveDelay -= delta;
        if (opponentMoveDelay > 0f) return;
        opponentMoveDelay = 0f;
        if (position.sideToMove() != Side.BLACK) {
            throw new IllegalStateException("Puzzle expected a black reply, but it is white to move");
        }
        Move response = opponent.chooseMove(position, Side.BLACK, 3);
        if (response == null) {
            finishIfTerminal();
            return;
        }
        applyMove(response);
        if (finishIfTerminal()) return;
        turnInProgress = false;
        viewModel.setEnemyPrediction("Your move " + (playerMovesUsed + 1) + " of " + puzzle.maxMoves + ".");
    }

    private void restoreUndoPosition() {
        if (undoFen == null) throw new IllegalStateException("Undo is available without a saved position");
        opponentMoveDelay = 0f;
        turnInProgress = false;
        clearSelection();
        for (Entity piece : pieces) {
            if (piece != null) engine.removeEntity(piece);
        }
        java.util.Arrays.fill(pieces, null);
        position = Position.fromFen(undoFen);
        playerMovesUsed = movesBeforeUndo;
        for (int square = 0; square < pieces.length; square++) {
            Piece piece = position.pieceAt(square);
            if (piece != null) createPiece(square, piece);
        }
        setMarkerSquare(cursorMarker, cursorSquare);
        viewModel.setEnemyPrediction("Your move " + (playerMovesUsed + 1) + " of " + puzzle.maxMoves + ".");
    }

    private boolean finishIfTerminal() {
        if (!position.legalMoves().isEmpty()) return false;
        if (position.isInCheck(position.sideToMove()) && position.sideToMove() == Side.BLACK) {
            viewModel.setGameMessage("Checkmate! Puzzle solved.");
            end(Result.WIN);
        } else {
            viewModel.setGameMessage("Puzzle failed. Try a different solution.");
            end(Result.LOSS);
        }
        return true;
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

    private String puzzleGuide() {
        String guide = "PUZZLE " + (puzzleIndex + 1) + "/" + PUZZLE_COUNT + " - "
            + puzzle.difficulty + " IN " + puzzle.maxMoves + "\n" + PIECE_GUIDE;
        return guide + "\nCLICK PIECE, THEN DESTINATION\nESC: DESELECT  |  BACKSPACE: GIVE UP";
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

    private static List<Puzzle> puzzleVariants(Puzzle... basePuzzles) {
        List<Puzzle> variants = new ArrayList<>(basePuzzles.length * 2);
        for (Puzzle puzzle : basePuzzles) {
            variants.add(puzzle);
            variants.add(new Puzzle(mirrorFen(puzzle.fen), puzzle.maxMoves, puzzle.difficulty));
        }
        return List.copyOf(variants);
    }

    private static String mirrorFen(String fen) {
        String[] fields = fen.split(" ");
        String[] ranks = fields[0].split("/");
        StringBuilder mirroredBoard = new StringBuilder();
        for (int rank = 0; rank < ranks.length; rank++) {
            if (rank > 0) mirroredBoard.append('/');
            char[] files = new char[8];
            int fileIndex = 0;
            for (char cell : ranks[rank].toCharArray()) {
                if (Character.isDigit(cell)) {
                    for (int empty = 0; empty < cell - '0'; empty++) files[fileIndex++] = '1';
                } else {
                    files[fileIndex++] = cell;
                }
            }
            int emptyCount = 0;
            for (int file = files.length - 1; file >= 0; file--) {
                char cell = files[file];
                if (cell == '1') {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        mirroredBoard.append(emptyCount);
                        emptyCount = 0;
                    }
                    mirroredBoard.append(cell);
                }
            }
            if (emptyCount > 0) mirroredBoard.append(emptyCount);
        }
        fields[0] = mirroredBoard.toString();
        return String.join(" ", fields);
    }

    private record Puzzle(String fen, int maxMoves, String difficulty) {
    }
}
