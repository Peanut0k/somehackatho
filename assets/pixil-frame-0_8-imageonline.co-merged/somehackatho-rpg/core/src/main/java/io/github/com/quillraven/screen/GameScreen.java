package io.github.com.peanut0k.screen;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.github.com.peanut0k.GdxGame;
import io.github.com.peanut0k.asset.MapAsset;
import io.github.com.peanut0k.asset.SkinAsset;
import io.github.com.peanut0k.audio.AudioService;
import io.github.com.peanut0k.component.Animation2D;
import io.github.com.peanut0k.component.Fsm;
import io.github.com.peanut0k.component.Graphic;
import io.github.com.peanut0k.component.Physic;
import io.github.com.peanut0k.component.Player;
import io.github.com.peanut0k.component.Inventory;
import io.github.com.peanut0k.component.PlayerProgression;
import io.github.com.peanut0k.component.Controller;
import io.github.com.peanut0k.component.Transform;
import io.github.com.peanut0k.input.GameControllerState;
import io.github.com.peanut0k.input.Command;
import io.github.com.peanut0k.input.KeyboardController;
import io.github.com.peanut0k.ldtk.LdtkAshleyConfigurator;
import io.github.com.peanut0k.ldtk.LdtkMap;
import io.github.com.peanut0k.ldtk.LdtkService;
import io.github.com.peanut0k.system.AnimationSystem;
import io.github.com.peanut0k.system.CameraSystem;
import io.github.com.peanut0k.system.ChessPieceSystem;
import io.github.com.peanut0k.system.ControllerSystem;
import io.github.com.peanut0k.system.FacingSystem;
import io.github.com.peanut0k.system.FsmSystem;
import io.github.com.peanut0k.system.LightingSystem;
import io.github.com.peanut0k.system.PhysicMoveSystem;
import io.github.com.peanut0k.system.PhysicSystem;
import io.github.com.peanut0k.system.RenderSystem;
import io.github.com.peanut0k.system.TriggerSystem;
import io.github.com.peanut0k.ui.model.GameViewModel;
import io.github.com.peanut0k.ui.view.GameView;
import io.github.com.peanut0k.ui.view.InfernalUi;

import java.util.ArrayList;

public final class GameScreen extends ScreenAdapter {
    private static final String OPEN_WORLD_LEVEL = "Level_0";
    private static final String PAWN_ARENA_LEVEL = "Level_1";

    private final GdxGame game;
    private final Stage stage;
    private final Skin skin;
    private final GameViewModel viewModel;
    private final Viewport uiViewport;
    private final LdtkService ldtkService;
    private final Engine engine;
    private final LdtkAshleyConfigurator entityConfigurator;
    private final World physicsWorld;
    private final LightingSystem lightingSystem;
    private final KeyboardController keyboardController;
    private final AudioService audioService;
    private final ChessPieceFactory chessPieceFactory;
    private final ChessPieceSystem chessPieceSystem;
    private final ControllerSystem controllerSystem;
    private final InfernalUi infernalUi;
    private TextureRegion playerRegion;
    private Animation2D playerAnimation;
    private Fsm playerFsm;
    private Vector2 playerSize;
    private String activeLevel = OPEN_WORLD_LEVEL;
    private boolean transitionPending;
    private boolean rpgQuestAccepted;
    private boolean rpgQuestComplete;
    private boolean groveSearched;
    private boolean rewardClaimed;
    private Texture rpgMarkerTexture;
    private Texture groveMarkerTexture;

