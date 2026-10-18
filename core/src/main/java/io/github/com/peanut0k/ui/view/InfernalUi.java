package io.github.com.peanut0k.ui.view;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;

public final class InfernalUi implements Disposable {
    public static final Color EMBER = new Color(0.96f, 0.22f, 0.12f, 1f);
    public static final Color GOLD = new Color(0.98f, 0.72f, 0.31f, 1f);
    public static final Color PALE = new Color(0.86f, 0.82f, 0.80f, 1f);

    private final Texture backdropTexture;
    private final Texture panelTexture;
    private final Texture buttonTexture;
    private final Texture selectedButtonTexture;
    private final Texture heartTexture;
    private final Drawable panel;
    private final Drawable button;
    private final Drawable selectedButton;
    private final Drawable heart;

    public InfernalUi() {
        backdropTexture = texture(createBackdropPixmap());
        panelTexture = texture(createPanel());
        buttonTexture = texture(createButton(new Color(0.10f, 0.09f, 0.13f, 1f), new Color(0.55f, 0.17f, 0.15f, 1f)));
        selectedButtonTexture = texture(createButton(new Color(0.30f, 0.08f, 0.10f, 1f), EMBER));
        heartTexture = texture(createHeart());
        panel = ninePatch(panelTexture);
        button = ninePatch(buttonTexture);
        selectedButton = ninePatch(selectedButtonTexture);
        heart = new TextureRegionDrawable(new TextureRegion(heartTexture));
    }

    public Image createBackdrop() {
        Image image = new Image(backdropTexture);
        image.setFillParent(true);
        image.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        return image;
    }

    public Drawable panel() {
        return panel;
    }

    public Drawable button() {
        return button;
    }

    public Drawable selectedButton() {
        return selectedButton;
    }

    public Drawable heart() {
        return heart;
    }

    private static Pixmap createBackdropPixmap() {
        Pixmap pixmap = new Pixmap(320, 180, Pixmap.Format.RGBA8888);
        for (int y = 0; y < 180; y++) {
            float progress = y / 180f;
            pixmap.setColor(
                0.035f + progress * 0.08f,
                0.035f + progress * 0.015f,
                0.075f - progress * 0.025f,
                1f);
            pixmap.drawLine(0, y, 319, y);
        }

        pixmap.setColor(new Color(0.15f, 0.025f, 0.035f, 1f));
        pixmap.fillTriangle(0, 110, 70, 66, 135, 110);
        pixmap.fillTriangle(94, 112, 183, 52, 256, 112);
        pixmap.fillTriangle(212, 108, 276, 68, 319, 108);
        pixmap.setColor(new Color(0.24f, 0.035f, 0.035f, 1f));
        pixmap.fillRectangle(151, 82, 18, 42);
        pixmap.fillRectangle(146, 90, 28, 34);
        pixmap.fillRectangle(155, 73, 4, 9);
        pixmap.fillRectangle(161, 68, 4, 14);
        pixmap.fillRectangle(166, 76, 3, 7);

        pixmap.setColor(new Color(0.25f, 0.045f, 0.035f, 1f));
        pixmap.fillTriangle(20, 30, 300, 30, 263, 77);
        pixmap.fillTriangle(20, 30, 263, 77, 57, 77);
        pixmap.setColor(new Color(0.55f, 0.11f, 0.055f, 1f));
        pixmap.drawLine(20, 30, 57, 77);
        pixmap.drawLine(300, 30, 263, 77);
        pixmap.drawLine(57, 77, 263, 77);

        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 10; column++) {
                boolean lightSquare = ((row + column) & 1) == 0;
                pixmap.setColor(lightSquare
                    ? new Color(0.38f, 0.14f, 0.12f, 1f)
                    : new Color(0.12f, 0.085f, 0.13f, 1f));
                int x1 = 57 + column * 20 - row * 4;
                int x2 = 57 + (column + 1) * 20 - row * 4;
                int y1 = 77 - row * 9;
                int y2 = y1 - 9;
                pixmap.fillTriangle(x1, y1, x2, y1, x2 - 4, y2);
                pixmap.fillTriangle(x1, y1, x2 - 4, y2, x1 - 4, y2);
            }
        }
        pixmap.setColor(new Color(0.95f, 0.12f, 0.055f, 1f));
        pixmap.drawLine(7, 11, 312, 11);
        pixmap.drawLine(7, 12, 312, 12);
        pixmap.setColor(new Color(0.42f, 0.09f, 0.065f, 1f));
        pixmap.drawLine(7, 13, 312, 13);

        pixmap.setColor(new Color(1f, 0.34f, 0.08f, 1f));
        pixmap.fillRectangle(34, 79, 2, 6);
        pixmap.fillRectangle(282, 79, 2, 6);
        pixmap.fillRectangle(35, 85, 1, 3);
        pixmap.fillRectangle(283, 85, 1, 3);
        return pixmap;
    }

    private static Pixmap createPanel() {
        Pixmap pixmap = new Pixmap(20, 20, Pixmap.Format.RGBA8888);
        pixmap.setColor(0.045f, 0.045f, 0.075f, 0.96f);
        pixmap.fill();
        pixmap.setColor(0.29f, 0.12f, 0.15f, 1f);
        pixmap.drawRectangle(0, 0, 20, 20);
        pixmap.drawRectangle(2, 2, 16, 16);
        pixmap.setColor(0.57f, 0.19f, 0.14f, 1f);
        pixmap.drawLine(5, 1, 14, 1);
        pixmap.drawLine(5, 18, 14, 18);
        return pixmap;
    }

    private static Pixmap createButton(Color fill, Color edge) {
        Pixmap pixmap = new Pixmap(20, 20, Pixmap.Format.RGBA8888);
        pixmap.setColor(fill);
        pixmap.fill();
        pixmap.setColor(edge);
        pixmap.drawRectangle(0, 0, 20, 20);
        pixmap.drawRectangle(2, 2, 16, 16);
        pixmap.setColor(edge.r * 0.55f, edge.g * 0.55f, edge.b * 0.55f, 1f);
        pixmap.drawLine(5, 1, 14, 1);
        return pixmap;
    }

    private static Pixmap createHeart() {
        Pixmap pixmap = new Pixmap(10, 10, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fillCircle(3, 3, 3);
        pixmap.fillCircle(7, 3, 3);
        pixmap.fillTriangle(0, 3, 10, 3, 5, 9);
        return pixmap;
    }

    private static Texture texture(Pixmap pixmap) {
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    private static Drawable ninePatch(Texture texture) {
        return new NinePatchDrawable(new NinePatch(new TextureRegion(texture), 5, 5, 5, 5));
    }

    @Override
    public void dispose() {
        backdropTexture.dispose();
        panelTexture.dispose();
        buttonTexture.dispose();
        selectedButtonTexture.dispose();
        heartTexture.dispose();
    }
}
