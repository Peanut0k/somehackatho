package io.github.com.peanut0k.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;

public final class ChessChallenger implements Component {
    public static final ComponentMapper<ChessChallenger> MAPPER =
        ComponentMapper.getFor(ChessChallenger.class);

    private final String id;
    private final String name;
    private boolean defeated;
    private Entity completionMarker;

    public ChessChallenger(String id, String name, boolean defeated) {
        this.id = id;
        this.name = name;
        this.defeated = defeated;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isDefeated() {
        return defeated;
    }

    public void setDefeated(boolean defeated) {
        this.defeated = defeated;
        updateCompletionMarker();
    }

    public void setCompletionMarker(Entity completionMarker) {
        this.completionMarker = completionMarker;
        updateCompletionMarker();
    }

    public boolean hasCompletionMarker() {
        return completionMarker != null;
    }

    private void updateCompletionMarker() {
        if (completionMarker == null) return;
        Graphic graphic = Graphic.MAPPER.get(completionMarker);
        if (graphic != null) graphic.getColor().a = defeated ? 1f : 0f;
    }

}
