package io.github.com.quillraven.chessengine;

public record Piece(Side side, PieceType type) {
    public Piece {
        if (side == null || type == null) throw new IllegalArgumentException("Piece side and type are required");
    }
}
