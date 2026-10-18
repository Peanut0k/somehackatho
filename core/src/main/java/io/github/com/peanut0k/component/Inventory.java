package io.github.com.peanut0k.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

public class Inventory implements Component {
    public static final ComponentMapper<Inventory> MAPPER = ComponentMapper.getFor(Inventory.class);

    private int soulFragments;

    public int getSoulFragments() {
        return soulFragments;
    }

    public void addSoulFragments(int amount) {
        soulFragments += Math.max(0, amount);
    }

}
