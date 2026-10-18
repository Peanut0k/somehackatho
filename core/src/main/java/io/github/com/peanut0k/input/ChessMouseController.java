package io.github.com.peanut0k.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.github.com.peanut0k.system.ChessDuelSystem;
import io.github.com.peanut0k.system.ChessPieceSystem;

import java.util.function.Predicate;

public final class ChessMouseController extends InputAdapter {
    private final Viewport viewport;
    private final ChessDuelSystem chessDuelSystem;
    private final ChessPieceSystem chessPieceSystem;
    private final Predicate<Vector2> onHelpClick;
    private final Predicate<Vector2> onAbilityClick;
    private final Predicate<Vector2> onHubClick;

    public ChessMouseController(Viewport viewport, ChessDuelSystem chessDuelSystem,
                                ChessPieceSystem chessPieceSystem,
                                Predicate<Vector2> onHelpClick,
                                Predicate<Vector2> onAbilityClick,
                                Predicate<Vector2> onHubClick) {
        this.viewport = viewport;
        this.chessDuelSystem = chessDuelSystem;
        this.chessPieceSystem = chessPieceSystem;
        this.onHelpClick = onHelpClick;
        this.onAbilityClick = onAbilityClick;
        this.onHubClick = onHubClick;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button == Input.Buttons.LEFT
            && onHelpClick.test(new Vector2(screenX, screenY))) return true;
        Vector2 worldPosition = viewport.unproject(new Vector2(screenX, screenY));
        if (chessDuelSystem.isActive()) {
            if (button == Input.Buttons.LEFT) {
                if (onAbilityClick.test(new Vector2(screenX, screenY))) return true;
                chessDuelSystem.handleMouseClick(worldPosition.x, worldPosition.y);
                return true;
            }
            return false;
        }
        if (chessPieceSystem.isArenaActive() && button == Input.Buttons.LEFT
            && onAbilityClick.test(new Vector2(screenX, screenY))) return true;
        if (button == Input.Buttons.LEFT && chessPieceSystem.isArenaActive()) {
            chessPieceSystem.handleMouseClick(worldPosition.x, worldPosition.y);
            return true;
        }
        return button == Input.Buttons.LEFT && onHubClick.test(worldPosition);
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        if (!chessDuelSystem.isActive()) return false;
        Vector2 worldPosition = viewport.unproject(new Vector2(screenX, screenY));
        chessDuelSystem.handleMouseMove(worldPosition.x, worldPosition.y);
        return true;
    }
}
