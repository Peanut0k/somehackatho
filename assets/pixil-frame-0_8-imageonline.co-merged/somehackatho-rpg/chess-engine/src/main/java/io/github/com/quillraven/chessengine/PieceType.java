package io.github.com.peanut0k.chessengine;

public enum PieceType {
    PAWN(100),
    KNIGHT(320),
    BISHOP(330),
    ROOK(500),
    QUEEN(900),
    KING(20_000);

    private final int value;

    PieceType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
