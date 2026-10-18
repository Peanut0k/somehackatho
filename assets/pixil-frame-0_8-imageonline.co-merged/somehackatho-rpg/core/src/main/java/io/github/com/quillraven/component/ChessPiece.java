package io.github.com.peanut0k.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

public final class ChessPiece implements Component {
    public static final ComponentMapper<ChessPiece> MAPPER = ComponentMapper.getFor(ChessPiece.class);

    private final Type type;

    public ChessPiece(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        PAWN, ROOK, KNIGHT, BISHOP, QUEEN, KING
    }
}