    public GameScreen(GdxGame game) {
        this.game = game;
        uiViewport = new FitViewport(320f, 180f);
        stage = new Stage(uiViewport, game.getBatch());
        skin = game.getAssetService().get(SkinAsset.DEFAULT);
        viewModel = new GameViewModel(game);
        infernalUi = new InfernalUi();
        audioService = game.getAudioService();
        physicsWorld = new World(Vector2.Zero, true);
        physicsWorld.setAutoClearForces(false);
        ldtkService = new LdtkService(physicsWorld);
        engine = new Engine();
        lightingSystem = new LightingSystem(physicsWorld, game.getCamera());
        chessPieceFactory = new ChessPieceFactory();
        entityConfigurator = new LdtkAshleyConfigurator(engine, physicsWorld, game.getAssetService(),
            lightingSystem.getRayHandler());
        keyboardController = new KeyboardController(GameControllerState.class, engine, null);
        chessPieceSystem = new ChessPieceSystem(viewModel, chessPieceFactory.getPredictionRegion(),
            victory -> Gdx.app.postRunnable(() -> handleCombatFinished(victory)));
        controllerSystem = new ControllerSystem(chessPieceSystem);

        engine.addSystem(chessPieceSystem);
        engine.addSystem(new EntitySystem() {
            @Override
            public void update(float deltaTime) {
                handleRpgInteractions();
            }
        });
        engine.addSystem(new PhysicMoveSystem());
        engine.addSystem(new PhysicSystem(physicsWorld, 1 / 60f));
        engine.addSystem(new FacingSystem());
        engine.addSystem(new FsmSystem());
        engine.addSystem(new TriggerSystem(this::requestPawnArenaEntry));
        engine.addSystem(new AnimationSystem(game.getAssetService()));
        engine.addSystem(new CameraSystem(game.getCamera()));
        engine.addSystem(new RenderSystem(game.getBatch(), game.getViewport(), game.getCamera()));
        engine.addSystem(lightingSystem);
        engine.addSystem(controllerSystem);
    }

    @Override
    public void show() {
        game.setInputProcessors(stage, keyboardController);
        keyboardController.setActiveState(GameControllerState.class);
        stage.addActor(new GameView(stage, skin, viewModel, infernalUi));

        ldtkService.setLoadEntityConsumer(entityConfigurator::onLoadEntity);
        ldtkService.setMapChangeConsumer(map -> {
            entityConfigurator.createDefaultPlayer(map);
            engine.getSystem(RenderSystem.class).setMap(map);
            engine.getSystem(CameraSystem.class).setMap(map);
            audioService.setMap(map);
            chessPieceSystem.setMap(map);
            controllerSystem.setPawnArena(PAWN_ARENA_LEVEL.equals(activeLevel));
            boolean pawnArena = PAWN_ARENA_LEVEL.equals(activeLevel);
            setPlayerAppearance(pawnArena);
            if (pawnArena) {
                ImmutableArray<Entity> players = engine.getEntitiesFor(Family.all(Player.class).get());
                if (players.size() > 0) {
                    chessPieceFactory.createStartingPosition(engine, physicsWorld, map, players.first());
                    chessPieceSystem.initializeBoard();
                }
            } else {
                createRpgMarkers(map);
                updateRpgStatus();
            }
            viewModel.setGameMessage(PAWN_ARENA_LEVEL.equals(activeLevel)
                ? "Chessboard: capture the black king."
                : rpgQuestAccepted
                    ? rpgQuestComplete ? "The forest is safe. Return to the villager." : "Explore the grove and find a lost soul."
                    : "Speak to the villager. Use SPACE when nearby.");
        });

        ldtkService.setMap(ldtkService.loadMap(MapAsset.MAIN, OPEN_WORLD_LEVEL));
    }

