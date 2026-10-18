package io.github.com.peanut0k.chessengine;

public enum Side {
    WHITE(1),
    BLACK(-1);

    private final int pawnDirection;

    Side(int pawnDirection) {
        this.pawnDirection = pawnDirection;
    }

    public int pawnDirection() {
        return pawnDirection;
    }

    public Side opposite() {
        return this == WHITE ? BLACK : WHITE;
    }
}
