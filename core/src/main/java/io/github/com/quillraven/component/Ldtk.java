package io.github.com.quillraven.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

public class Ldtk implements Component {
    public static final ComponentMapper<Ldtk> MAPPER = ComponentMapper.getFor(Ldtk.class);

    private final String iid;
    private final String identifier;

    public Ldtk(String iid, String identifier) {
        this.iid = iid;
        this.identifier = identifier;
    }

    public String getIid() {
        return iid;
    }

    public String getIdentifier() {
        return identifier;
    }
}
