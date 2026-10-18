package io.github.com.quillraven.asset;

public enum MapAsset {
    MAIN("PoserSeDane/PoserSeDane.ldtk");

    private final String fileName;

    MapAsset(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}
