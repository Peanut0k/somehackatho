package io.github.com.quillraven.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

public final class ChessChallenger implements Component {
    public static final ComponentMapper<ChessChallenger> MAPPER =
        ComponentMapper.getFor(ChessChallenger.class);

    private final String id;
    private final String name;
    private final int searchDepth;
    private final RewardType rewardType;
    private final Ability ability;
    private boolean defeated;

    public ChessChallenger(String id, String name, int searchDepth, RewardType rewardType,
                           Ability ability, boolean defeated) {
        this.id = id;
        this.name = name;
        this.searchDepth = searchDepth;
        this.rewardType = rewardType;
        this.ability = ability;
        this.defeated = defeated;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getSearchDepth() {
        return searchDepth;
    }

    public RewardType getRewardType() {
        return rewardType;
    }

    public Ability getAbility() {
        return ability;
    }

    public boolean isDefeated() {
        return defeated;
    }

    public void setDefeated(boolean defeated) {
        this.defeated = defeated;
    }

    public enum RewardType {
        LIFE, ABILITY
    }

    public enum Ability {
        NONE, DOUBLE_MOVE
    }
}
