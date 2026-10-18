package io.github.com.peanut0k.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

public class Ldtk implements Component {
    public static final ComponentMapper<Ldtk> MAPPER = ComponentMapper.getFor(Ldtk.class);

    private final String iid;
    private final String identifier;
    private final int layerIndex;

    public Ldtk(String iid, String identifier) {
        this(iid, identifier, -1);
    }

    public Ldtk(String iid, String identifier, int layerIndex) {
        this.iid = iid;
        this.identifier = identifier;
        this.layerIndex = layerIndex;
    }

    public String getIid() {
        return iid;
    }

    public String getIdentifier() {
        return identifier;
    }

    public int getLayerIndex() {
        return layerIndex;
    }
}