    private void handleRpgInteractions() {
        if (!OPEN_WORLD_LEVEL.equals(activeLevel)) return;
        ImmutableArray<Entity> players = engine.getEntitiesFor(Family.all(Player.class).get());
        if (players.size() == 0) return;
        Entity player = players.first();
        Controller controller = Controller.MAPPER.get(player);
        if (controller == null || controller.getPressedCommands().isEmpty()) return;
        boolean interact = controller.getPressedCommands().contains(Command.SELECT);
        controller.getPressedCommands().removeIf(command -> command == Command.SELECT);
        if (!interact) return;

        Vector2 playerPosition = Physic.MAPPER.get(player).getBody().getWorldCenter();
        if (!rpgQuestAccepted && near(playerPosition, 304f, 205f, 46f)) {
            rpgQuestAccepted = true;
            viewModel.setGameMessage("The villager asks you to search the old grove.");
            logRpg("Quest started: A Soul Lost in the Grove.");
        } else if (rpgQuestAccepted && !groveSearched && near(playerPosition, 400f, 285f, 48f)) {
            groveSearched = true;
            Inventory inventory = Inventory.MAPPER.get(player);
            if (inventory == null) {
                inventory = new Inventory();
                player.add(inventory);
            }
            inventory.addSoulFragments(1);
            PlayerProgression progression = PlayerProgression.MAPPER.get(player);
            if (progression == null) {
                progression = new PlayerProgression();
                player.add(progression);
            }
            int levels = progression.addExperience(3);
            rpgQuestComplete = true;
            updateRpgStatus();
            viewModel.setGameMessage(levels > 0
                ? "The lost soul joins you. You reached level " + progression.getLevel() + "!"
                : "You found a soul fragment. Return to the villager.");
            logRpg("Found a soul fragment. +3 XP" + (levels > 0 ? " · Level up!" : ""));
        } else if (rpgQuestComplete && !rewardClaimed && near(playerPosition, 304f, 205f, 46f)) {
            rewardClaimed = true;
            Inventory inventory = Inventory.MAPPER.get(player);
            if (inventory != null) inventory.addSoulFragments(2);
            PlayerProgression progression = PlayerProgression.MAPPER.get(player);
            if (progression != null) progression.addExperience(3);
            updateRpgStatus();
            viewModel.setGameMessage("Quest complete! The villager rewards you. The chess portal remains open.");
            logRpg("Quest complete. Received 2 soul fragments and 3 XP.");
        } else if (near(playerPosition, 304f, 205f, 46f)) {
            viewModel.setGameMessage(rpgQuestAccepted
                ? "The villager: Please find the lost soul in the old grove."
                : "Villager: A soul is missing from the old grove. Will you find it?");
        } else if (near(playerPosition, 400f, 285f, 48f)) {
            viewModel.setGameMessage("An ancient grove. Press SPACE to search.");
        } else {
            viewModel.setGameMessage("Nothing to interact with here.");
        }
    }

    private void createRpgMarkers(LdtkMap map) {
        if (rpgMarkerTexture == null) {
            Pixmap pixmap = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
            pixmap.setColor(0.12f, 0.18f, 0.16f, 0.8f);
            pixmap.fillCircle(8, 8, 7);
            pixmap.setColor(0.95f, 0.72f, 0.31f, 1f);
            pixmap.fillCircle(8, 5, 3);
            pixmap.fillRectangle(4, 8, 8, 5);
            rpgMarkerTexture = new Texture(pixmap);
            rpgMarkerTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            pixmap.dispose();

            Pixmap grove = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
            grove.setColor(0.1f, 0.2f, 0.13f, 0.8f);
            grove.fillCircle(8, 8, 7);
            grove.setColor(0.48f, 0.82f, 0.43f, 1f);
            grove.fillTriangle(8, 2, 3, 11, 13, 11);
            grove.setColor(0.8f, 0.9f, 0.55f, 1f);
            grove.fillRectangle(7, 9, 2, 4);
            groveMarkerTexture = new Texture(grove);
            groveMarkerTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            grove.dispose();
        }
        addRpgMarker(304f, 221f, rpgMarkerTexture);
        addRpgMarker(400f, 285f, groveMarkerTexture);
    }

    private void addRpgMarker(float x, float y, Texture texture) {
        Entity marker = engine.createEntity();
        marker.add(new Transform(new Vector2(x - 8f, y - 8f), 4,
            new Vector2(16f, 16f), new Vector2(1f, 1f), 0f, 0f));
        marker.add(new Graphic(new TextureRegion(texture), Color.WHITE.cpy()));
        engine.addEntity(marker);
    }

    private static boolean near(Vector2 position, float x, float y, float radius) {
        return position.dst2(x, y) <= radius * radius;
    }

    private void updateRpgStatus() {
        ImmutableArray<Entity> players = engine.getEntitiesFor(Family.all(Player.class).get());
        if (players.size() == 0) return;
        Entity player = players.first();
        PlayerProgression progression = PlayerProgression.MAPPER.get(player);
        Inventory inventory = Inventory.MAPPER.get(player);
        int level = progression == null ? 1 : progression.getLevel();
        int xp = progression == null ? 0 : progression.getExperience();
        int next = progression == null ? 3 : progression.getExperienceToNextLevel();
        int souls = inventory == null ? 0 : inventory.getSoulFragments();
        viewModel.setRpgStatus("LV " + level + "  ·  XP " + xp + "/" + next + "  ·  Souls " + souls);
    }

