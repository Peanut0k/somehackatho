package io.github.com.quillraven.chessengine;

public record Move(int from, int to, PieceType promotion) {
    public Move {
        if (from < 0 || from >= 64 || to < 0 || to >= 64 || from == to) {
            throw new IllegalArgumentException("Move squares must be distinct board squares");
        }
    }

    public int fromFile() {
        return from % 8;
    }

    public int fromRank() {
        return from / 8;
    }

    public int toFile() {
        return to % 8;
    }

    public int toRank() {
        return to / 8;
    }

    public String toUci() {
        String move = squareName(from) + squareName(to);
        return promotion == null ? move : move + promotion.name().toLowerCase().charAt(0);
    }

    private static String squareName(int square) {
        return String.valueOf((char) ('a' + square % 8)) + (square / 8 + 1);
    }
}
