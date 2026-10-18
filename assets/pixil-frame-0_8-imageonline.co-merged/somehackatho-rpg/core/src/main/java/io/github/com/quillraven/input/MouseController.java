package io.github.com.quillraven.input;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import io.github.com.quillraven.component.Attack;
import io.github.com.quillraven.component.Facing;
import io.github.com.quillraven.component.Player;

public final class MouseController extends InputAdapter {
    private final ImmutableArray<Entity> players;

    public MouseController(Engine engine) {
        this.players = engine.getEntitiesFor(Family.all(Player.class, Facing.class, Attack.class).get());
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button != Input.Buttons.LEFT) return false;
        if (players.size() == 0) return false;

        Attack attack = Attack.MAPPER.get(players.first());
        if (attack.canAttack()) attack.startAttack();
        return true;
    }
}
