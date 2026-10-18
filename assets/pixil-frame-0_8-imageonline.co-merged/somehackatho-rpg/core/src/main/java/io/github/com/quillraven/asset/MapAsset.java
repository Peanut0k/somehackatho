package io.github.com.quillraven.asset;

public enum MapAsset {
    MAIN("tilemap.ldtk");

    private final String fileName;

    MapAsset(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}
