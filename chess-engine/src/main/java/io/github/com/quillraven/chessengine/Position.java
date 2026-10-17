package io.github.com.quillraven.chessengine;

import java.util.ArrayList;
import java.util.List;

public final class Position {
    private static final int WHITE_KING_SIDE = 1;
    private static final int WHITE_QUEEN_SIDE = 2;
    private static final int BLACK_KING_SIDE = 4;
    private static final int BLACK_QUEEN_SIDE = 8;
    private static final PieceType[] PROMOTIONS = {
        PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT
    };
    private static final int[][] KNIGHT_STEPS = {
        {1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
    };
    private static final int[][] KING_STEPS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    private static final int[][] ROOK_RAYS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] BISHOP_RAYS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private final Piece[] board;
    private Side sideToMove;
    private int castlingRights;
    private int enPassantSquare;
    private int halfmoveClock;
    private int fullmoveNumber;

    public Position() {
        board = new Piece[64];
        sideToMove = Side.WHITE;
        castlingRights = 0;
        enPassantSquare = -1;
        fullmoveNumber = 1;
    }

    private Position(Position source) {
        board = source.board.clone();
        sideToMove = source.sideToMove;
        castlingRights = source.castlingRights;
        enPassantSquare = source.enPassantSquare;
        halfmoveClock = source.halfmoveClock;
        fullmoveNumber = source.fullmoveNumber;
    }

