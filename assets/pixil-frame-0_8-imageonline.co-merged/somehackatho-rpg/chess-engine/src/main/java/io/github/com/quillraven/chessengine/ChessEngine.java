package io.github.com.quillraven.chessengine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ChessEngine {
    private static final int MATE_SCORE = 100_000;
    private static final int QUIESCENCE_DEPTH = 3;
    private static final int INFINITY = 1_000_000;

    private long nodes;

    public Move chooseMove(Position position, Side side, int depth) {
        if (position == null || side == null) throw new IllegalArgumentException("Position and side are required");
        if (depth < 1) throw new IllegalArgumentException("Search depth must be at least one");
        Position root = Position.fromFen(position.toFen());
        root.setSideToMove(side);
        List<Move> moves = orderMoves(root, root.legalMoves());
        if (moves.isEmpty()) return null;

        nodes = 0;
        Move bestMove = moves.get(0);
        int bestScore = -INFINITY;
        int alpha = -INFINITY;
        for (Move move : moves) {
            int score = -search(root.apply(move), depth - 1, -INFINITY, -alpha, 1);
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            alpha = Math.max(alpha, score);
        }
        return bestMove;
    }

    public long searchedNodes() {
        return nodes;
    }

    private int search(Position position, int depth, int alpha, int beta, int ply) {
        nodes++;
        if (!position.hasPieces(position.sideToMove())) return -MATE_SCORE + ply;
        if (position.isDrawByFiftyMoveRule() || position.isInsufficientMaterial()) return 0;
        if (depth <= 0) return quiescence(position, alpha, beta, ply, QUIESCENCE_DEPTH);

        List<Move> moves = orderMoves(position, position.legalMoves());
        if (moves.isEmpty()) {
            return position.isInCheck(position.sideToMove()) ? -MATE_SCORE + ply : 0;
        }
        for (Move move : moves) {
            int score = -search(position.apply(move), depth - 1, -beta, -alpha, ply + 1);
            if (score >= beta) return beta;
            alpha = Math.max(alpha, score);
        }
        return alpha;
    }

    private int quiescence(Position position, int alpha, int beta, int ply, int remainingDepth) {
        nodes++;
        if (!position.hasPieces(position.sideToMove())) return -MATE_SCORE + ply;
        if (position.isDrawByFiftyMoveRule() || position.isInsufficientMaterial()) return 0;
        boolean inCheck = position.isInCheck(position.sideToMove());
        int standPat = evaluate(position);
        if (!inCheck) {
            if (standPat >= beta) return beta;
            alpha = Math.max(alpha, standPat);
            if (remainingDepth <= 0) return alpha;
        }

        List<Move> moves = orderMoves(position, position.legalMoves());
        if (moves.isEmpty()) return inCheck ? -MATE_SCORE + ply : alpha;
        for (Move move : moves) {
            if (!inCheck && !isCapture(position, move) && move.promotion() == null) continue;
            int score = -quiescence(position.apply(move), -beta, -alpha, ply + 1, remainingDepth - 1);
            if (score >= beta) return beta;
            alpha = Math.max(alpha, score);
        }
        return alpha;
    }

    private int evaluate(Position position) {
        int score = 0;
        int whitePawnCount = 0;
        int blackPawnCount = 0;
        for (int square = 0; square < 64; square++) {
            Piece piece = position.pieceAt(square);
            if (piece == null) continue;
            int file = square % 8;
            int rank = square / 8;
            int relativeRank = piece.side() == Side.WHITE ? rank : 7 - rank;
            int pieceScore = piece.type().value();
            switch (piece.type()) {
                case PAWN -> {
                    pieceScore += relativeRank * 8;
                    if (piece.side() == Side.WHITE) whitePawnCount++;
                    else blackPawnCount++;
                }
                case KNIGHT, BISHOP -> pieceScore += centerBonus(file, rank) * 4;
                case ROOK -> pieceScore += relativeRank == 6 ? 20 : 0;
                case QUEEN -> pieceScore += centerBonus(file, rank) * 2;
                case KING -> {
                    if (relativeRank < 2) pieceScore += 15;
                    else pieceScore -= centerBonus(file, rank) * 8;
                }
            }
            score += piece.side() == Side.WHITE ? pieceScore : -pieceScore;
        }

        if (whitePawnCount == 0) score -= 5_000;
        if (blackPawnCount == 0) score += 20;
        score += blackPressure(position);
        return position.sideToMove() == Side.WHITE ? score : -score;
    }

    private int blackPressure(Position position) {
        List<Integer> whiteTargets = new ArrayList<>();
        for (int square = 0; square < 64; square++) {
            Piece piece = position.pieceAt(square);
            if (piece != null && piece.side() == Side.WHITE) whiteTargets.add(square);
        }
        int pressure = 0;
        for (int square = 0; square < 64; square++) {
            Piece piece = position.pieceAt(square);
            if (piece == null || piece.side() != Side.BLACK) continue;
            int nearest = 14;
            for (int target : whiteTargets) {
                int distance = Math.abs(square % 8 - target % 8) + Math.abs(square / 8 - target / 8);
                nearest = Math.min(nearest, distance);
            }
            if (nearest < 14) pressure += (14 - nearest) * 5;
        }
        return -pressure;
    }

    private int centerBonus(int file, int rank) {
        return 6 - Math.abs(2 * file - 7) - Math.abs(2 * rank - 7);
    }

    private List<Move> orderMoves(Position position, List<Move> moves) {
        moves.sort(Comparator.comparingInt((Move move) -> movePriority(position, move)).reversed());
        return moves;
    }

    private int movePriority(Position position, Move move) {
        int priority = 0;
        Piece attacker = position.pieceAt(move.from());
        Piece victim = position.pieceAt(move.to());
        if (victim != null) priority += 10_000 + victim.type().value() - attacker.type().value() / 10;
        if (attacker != null && attacker.type() == PieceType.PAWN && move.fromFile() != move.toFile() && victim == null) {
            priority += 10_000 + PieceType.PAWN.value();
        }
        if (move.promotion() != null) priority += 8_000 + move.promotion().value();
        return priority;
    }

    private boolean isCapture(Position position, Move move) {
        if (position.pieceAt(move.to()) != null) return true;
        Piece moving = position.pieceAt(move.from());
        return moving != null
            && moving.type() == PieceType.PAWN
            && move.fromFile() != move.toFile()
            && move.to() == position.enPassantSquare();
    }
}
