package io.github.com.quillraven.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.github.com.quillraven.system.ChessDuelSystem;

import java.util.function.Predicate;

public final class ChessMouseController extends InputAdapter {
    private final Viewport viewport;
    private final ChessDuelSystem chessDuelSystem;
    private final Predicate<Vector2> onHubClick;

    public ChessMouseController(Viewport viewport, ChessDuelSystem chessDuelSystem,
                                Predicate<Vector2> onHubClick) {
        this.viewport = viewport;
        this.chessDuelSystem = chessDuelSystem;
        this.onHubClick = onHubClick;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        Vector2 worldPosition = viewport.unproject(new Vector2(screenX, screenY));
        if (chessDuelSystem.isActive()) {
            if (button == Input.Buttons.LEFT) {
                chessDuelSystem.handleMouseClick(worldPosition.x, worldPosition.y);
                return true;
            }
            if (button == Input.Buttons.RIGHT) {
                chessDuelSystem.handleAbilityClick();
                return true;
            }
            return false;
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