    public static Position standard() {
        return fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    public static Position fromFen(String fen) {
        if (fen == null) throw new IllegalArgumentException("FEN is required");
        String[] fields = fen.trim().split("\\s+");
        if (fields.length != 6) throw new IllegalArgumentException("FEN must contain six fields");

        Position position = new Position();
        String[] ranks = fields[0].split("/");
        if (ranks.length != 8) throw new IllegalArgumentException("FEN must contain eight ranks");
        for (int fenRank = 0; fenRank < 8; fenRank++) {
            int file = 0;
            for (char symbol : ranks[fenRank].toCharArray()) {
                if (Character.isDigit(symbol)) {
                    file += symbol - '0';
                } else {
                    if (file >= 8) throw new IllegalArgumentException("Invalid FEN rank: " + ranks[fenRank]);
                    position.board[(7 - fenRank) * 8 + file++] = pieceFromFen(symbol);
                }
            }
            if (file != 8) throw new IllegalArgumentException("Invalid FEN rank: " + ranks[fenRank]);
        }
        position.sideToMove = switch (fields[1]) {
            case "w" -> Side.WHITE;
            case "b" -> Side.BLACK;
            default -> throw new IllegalArgumentException("Invalid FEN side to move");
        };
        position.castlingRights = parseCastlingRights(fields[2]);
        position.enPassantSquare = fields[3].equals("-") ? -1 : parseSquare(fields[3]);
        position.halfmoveClock = parseNonNegative(fields[4], "halfmove clock");
        position.fullmoveNumber = Integer.parseInt(fields[5]);
        if (position.fullmoveNumber < 1) throw new IllegalArgumentException("Invalid fullmove number");
        return position;
    }

    public Piece pieceAt(int file, int rank) {
        if (!inside(file, rank)) return null;
        return board[index(file, rank)];
    }

    public Piece pieceAt(int square) {
        if (square < 0 || square >= 64) return null;
        return board[square];
    }

    public void putPiece(int file, int rank, Piece piece) {
        if (!inside(file, rank)) throw new IllegalArgumentException("Square is outside the board");
        board[index(file, rank)] = piece;
    }

    public void setSideToMove(Side sideToMove) {
        if (sideToMove == null) throw new IllegalArgumentException("Side to move is required");
        this.sideToMove = sideToMove;
    }

    public Side sideToMove() {
        return sideToMove;
    }

    public int enPassantSquare() {
        return enPassantSquare;
    }

    public boolean hasPieces(Side side) {
        for (Piece piece : board) {
            if (piece != null && piece.side() == side) return true;
        }
        return false;
    }

    public List<Move> legalMoves() {
        return legalMoves(sideToMove);
    }

    public List<Move> legalMoves(Side side) {
        if (side == null) throw new IllegalArgumentException("Move side is required");
        List<Move> pseudoMoves = new ArrayList<>();
        for (int square = 0; square < 64; square++) {
            Piece piece = board[square];
            if (piece == null || piece.side() != side) continue;
            int file = square % 8;
            int rank = square / 8;
            switch (piece.type()) {
                case PAWN -> generatePawnMoves(pseudoMoves, file, rank, side);
                case KNIGHT -> generateStepMoves(pseudoMoves, file, rank, side, KNIGHT_STEPS);
                case BISHOP -> generateSlidingMoves(pseudoMoves, file, rank, side, BISHOP_RAYS);
                case ROOK -> generateSlidingMoves(pseudoMoves, file, rank, side, ROOK_RAYS);
                case QUEEN -> {
                    generateSlidingMoves(pseudoMoves, file, rank, side, ROOK_RAYS);
                    generateSlidingMoves(pseudoMoves, file, rank, side, BISHOP_RAYS);
                }
                case KING -> {
                    generateStepMoves(pseudoMoves, file, rank, side, KING_STEPS);
                    generateCastlingMoves(pseudoMoves, file, rank, side);
                }
            }
        }

        List<Move> legalMoves = new ArrayList<>(pseudoMoves.size());
        for (Move move : pseudoMoves) {
            Position next = apply(move);
            if (!next.isInCheck(side)) legalMoves.add(move);
        }
        return legalMoves;
    }

    public Position apply(Move move) {
        Piece moving = board[move.from()];
        if (moving == null) throw new IllegalArgumentException("No piece on move origin");
        Position next = new Position(this);
        Piece captured = board[move.to()];
        int fromFile = move.fromFile();
        int fromRank = move.fromRank();
        int toFile = move.toFile();
        int toRank = move.toRank();
        next.board[move.from()] = null;

        if (moving.type() == PieceType.PAWN && move.to() == enPassantSquare
            && fromFile != toFile && captured == null) {
            int capturedPawnSquare = move.to() - moving.side().pawnDirection() * 8;
            captured = next.board[capturedPawnSquare];
            next.board[capturedPawnSquare] = null;
        }

        PieceType resultingType = move.promotion() == null ? moving.type() : move.promotion();
        next.board[move.to()] = new Piece(moving.side(), resultingType);
        if (moving.type() == PieceType.KING && Math.abs(toFile - fromFile) == 2) {
            int rookFromFile = toFile > fromFile ? 7 : 0;
            int rookToFile = toFile > fromFile ? toFile - 1 : toFile + 1;
            int rank = fromRank;
            next.board[index(rookToFile, rank)] = next.board[index(rookFromFile, rank)];
            next.board[index(rookFromFile, rank)] = null;
        }

        next.updateCastlingRights(move, moving, captured);
        next.enPassantSquare = moving.type() == PieceType.PAWN && Math.abs(toRank - fromRank) == 2
            ? index(fromFile, (fromRank + toRank) / 2)
            : -1;
        next.halfmoveClock = moving.type() == PieceType.PAWN || captured != null ? 0 : halfmoveClock + 1;
        next.fullmoveNumber = fullmoveNumber + (moving.side() == Side.BLACK ? 1 : 0);
        next.sideToMove = moving.side().opposite();
        return next;
    }

    public boolean isInCheck(Side side) {
        int kingSquare = -1;
        for (int square = 0; square < 64; square++) {
            Piece piece = board[square];
            if (piece != null && piece.side() == side && piece.type() == PieceType.KING) {
                kingSquare = square;
                break;
            }
        }
        return kingSquare >= 0 && isSquareAttacked(kingSquare, side.opposite());
    }

    public boolean isSquareAttacked(int square, Side bySide) {
        int file = square % 8;
        int rank = square / 8;
        int pawnSourceRank = rank - bySide.pawnDirection();
        for (int fileOffset : new int[] {-1, 1}) {
            int pawnFile = file - fileOffset;
            Piece pawn = pieceAt(pawnFile, pawnSourceRank);
            if (pawn != null && pawn.side() == bySide && pawn.type() == PieceType.PAWN) return true;
        }
        for (int[] step : KNIGHT_STEPS) {
            Piece piece = pieceAt(file + step[0], rank + step[1]);
            if (piece != null && piece.side() == bySide && piece.type() == PieceType.KNIGHT) return true;
        }
        for (int[] step : KING_STEPS) {
            Piece piece = pieceAt(file + step[0], rank + step[1]);
            if (piece != null && piece.side() == bySide && piece.type() == PieceType.KING) return true;
        }
        return rayAttacks(file, rank, bySide, ROOK_RAYS, PieceType.ROOK)
            || rayAttacks(file, rank, bySide, BISHOP_RAYS, PieceType.BISHOP);
    }

    public boolean isDrawByFiftyMoveRule() {
        return halfmoveClock >= 100;
    }

    public boolean isInsufficientMaterial() {
        int minors = 0;
        int bishopSquareColor = -1;
        for (int square = 0; square < 64; square++) {
            Piece piece = board[square];
            if (piece == null || piece.type() == PieceType.KING) continue;
            if (piece.type() == PieceType.PAWN || piece.type() == PieceType.ROOK || piece.type() == PieceType.QUEEN) {
                return false;
            }
            minors++;
            if (piece.type() == PieceType.BISHOP) {
                int color = (square % 8 + square / 8) % 2;
                if (bishopSquareColor >= 0 && bishopSquareColor != color) return false;
                bishopSquareColor = color;
            }
        }
        return minors <= 1 || bishopSquareColor >= 0 && minors == countPieces(PieceType.BISHOP);
    }

    public String toFen() {
        StringBuilder fen = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int emptyCount = 0;
            for (int file = 0; file < 8; file++) {
                Piece piece = pieceAt(file, rank);
                if (piece == null) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(toFen(piece));
                }
            }
            if (emptyCount > 0) fen.append(emptyCount);
            if (rank > 0) fen.append('/');
        }
        fen.append(sideToMove == Side.WHITE ? " w " : " b ");
        appendCastling(fen);
        fen.append(enPassantSquare < 0 ? " -" : " " + squareName(enPassantSquare));
        fen.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber);
        return fen.toString();
    }

    private void generatePawnMoves(List<Move> moves, int file, int rank, Side side) {
        int direction = side.pawnDirection();
        int nextRank = rank + direction;
        if (inside(file, nextRank) && pieceAt(file, nextRank) == null) {
            addPawnMove(moves, file, rank, file, nextRank);
            int initialRank = side == Side.WHITE ? 1 : 6;
            int doubleRank = rank + direction * 2;
            if (rank == initialRank && pieceAt(file, doubleRank) == null) {
                moves.add(new Move(index(file, rank), index(file, doubleRank), null));
            }
        }
        for (int fileOffset : new int[] {-1, 1}) {
            int targetFile = file + fileOffset;
            if (!inside(targetFile, nextRank)) continue;
            Piece target = pieceAt(targetFile, nextRank);
            int targetSquare = index(targetFile, nextRank);
            if ((target != null && target.side() != side && target.type() != PieceType.KING)
                || targetSquare == enPassantSquare) {
                addPawnMove(moves, file, rank, targetFile, nextRank);
            }
        }
    }

    private void addPawnMove(List<Move> moves, int fromFile, int fromRank, int toFile, int toRank) {
        if (toRank == 0 || toRank == 7) {
            for (PieceType promotion : PROMOTIONS) {
                moves.add(new Move(index(fromFile, fromRank), index(toFile, toRank), promotion));
            }
        } else {
            moves.add(new Move(index(fromFile, fromRank), index(toFile, toRank), null));
        }
    }

    private void generateStepMoves(List<Move> moves, int file, int rank, Side side, int[][] steps) {
        for (int[] step : steps) {
            int targetFile = file + step[0];
            int targetRank = rank + step[1];
            if (!inside(targetFile, targetRank)) continue;
            Piece target = pieceAt(targetFile, targetRank);
            if (target == null || target.side() != side && target.type() != PieceType.KING) {
                moves.add(new Move(index(file, rank), index(targetFile, targetRank), null));
            }
        }
    }

    private void generateSlidingMoves(List<Move> moves, int file, int rank, Side side, int[][] rays) {
        for (int[] ray : rays) {
            int targetFile = file + ray[0];
            int targetRank = rank + ray[1];
            while (inside(targetFile, targetRank)) {
                Piece target = pieceAt(targetFile, targetRank);
                if (target == null) {
                    moves.add(new Move(index(file, rank), index(targetFile, targetRank), null));
                } else {
                    if (target.side() != side && target.type() != PieceType.KING) {
                        moves.add(new Move(index(file, rank), index(targetFile, targetRank), null));
                    }
                    break;
                }
                targetFile += ray[0];
                targetRank += ray[1];
            }
        }
    }

    private void generateCastlingMoves(List<Move> moves, int file, int rank, Side side) {
        int homeRank = side == Side.WHITE ? 0 : 7;
        if (file != 4 || rank != homeRank || isInCheck(side)) return;
        int kingSideRight = side == Side.WHITE ? WHITE_KING_SIDE : BLACK_KING_SIDE;
        int queenSideRight = side == Side.WHITE ? WHITE_QUEEN_SIDE : BLACK_QUEEN_SIDE;
        Side enemy = side.opposite();
        if ((castlingRights & kingSideRight) != 0
            && pieceAt(7, homeRank) != null
            && pieceAt(7, homeRank).side() == side
            && pieceAt(7, homeRank).type() == PieceType.ROOK
            && pieceAt(5, homeRank) == null
            && pieceAt(6, homeRank) == null
            && !isSquareAttacked(index(5, homeRank), enemy)
            && !isSquareAttacked(index(6, homeRank), enemy)) {
            moves.add(new Move(index(4, homeRank), index(6, homeRank), null));
        }
        if ((castlingRights & queenSideRight) != 0
            && pieceAt(0, homeRank) != null
            && pieceAt(0, homeRank).side() == side
            && pieceAt(0, homeRank).type() == PieceType.ROOK
            && pieceAt(1, homeRank) == null
            && pieceAt(2, homeRank) == null
            && pieceAt(3, homeRank) == null
            && !isSquareAttacked(index(3, homeRank), enemy)
            && !isSquareAttacked(index(2, homeRank), enemy)) {
            moves.add(new Move(index(4, homeRank), index(2, homeRank), null));
        }
    }

    private boolean rayAttacks(int file, int rank, Side side, int[][] rays, PieceType primaryType) {
        for (int[] ray : rays) {
            int targetFile = file + ray[0];
            int targetRank = rank + ray[1];
            while (inside(targetFile, targetRank)) {
                Piece piece = pieceAt(targetFile, targetRank);
                if (piece != null) {
                    if (piece.side() == side && (piece.type() == primaryType || piece.type() == PieceType.QUEEN)) {
                        return true;
                    }
                    break;
                }
                targetFile += ray[0];
                targetRank += ray[1];
            }
        }
        return false;
    }

    private void updateCastlingRights(Move move, Piece moving, Piece captured) {
        if (moving.type() == PieceType.KING) {
            castlingRights &= moving.side() == Side.WHITE
                ? ~(WHITE_KING_SIDE | WHITE_QUEEN_SIDE)
                : ~(BLACK_KING_SIDE | BLACK_QUEEN_SIDE);
        }
        clearRookRight(move.from());
        if (captured != null) clearRookRight(move.to());
    }

    private int countPieces(PieceType type) {
        int count = 0;
        for (Piece piece : board) {
            if (piece != null && piece.type() == type) count++;
        }
        return count;
    }

    private void clearRookRight(int square) {
        if (square == index(0, 0)) castlingRights &= ~WHITE_QUEEN_SIDE;
        else if (square == index(7, 0)) castlingRights &= ~WHITE_KING_SIDE;
        else if (square == index(0, 7)) castlingRights &= ~BLACK_QUEEN_SIDE;
        else if (square == index(7, 7)) castlingRights &= ~BLACK_KING_SIDE;
    }

    private void appendCastling(StringBuilder fen) {
        if (castlingRights == 0) {
            fen.append('-');
            return;
        }
        if ((castlingRights & WHITE_KING_SIDE) != 0) fen.append('K');
        if ((castlingRights & WHITE_QUEEN_SIDE) != 0) fen.append('Q');
        if ((castlingRights & BLACK_KING_SIDE) != 0) fen.append('k');
        if ((castlingRights & BLACK_QUEEN_SIDE) != 0) fen.append('q');
    }

    private static Piece pieceFromFen(char symbol) {
        Side side = Character.isUpperCase(symbol) ? Side.WHITE : Side.BLACK;
        PieceType type = switch (Character.toLowerCase(symbol)) {
            case 'p' -> PieceType.PAWN;
            case 'n' -> PieceType.KNIGHT;
            case 'b' -> PieceType.BISHOP;
            case 'r' -> PieceType.ROOK;
            case 'q' -> PieceType.QUEEN;
            case 'k' -> PieceType.KING;
            default -> throw new IllegalArgumentException("Invalid FEN piece: " + symbol);
        };
        return new Piece(side, type);
    }

    private static char toFen(Piece piece) {
        char symbol = switch (piece.type()) {
            case PAWN -> 'p';
            case KNIGHT -> 'n';
            case BISHOP -> 'b';
            case ROOK -> 'r';
            case QUEEN -> 'q';
            case KING -> 'k';
        };
        return piece.side() == Side.WHITE ? Character.toUpperCase(symbol) : symbol;
    }

    private static int parseCastlingRights(String field) {
        if (field.equals("-")) return 0;
        int rights = 0;
        for (char right : field.toCharArray()) {
            rights |= switch (right) {
                case 'K' -> WHITE_KING_SIDE;
                case 'Q' -> WHITE_QUEEN_SIDE;
                case 'k' -> BLACK_KING_SIDE;
                case 'q' -> BLACK_QUEEN_SIDE;
                default -> throw new IllegalArgumentException("Invalid FEN castling rights");
            };
        }
        return rights;
    }

    private static int parseSquare(String square) {
        if (square.length() != 2 || square.charAt(0) < 'a' || square.charAt(0) > 'h'
            || square.charAt(1) < '1' || square.charAt(1) > '8') {
            throw new IllegalArgumentException("Invalid FEN en-passant square");
        }
        return index(square.charAt(0) - 'a', square.charAt(1) - '1');
    }

    private static int parseNonNegative(String value, String name) {
        int parsed = Integer.parseInt(value);
        if (parsed < 0) throw new IllegalArgumentException("Invalid FEN " + name);
        return parsed;
    }

    private static String squareName(int square) {
        return String.valueOf((char) ('a' + square % 8)) + (square / 8 + 1);
    }

    private static int index(int file, int rank) {
        return rank * 8 + file;
    }

    private static boolean inside(int file, int rank) {
        return file >= 0 && file < 8 && rank >= 0 && rank < 8;
    }
}