    private void logRpg(String message) {
        Gdx.app.log("RPG", message);
    }

    private void setPlayerAppearance(boolean pawn) {
        ImmutableArray<Entity> players = engine.getEntitiesFor(Family.all(Player.class).get());
        if (players.size() == 0) return;
        Entity player = players.first();
        Graphic graphic = Graphic.MAPPER.get(player);
        Transform transform = Transform.MAPPER.get(player);
        if (playerRegion == null) {
            playerRegion = graphic.getRegion();
            playerAnimation = Animation2D.MAPPER.get(player);
            playerFsm = Fsm.MAPPER.get(player);
            playerSize = transform.getSize().cpy();
        }

        if (pawn) {
            player.remove(Animation2D.class);
            player.remove(Fsm.class);
            graphic.setRegion(chessPieceFactory.getPawnRegion());
            transform.getSize().set(16f, 16f);
        } else {
            if (playerAnimation != null) player.add(playerAnimation);
            if (playerFsm != null) player.add(playerFsm);
            graphic.setRegion(playerRegion);
            transform.getSize().set(playerSize);
        }
        configurePlayerCollider(player, pawn);
    }

    private static void configurePlayerCollider(Entity player, boolean pawn) {
        Body body = Physic.MAPPER.get(player).getBody();
        while (body.getFixtureList().size > 0) {
            body.destroyFixture(body.getFixtureList().first());
        }
        PolygonShape shape = new PolygonShape();
        float halfSize = pawn ? 5f : 3f;
        float center = pawn ? 8f : 16f;
        shape.setAsBox(halfSize, pawn ? 5f : 2f, new Vector2(center, center), 0f);
        body.createFixture(shape, 1f).setFriction(0f);
        shape.dispose();
    }

    private void requestPawnArenaEntry() {
        if (transitionPending || !OPEN_WORLD_LEVEL.equals(activeLevel)) return;
        transitionPending = true;
        Gdx.app.postRunnable(() -> {
            try {
                changeLevel(PAWN_ARENA_LEVEL);
            } finally {
                transitionPending = false;
            }
        });
    }

    private void handleCombatFinished(boolean victory) {
        if (!PAWN_ARENA_LEVEL.equals(activeLevel) || transitionPending) return;
        transitionPending = true;
        try {
            changeLevel(OPEN_WORLD_LEVEL);
            viewModel.setGameMessage(victory
                ? "Arena clear. Find the portal to play again."
                : "You lost both lives and returned to the main world.");
        } finally {
            transitionPending = false;
        }
    }

    private void changeLevel(String levelIdentifier) {
        LdtkMap nextMap = ldtkService.loadMap(MapAsset.MAIN, levelIdentifier);
        removeCurrentLevelEntities();
        activeLevel = levelIdentifier;
        ldtkService.setMap(nextMap);
    }

    private void removeCurrentLevelEntities() {
        ImmutableArray<Entity> entities = engine.getEntitiesFor(Family.all().get());
        ArrayList<Entity> toRemove = new ArrayList<>();
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            if (Player.MAPPER.get(entity) == null) toRemove.add(entity);
        }
        for (Entity entity : toRemove) engine.removeEntity(entity);
    }

    @Override
    public void hide() {
        engine.removeAllEntities();
        stage.clear();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        uiViewport.update(width, height, true);
        lightingSystem.resize(width, height);
    }

    @Override
    public void render(float delta) {
        engine.update(Math.min(1 / 30f, delta));
        uiViewport.apply();
        stage.getBatch().setColor(Color.WHITE);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void dispose() {
        super.dispose();
        for (EntitySystem system : engine.getSystems()) {
            if (system instanceof Disposable disposable) disposable.dispose();
        }
        physicsWorld.dispose();
        ldtkService.dispose();
        entityConfigurator.dispose();
        chessPieceFactory.dispose();
        if (rpgMarkerTexture != null) rpgMarkerTexture.dispose();
        if (groveMarkerTexture != null) groveMarkerTexture.dispose();
        infernalUi.dispose();
        stage.dispose();
    }
}
