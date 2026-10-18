package io.github.com.peanut0k.ui.view;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.com.peanut0k.ui.model.MenuViewModel;

public class MenuView extends View<MenuViewModel> {
    private final InfernalUi infernalUi;
    private Group selectedItem;

    public MenuView(Stage stage, Skin skin, MenuViewModel viewModel, InfernalUi infernalUi) {
        super(stage, skin, viewModel);
        this.infernalUi = infernalUi;
        initializeUI();
        this.selectedItem = findActor(MenuOption.START_GAME.name());
        selectMenuItem(this.selectedItem);
    }

    /**
     * Selects a menu item and animates the selection indicator.
     */
    private void selectMenuItem(Group menuItem) {
        updateOption(selectedItem, false);
        this.selectedItem = menuItem;
        updateOption(selectedItem, true);
    }

    private void updateOption(Group option, boolean selected) {
        if (option instanceof TextButton button) {
            button.getStyle().up = selected ? infernalUi.selectedButton() : infernalUi.button();
            button.getLabel().setColor(selected ? InfernalUi.GOLD : InfernalUi.PALE);
        } else if (option instanceof Table table) {
            table.setBackground(selected ? infernalUi.selectedButton() : infernalUi.panel());
            Label label = (Label) table.getChild(0);
            label.setColor(selected ? InfernalUi.GOLD : InfernalUi.PALE);
        }
    }

    /**
     * Sets up the main UI layout with banner and menu content.
     */
    @Override
    protected void setupUI() {
        setFillParent(true);

        Label eyebrow = new Label("A CHESSBOARD DESCENT", skin, "small");
        eyebrow.setFontScale(0.8f);
        eyebrow.setColor(InfernalUi.EMBER);
        add(eyebrow).padTop(3f).row();

        Label title = new Label("THE LAST SQUARE", skin);
        title.setFontScale(0.8f);
        title.setColor(InfernalUi.GOLD);
        add(title).padTop(1f).row();

        setupMenuContent();

        Label label = new Label("WASD / ARROWS CHOOSE     SPACE / CLICK ENTER", skin, "small");
        label.setFontScale(0.8f);
        label.setColor(InfernalUi.PALE);
        add(label).expand().align(Align.bottom).padBottom(2f);
    }

    /**
     * Creates the menu content with buttons and volume sliders.
     */
    private void setupMenuContent() {
        Table contentTable = new Table();
        contentTable.setBackground(infernalUi.panel());
        contentTable.padLeft(12f);
        contentTable.padRight(12f);
        contentTable.padTop(7f);
        contentTable.padBottom(6f);
        contentTable.defaults().space(2f);

        Label chapter = new Label("CHAPTER I  //  THE ASHEN OPENING", skin, "small");
        chapter.setFontScale(0.8f);
        chapter.setColor(InfernalUi.GOLD);
        contentTable.add(chapter).padBottom(2f).row();

        TextButton textButton = createButton("Enter the Board");
        textButton.setName(MenuOption.START_GAME.name());
        onClick(textButton, viewModel::startGame);
        onEnter(textButton, this::selectMenuItem);
        contentTable.add(textButton).width(132f).height(18f).padBottom(1f).row();

        Slider musicSlider = setupVolumeSlider(contentTable, "Music Volume", MenuOption.MUSIC_VOLUME);
        musicSlider.setValue(viewModel.getMusicVolume());
        onChange(musicSlider, (slider) -> viewModel.setMusicVolume(slider.getValue()));

        Slider soundSlider = setupVolumeSlider(contentTable, "Sound Volume", MenuOption.SOUND_VOLUME);
        soundSlider.setValue(viewModel.getSoundVolume());
        onChange(soundSlider, (slider) -> viewModel.setSoundVolume(slider.getValue()));

        textButton = createButton("Quit Game");
        textButton.setName(MenuOption.QUIT_GAME.name());
        onClick(textButton, viewModel::quitGame);
        onEnter(textButton, this::selectMenuItem);
        contentTable.add(textButton).width(132f).height(18f).padTop(1f);

        add(contentTable).align(Align.top).padTop(4f).row();
    }

