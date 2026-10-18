package io.github.com.peanut0k.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

public class SoulFragment implements Component {
    public static final ComponentMapper<SoulFragment> MAPPER = ComponentMapper.getFor(SoulFragment.class);

    private final int amount;

    public SoulFragment(int amount) {
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }
}
