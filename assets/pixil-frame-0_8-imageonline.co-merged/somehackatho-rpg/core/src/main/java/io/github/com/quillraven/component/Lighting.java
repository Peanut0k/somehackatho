package io.github.com.quillraven.component;

import box2dLight.Light;
import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

public final class Lighting implements Component {
    public static final ComponentMapper<Lighting> MAPPER = ComponentMapper.getFor(Lighting.class);

    private final Light light;
    private final boolean followsTransform;

    public Lighting(Light light, boolean followsTransform) {
        this.light = light;
        this.followsTransform = followsTransform;
    }

    public Light getLight() {
        return light;
    }

    public boolean followsTransform() {
        return followsTransform;
    }
}