    private TextButton createButton(String text) {
        com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle style =
            new com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle(skin.get(
                "default", com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle.class));
        style.up = infernalUi.button();
        style.down = infernalUi.selectedButton();
        style.checked = infernalUi.selectedButton();
        TextButton button = new TextButton(text, style);
        button.getLabel().setFontScale(0.8f);
        button.getLabel().setColor(InfernalUi.PALE);
        return button;
    }

    private Slider setupVolumeSlider(Table contentTable, String title, MenuOption menuOption) {
        Table table = new Table();
        table.setName(menuOption.name());
        Label label = new Label(title, skin);
        label.setFontScale(0.8f);
        label.setColor(skin.getColor("sand"));
        table.add(label).row();

        Slider slider = new Slider(0.0f, 1f, 0.05f, false, skin);
        slider.setScale(0.85f);
        table.add(slider);
        contentTable.add(table).padTop(1f).row();

        onEnter(table, this::selectMenuItem);
        return slider;
    }

    /**
     * Moves selection to the next menu item.
     */
    @Override
    public void onDown() {
        Group menuContentTable = this.selectedItem.getParent();
        int currentIdx = menuContentTable.getChildren().indexOf(this.selectedItem, true);
        if (currentIdx == -1) {
            throw new GdxRuntimeException("'selectedItem' is not a child of 'menuContentTable'");
        }

        int numOptions = menuContentTable.getChildren().size;
        do {
            currentIdx = (currentIdx + 1) % numOptions;
        } while (!isSelectable(menuContentTable.getChild(currentIdx)));
        selectMenuItem((Group) menuContentTable.getChild(currentIdx));
    }

    /**
     * Moves selection to the previous menu item.
     */
    @Override
    public void onUp() {
        Group menuContentTable = this.selectedItem.getParent();
        int currentIdx = menuContentTable.getChildren().indexOf(this.selectedItem, true);
        if (currentIdx == -1) {
            throw new GdxRuntimeException("'selectedItem' is not a child of 'menuContentTable'");
        }

        int numOptions = menuContentTable.getChildren().size;
        do {
            currentIdx = currentIdx == 0 ? numOptions - 1 : currentIdx - 1;
        } while (!isSelectable(menuContentTable.getChild(currentIdx)));
        selectMenuItem((Group) menuContentTable.getChild(currentIdx));
    }

    private static boolean isSelectable(com.badlogic.gdx.scenes.scene2d.Actor actor) {
        return actor instanceof Group group && group.getName() != null;
    }

    @Override
    public void onRight() {
        MenuOption menuOption = MenuOption.valueOf(this.selectedItem.getName());
        switch (menuOption) {
            case MUSIC_VOLUME, SOUND_VOLUME -> {
                Slider slider = (Slider) this.selectedItem.getChild(1);
                slider.setValue(slider.getValue() + slider.getStepSize());
            }
        }
    }

    @Override
    public void onLeft() {
        MenuOption menuOption = MenuOption.valueOf(this.selectedItem.getName());
        switch (menuOption) {
            case MUSIC_VOLUME, SOUND_VOLUME -> {
                Slider slider = (Slider) this.selectedItem.getChild(1);
                slider.setValue(slider.getValue() - slider.getStepSize());
            }
        }
    }

    @Override
    public void onSelect() {
        MenuOption menuOption = MenuOption.valueOf(this.selectedItem.getName());
        switch (menuOption) {
            case START_GAME -> viewModel.startGame();
            case QUIT_GAME -> viewModel.quitGame();
        }
    }

    private enum MenuOption {
        START_GAME,
        MUSIC_VOLUME,
        SOUND_VOLUME,
        QUIT_GAME
    }
}
