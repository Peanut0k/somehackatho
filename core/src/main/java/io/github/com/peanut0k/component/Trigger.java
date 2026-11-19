package io.github.com.peanut0k.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import java.util.HashSet;
import java.util.Set;

public class Trigger implements Component {
    public static final ComponentMapper<Trigger> MAPPER = ComponentMapper.getFor(Trigger.class);

    private final String name;
    private final Set<Entity> triggeringEntities = new HashSet<>();

    public Trigger(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setTriggeringEntity(Entity triggeringEntity) {
        if (triggeringEntity == null) {
            triggeringEntities.clear();
        } else {
            triggeringEntities.add(triggeringEntity);
        }
    }

    public Entity getTriggeringEntity() {
        for (Entity entity : triggeringEntities) return entity;
        return null;
    }

    public void removeTriggeringEntity(Entity triggeringEntity) {
        if (triggeringEntity != null) triggeringEntities.remove(triggeringEntity);
    }

    public boolean isTriggered() {
        return !triggeringEntities.isEmpty();
    }
}
