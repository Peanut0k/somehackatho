package io.github.com.quillraven.ldtk;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.com.quillraven.GdxGame;
import io.github.com.quillraven.asset.MapAsset;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LdtkMap implements Disposable {
    private final int pixelWidth;
    private final int pixelHeight;
    private final int gridSize;
    private final int layerCount;
    private final float width;
    private final float height;
    private final Color backgroundColor;
    private final String music;
    private final List<Tile> tiles = new ArrayList<>();
    private final List<EntityInstance> entities = new ArrayList<>();
    private final List<Texture> textures = new ArrayList<>();
    private final boolean[][] solidCells;

    private LdtkMap(int pixelWidth, int pixelHeight, int gridSize, int layerCount,
                    Color backgroundColor, String music) {
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        this.gridSize = gridSize;
        this.layerCount = layerCount;
        this.width = pixelWidth * GdxGame.UNIT_SCALE;
        this.height = pixelHeight * GdxGame.UNIT_SCALE;
        this.backgroundColor = backgroundColor;
        this.music = music;
        this.solidCells = new boolean[Math.max(1, pixelWidth / gridSize)][Math.max(1, pixelHeight / gridSize)];
    }

    public static LdtkMap load(MapAsset asset) {
        return load(Gdx.files.internal(asset.getFileName()));
    }

    public static LdtkMap load(MapAsset asset, String levelIdentifier) {
        return load(Gdx.files.internal(asset.getFileName()), levelIdentifier);
    }

    public static LdtkMap load(FileHandle projectFile) {
        return load(projectFile, null);
    }

    public static LdtkMap load(FileHandle projectFile, String levelIdentifier) {
        JsonValue project = new JsonReader().parse(projectFile);
        JsonValue levels = project.get("levels");
        JsonValue level = levelIdentifier == null
            ? findPlayerLevel(levels)
            : findLevel(levels, levelIdentifier);
        int pixelWidth = level.getInt("pxWid");
        int pixelHeight = level.getInt("pxHei");
        JsonValue layers = level.get("layerInstances");
        int gridSize = project.getInt("defaultGridSize", 16);
        if (layers != null) {
            for (JsonValue layer : layers) {
                if ("IntGrid".equals(layer.getString("__type", ""))) {
                    gridSize = layer.getInt("__gridSize", gridSize);
                    break;
                }
            }
        }
        Color bg = Color.valueOf(project.getString("bgColor", "000000").replace("#", ""));
        String music = fieldValue(level.get("fieldInstances"), "music", "");
        LdtkMap map = new LdtkMap(pixelWidth, pixelHeight, gridSize,
            layers == null ? 0 : layers.size, bg, music);

        Map<Integer, String> tilesetPaths = new HashMap<>();
        Map<Integer, Integer> tilesetGridSizes = new HashMap<>();
        JsonValue tilesets = project.get("defs").get("tilesets");
        for (JsonValue tileset : tilesets) {
            int uid = tileset.getInt("uid");
            tilesetPaths.put(uid, tileset.getString("relPath"));
            tilesetGridSizes.put(uid, tileset.getInt("tileGridSize", gridSize));
        }
        Map<Integer, Texture> texturesByUid = new HashMap<>();
        FileHandle projectDirectory = projectFile.parent();
        try {
            if (layers != null) {
                for (int layerIndex = 0; layerIndex < layers.size; layerIndex++) {
                    JsonValue layer = layers.get(layerIndex);
                    int layerGrid = layer.getInt("__gridSize", gridSize);
                    int tilesetUid = intValue(layer.get("__tilesetDefUid"), -1);
                    int tileSize = tilesetGridSizes.getOrDefault(tilesetUid, layerGrid);
                    Texture layerTexture = textureFor(
                        tilesetUid, tilesetPaths, texturesByUid, map, projectDirectory);
                    addTiles(map, layer.get("autoLayerTiles"), layerTexture, layer, pixelHeight,
                        tileSize, layerIndex);
                    addTiles(map, layer.get("gridTiles"), layerTexture, layer, pixelHeight,
                        tileSize, layerIndex);

                    JsonValue csv = layer.get("intGridCsv");
                    if (csv != null) {
                        int columns = layer.getInt("__cWid", Math.max(1, pixelWidth / layerGrid));
                        int rows = layer.getInt("__cHei", Math.max(1, pixelHeight / layerGrid));
                        int offsetX = layer.getInt("__pxTotalOffsetX", 0) / layerGrid;
                        int offsetY = layer.getInt("__pxTotalOffsetY", 0) / layerGrid;


                        for (int i = 0; i < csv.size; i++) {
                            if (csv.getInt(i) == 0) continue;
                            int cellX = i % columns + offsetX;
                            int cellY = i / columns + offsetY;
                            if (cellX >= 0 && cellX < map.solidCells.length
                                && cellY >= 0 && cellY < map.solidCells[0].length) {
                                map.solidCells[cellX][cellY] = true;
                            }
                        }
                    }

                    JsonValue instances = layer.get("entityInstances");
                    if (instances != null) {
                        for (JsonValue instance : instances) {
                            map.entities.add(parseEntity(
                                instance, layer, layerIndex, pixelHeight,
                                tilesetPaths, texturesByUid, map, projectDirectory));
                        }
                    }
                }
            }
            return map;
        } catch (RuntimeException exception) {
            map.dispose();
            throw exception;
        }
    }

    private static JsonValue findPlayerLevel(JsonValue levels) {
        for (JsonValue level : levels) {
            JsonValue layers = level.get("layerInstances");
            if (layers == null) continue;
            for (JsonValue layer : layers) {
                JsonValue instances = layer.get("entityInstances");
                if (instances == null) continue;
                for (JsonValue instance : instances) {
                    if ("Player".equalsIgnoreCase(instance.getString("__identifier", ""))) {
                        return level;
                    }
                }
            }
        }
        return levels.get(0);
    }

    private static JsonValue findLevel(JsonValue levels, String identifier) {
        for (JsonValue level : levels) {
            if (identifier.equals(level.getString("identifier", ""))) return level;
        }
        throw new GdxRuntimeException("LDtk level not found: " + identifier);
    }

    private static void addTiles(
        LdtkMap map,
        JsonValue tiles,
        Texture texture,
        JsonValue layer,
        int pixelHeight,
        int tileSize,
        int layerIndex
    ) {
        if (tiles == null || texture == null) return;
        int[] offset = intArrayOrDefault(layer.get("__pxTotalOffsetX"), layer.get("__pxTotalOffsetY"));
        for (JsonValue tile : tiles) {
            int[] px = intArray(tile.get("px"));
            int[] src = intArray(tile.get("src"));
            int flags = tile.getInt("f", 0);
            TextureRegion region = new TextureRegion(texture, src[0], src[1], tileSize, tileSize);
            region.flip((flags & 1) != 0, (flags & 2) != 0);
            float x = (px[0] + offset[0]) * GdxGame.UNIT_SCALE;
            float y = (pixelHeight - px[1] - tileSize - offset[1]) * GdxGame.UNIT_SCALE;
            map.tiles.add(new Tile(region, x, y, tileSize * GdxGame.UNIT_SCALE, layerIndex));
        }
    }

    private static Texture textureFor(
        int uid,
        Map<Integer, String> paths,
        Map<Integer, Texture> texturesByUid,
        LdtkMap map,
        FileHandle projectDirectory
    ) {
        if (uid < 0) return null;
        Texture existing = texturesByUid.get(uid);
        if (existing != null) return existing;
        String path = paths.get(uid);
        if (path == null) return null;
        String assetPath = path.replace('\\', '/');
        if (assetPath.startsWith("somehackatho-rpg/assets/")) {
            assetPath = assetPath.substring("somehackatho-rpg/assets/".length());
        } else if (assetPath.startsWith("somehackatho-rpg/")) {
            assetPath = assetPath.substring("somehackatho-rpg/".length());
        } else if (assetPath.startsWith("assets/")) {
            assetPath = assetPath.substring("assets/".length());
        }
        FileHandle file = projectDirectory.child(assetPath);
        if (!file.exists()) {
            file = Gdx.files.internal(assetPath);
        }
        if (!file.exists()) {
            file = projectDirectory.child(new File(assetPath).getName());
        }
        if (!file.exists()) {
            throw new GdxRuntimeException("LDtk tileset image not found: " + assetPath);
        }
        Texture texture = new Texture(file);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        texturesByUid.put(uid, texture);
        map.textures.add(texture);
        return texture;
    }

    private static EntityInstance parseEntity(
        JsonValue instance,
        JsonValue layer,
        int layerIndex,
        int levelHeight,
        Map<Integer, String> tilesetPaths,
        Map<Integer, Texture> texturesByUid,
        LdtkMap map,
        FileHandle projectDirectory
    ) {
        int[] px = intArray(instance.get("px"));
        float entityWidth = instance.getFloat("width", map.gridSize);
        float entityHeight = instance.getFloat("height", map.gridSize);
        Map<String, Object> fields = new HashMap<>();
        JsonValue fieldInstances = instance.get("fieldInstances");
        if (fieldInstances != null) {
            for (JsonValue field : fieldInstances) {
                fields.put(field.getString("__identifier"), valueOf(field.get("__value")));
            }
        }
        int tilesetUid = intValue(layer.get("__tilesetDefUid"), -1);
        JsonValue tile = instance.get("__tile");
        TextureRegion region = null;
        if (tile != null && !tile.isNull()) {
            tilesetUid = tile.getInt("tilesetUid", tilesetUid);
            Texture texture = textureFor(tilesetUid, tilesetPaths, texturesByUid, map, projectDirectory);
            if (texture != null) {
                int x = tile.getInt("x");
                int y = tile.getInt("y");
                int w = tile.getInt("w", map.gridSize);
                int h = tile.getInt("h", map.gridSize);
                region = new TextureRegion(texture, x, y, w, h);
            }
        }
        int offsetX = layer.getInt("__pxTotalOffsetX", 0);
        int offsetY = layer.getInt("__pxTotalOffsetY", 0);
        float x = (px[0] + offsetX) * GdxGame.UNIT_SCALE;
        float y = (levelHeight - px[1] - entityHeight - offsetY) * GdxGame.UNIT_SCALE;
        return new EntityInstance(
            instance.getString("__identifier"),
            instance.getString("iid", instance.getString("__identifier")),
            layerIndex,
            x,
            y,
            entityWidth * GdxGame.UNIT_SCALE,
            entityHeight * GdxGame.UNIT_SCALE,
            fields,
            region
        );
    }

    private static Object valueOf(JsonValue value) {
        if (value == null || value.isNull()) return null;
        if (value.isBoolean()) return value.asBoolean();
        if (value.isNumber()) return value.asDouble();
        if (value.isString()) return value.asString();
        if (value.isObject()) {
            JsonValue enumValue = value.get("entityIid");
            if (enumValue != null && !enumValue.isNull()) return enumValue.asString();
            enumValue = value.get("value");
            if (enumValue != null && !enumValue.isNull()) return enumValue.asString();
        }
        return value.toString();
    }

    private static int intValue(JsonValue value, int fallback) {
        return value == null || value.isNull() ? fallback : value.asInt();
    }

    private static String fieldValue(JsonValue instances, String name, String fallback) {
        if (instances == null) return fallback;
        for (JsonValue field : instances) {
            if (name.equals(field.getString("__identifier", ""))) {
                JsonValue value = field.get("__value");
                return value == null || value.isNull() ? fallback : value.asString();
            }
        }
        return fallback;
    }

    private static int[] intArray(JsonValue array) {
        return new int[]{array.get(0).asInt(), array.get(1).asInt()};
    }

    private static int[] intArrayOrDefault(JsonValue x, JsonValue y) {
        return new int[]{
            x == null ? 0 : x.asInt(),
            y == null ? 0 : y.asInt()
        };
    }

    public int getPixelWidth() {
        return pixelWidth;
    }

    public int getPixelHeight(
 ) {
        return pixelHeight;
    }

    public int getGridSize() {
        return gridSize;
    }

    public int getLayerCount() {
        return layerCount;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public String getMusic() {
        return music;
    }

    public List<Tile> getTiles() {
        return tiles;
    }

    public List<EntityInstance> getEntities() {
        return entities;
    }

    public boolean isSolid(int x, int y) {
        return x >= 0 && x < solidCells.length && y >= 0 && y < solidCells[x].length && solidCells[x][y];
    }

    public float[] findOpenSpawn() {
        int centerX = solidCells.length / 2;
        int centerY = solidCells[0].length / 2;
        for (int radius = 0; radius < Math.max(solidCells.length, solidCells[0].length); radius++) {
            for (int y = Math.max(0, centerY - radius); y <= Math.min(solidCells[0].length - 1, centerY + radius); y++) {
                for (int x = Math.max(0, centerX - radius); x <= Math.min(solidCells.length - 1, centerX + radius); x++) {
                    int topDownY = solidCells[0].length - 1 - y;
                    if (!isSolid(x, topDownY)) {
                        return new float[]{(x + 0.5f) * gridSize * GdxGame.UNIT_SCALE,
                            (y + 0.5f) * gridSize * GdxGame.UNIT_SCALE};
                    }
                }
            }
        }
        return new float[]{width * 0.5f, height * 0.5f};
    }

    @Override
    public void dispose() {
        for (Texture texture : textures) texture.dispose();
        textures.clear();
    }

    public record Tile(TextureRegion region, float x, float y, float size, int layerIndex) {
    }

    public record EntityInstance(
        String identifier,
        String iid,
        int layerIndex,
        float x,
        float y,
        float width,
        float height,
        Map<String, Object> fields,
        TextureRegion region
    ) {
        public Object get(String field, Object fallback) {
            return fields.getOrDefault(field, fallback);
        }

        public String getString(String field, String fallback) {
            Object value = fields.get(field);
            return value == null ? fallback : String.valueOf(value);
        }

        public float getFloat(String field, float fallback) {
            Object value = fields.get(field);
            return value instanceof Number number ? number.floatValue() : fallback;
        }

        public boolean getBoolean(String field, boolean fallback) {
            Object value = fields.get(field);
            return value instanceof Boolean bool ? bool : fallback;
        }
    }
}
