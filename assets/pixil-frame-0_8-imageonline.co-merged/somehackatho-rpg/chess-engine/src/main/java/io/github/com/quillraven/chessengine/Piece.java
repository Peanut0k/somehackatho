package io.github.com.peanut0k.chessengine;

public record Piece(Side side, PieceType type) {
    public Piece {
        if (side == null || type == null) throw new IllegalArgumentException("Piece side and type are required");
    }
}
