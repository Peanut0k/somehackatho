package io.github.com.quillraven.screen;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
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
import io.github.com.quillraven.GdxGame;
import io.github.com.quillraven.asset.MapAsset;
import io.github.com.quillraven.asset.SkinAsset;
import io.github.com.quillraven.audio.AudioService;
import io.github.com.quillraven.component.Animation2D;
import io.github.com.quillraven.component.Fsm;
import io.github.com.quillraven.component.Graphic;
import io.github.com.quillraven.component.Physic;
import io.github.com.quillraven.component.Player;
import io.github.com.quillraven.component.Transform;
import io.github.com.quillraven.input.GameControllerState;
import io.github.com.quillraven.input.KeyboardController;
import io.github.com.quillraven.ldtk.LdtkAshleyConfigurator;
import io.github.com.quillraven.ldtk.LdtkMap;
import io.github.com.quillraven.ldtk.LdtkService;
import io.github.com.quillraven.system.AnimationSystem;
import io.github.com.quillraven.system.CameraSystem;
import io.github.com.quillraven.system.ChessPieceSystem;
import io.github.com.quillraven.system.ControllerSystem;
import io.github.com.quillraven.system.FacingSystem;
import io.github.com.quillraven.system.FsmSystem;
import io.github.com.quillraven.system.LightingSystem;
import io.github.com.quillraven.system.PhysicMoveSystem;
import io.github.com.quillraven.system.PhysicSystem;
import io.github.com.quillraven.system.RenderSystem;
import io.github.com.quillraven.system.TriggerSystem;
import io.github.com.quillraven.ui.model.GameViewModel;
import io.github.com.quillraven.ui.view.GameView;
import io.github.com.quillraven.ui.view.InfernalUi;

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
            }
            viewModel.setGameMessage(PAWN_ARENA_LEVEL.equals(activeLevel)
                ? "Chessboard: capture the black king."
                : "Explore the empty world and find the pawn portal.");
        });

        ldtkService.setMap(ldtkService.loadMap(MapAsset.MAIN, OPEN_WORLD_LEVEL));
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
        infernalUi.dispose();
        stage.dispose();
    }
}
