package io.github.com.peanut0k.screen;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
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
import io.github.com.peanut0k.GdxGame;
import io.github.com.peanut0k.asset.MapAsset;
import io.github.com.peanut0k.asset.SkinAsset;
import io.github.com.peanut0k.audio.AudioService;
import io.github.com.peanut0k.component.Animation2D;
import io.github.com.peanut0k.component.BoardMotion;
import io.github.com.peanut0k.component.ChessChallenger;
import io.github.com.peanut0k.component.Facing;
import io.github.com.peanut0k.component.Fsm;
import io.github.com.peanut0k.component.Graphic;
import io.github.com.peanut0k.component.Physic;
import io.github.com.peanut0k.component.Player;
import io.github.com.peanut0k.component.Transform;
import io.github.com.peanut0k.input.GameControllerState;
import io.github.com.peanut0k.input.ChessMouseController;
import io.github.com.peanut0k.input.KeyboardController;
import io.github.com.peanut0k.ldtk.LdtkAshleyConfigurator;
import io.github.com.peanut0k.ldtk.LdtkMap;
import io.github.com.peanut0k.ldtk.LdtkService;
import io.github.com.peanut0k.system.AnimationSystem;
import io.github.com.peanut0k.system.BoardMotionSystem;
import io.github.com.peanut0k.system.CameraSystem;
import io.github.com.peanut0k.system.ChessDuelAbility;
import io.github.com.peanut0k.system.ChessDuelSystem;
import io.github.com.peanut0k.system.ChessPieceSystem;
import io.github.com.peanut0k.system.ControllerSystem;
import io.github.com.peanut0k.system.FacingSystem;
import io.github.com.peanut0k.system.FsmSystem;
import io.github.com.peanut0k.system.LightingSystem;
import io.github.com.peanut0k.system.PhysicMoveSystem;
import io.github.com.peanut0k.system.PhysicSystem;
import io.github.com.peanut0k.system.PhysicsDebugSystem;
import io.github.com.peanut0k.system.RenderSystem;
import io.github.com.peanut0k.system.TriggerSystem;
import io.github.com.peanut0k.ui.model.GameViewModel;
import io.github.com.peanut0k.ui.view.GameView;
import io.github.com.peanut0k.ui.view.InfernalUi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

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
    private final PhysicsDebugSystem physicsDebugSystem;
    private final KeyboardController keyboardController;
    private final InputAdapter physicsDebugController;
    private final InputAdapter resultKeyboardController;
    private final AudioService audioService;
    private final ChessPieceFactory chessPieceFactory;
    private final ChessPieceSystem chessPieceSystem;
    private final TriggerSystem triggerSystem;
    private final ChessChallengerFactory challengerFactory;
    private final ChessDuelSystem chessDuelSystem;
    private final ControllerSystem controllerSystem;
    private final ChessMouseController mouseController;
    private final GameView gameView;
    private final InfernalUi infernalUi;
    private final Set<String> defeatedChallengers = new HashSet<>();
    private TextureRegion playerRegion;
    private Animation2D playerAnimation;
    private Fsm playerFsm;
    private Vector2 playerSize;
    private String activeLevel = OPEN_WORLD_LEVEL;
    private boolean transitionPending;
    private Entity nearbyChallenger;
    private Entity nearbyArenaPortal;
    private boolean duelResultPending;
    private boolean arenaResultPending;
    private boolean arenaResultWasVictory;

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
        CameraSystem cameraSystem = new CameraSystem(game.getCamera());
        physicsDebugSystem = new PhysicsDebugSystem(physicsWorld, game.getCamera());
        physicsDebugController = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode != com.badlogic.gdx.Input.Keys.F3) return false;
                physicsDebugSystem.toggle();
                Gdx.app.log("PhysicsDebug", physicsDebugSystem.isEnabled()
                    ? "Collider overlay enabled." : "Collider overlay disabled.");
                return true;
            }
        };
        chessPieceFactory = new ChessPieceFactory();
        challengerFactory = new ChessChallengerFactory(game.getAssetService());
        entityConfigurator = new LdtkAshleyConfigurator(engine, physicsWorld, game.getAssetService(),
            lightingSystem.getRayHandler());
        keyboardController = new KeyboardController(GameControllerState.class, engine, null);
        resultKeyboardController = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (!arenaResultPending && !duelResultPending) return false;
                dismissResultWithKeyboard();
                return true;
            }
        };
        chessPieceSystem = new ChessPieceSystem(viewModel, chessPieceFactory.getPredictionRegion(),
            victory -> Gdx.app.postRunnable(() -> handleCombatFinished(victory)));
        chessPieceSystem.setOpponentElo(game.getOpponentElo());
        chessDuelSystem = new ChessDuelSystem(chessPieceFactory, viewModel, cameraSystem,
            result -> Gdx.app.postRunnable(() -> handleDuelFinished(result)));
        triggerSystem = new TriggerSystem(this::requestPawnArenaEntry, this::onChallengerChanged,
            this::onArenaPortalChanged);
        controllerSystem = new ControllerSystem(chessPieceSystem, chessDuelSystem, this::handleInteraction);
        gameView = new GameView(stage, skin, viewModel, infernalUi);
        mouseController = new ChessMouseController(
            game.getViewport(), chessDuelSystem, chessPieceSystem,
            this::handleHelpClick, this::handleAbilityClick, this::handleHubClick);

        engine.addSystem(chessPieceSystem);
        engine.addSystem(new PhysicMoveSystem());
        engine.addSystem(new PhysicSystem(physicsWorld, 1 / 60f));
        engine.addSystem(new BoardMotionSystem());
        engine.addSystem(new FacingSystem());
        engine.addSystem(new FsmSystem());
        engine.addSystem(triggerSystem);
        engine.addSystem(new AnimationSystem(game.getAssetService()));
        engine.addSystem(cameraSystem);
        engine.addSystem(new RenderSystem(game.getBatch(), game.getViewport(), game.getCamera()));
        engine.addSystem(lightingSystem);
        engine.addSystem(controllerSystem);
    }

    @Override
    public void show() {
        game.setInputProcessors(
            physicsDebugController, mouseController, stage, resultKeyboardController, keyboardController);
        keyboardController.setActiveState(GameControllerState.class);
        stage.addActor(gameView);

        ldtkService.setLoadEntityConsumer(entityConfigurator::onLoadEntity);
        ldtkService.setMapChangeConsumer(map -> {
            entityConfigurator.createDefaultPlayer(map);
            engine.getSystem(RenderSystem.class).setMap(map);
            engine.getSystem(CameraSystem.class).setMap(
                map,
                PAWN_ARENA_LEVEL.equals(activeLevel)
            );
            audioService.setMap(map);
            chessPieceSystem.setMap(map);
            controllerSystem.setPawnArena(PAWN_ARENA_LEVEL.equals(activeLevel));
            boolean pawnArena = PAWN_ARENA_LEVEL.equals(activeLevel);
            chessPieceSystem.setArenaActive(pawnArena);
            gameView.setArenaActive(pawnArena);
            gameView.setDuelActive(false);
            setPlayerAppearance(pawnArena);
            if (pawnArena) {
                ImmutableArray<Entity> players = engine.getEntitiesFor(Family.all(Player.class).get());
                if (players.size() > 0) {
                    chessPieceFactory.createStartingPosition(engine, physicsWorld, players.first());
                    chessPieceSystem.initializeBoard();
                }
            } else {
                entityConfigurator.createDefaultArenaPortalIfMissing(map);
                ImmutableArray<Entity> players = engine.getEntitiesFor(Family.all(Player.class, Transform.class).get());
                if (players.size() > 0) {
                    if (engine.getEntitiesFor(Family.all(ChessChallenger.class).get()).size() == 0) {
                        challengerFactory.create(engine, physicsWorld,
                            Transform.MAPPER.get(players.first()).getPosition(), defeatedChallengers);
                    }
                }
                ImmutableArray<Entity> challengers =
                    engine.getEntitiesFor(Family.all(ChessChallenger.class).get());
                for (Entity challengerEntity : challengers) {
                    ChessChallenger challenger = ChessChallenger.MAPPER.get(challengerEntity);
                    challenger.setDefeated(defeatedChallengers.contains(challenger.getId()));
                    challengerFactory.addCompletionMarker(engine, challengerEntity, challenger);
                }
            }
            viewModel.setGameMessage(PAWN_ARENA_LEVEL.equals(activeLevel)
                ? "Chessboard: capture the black king."
                : "Explore the hub, challenge chess players, and find the pawn portal.");
            viewModel.setControls(PAWN_ARENA_LEVEL.equals(activeLevel)
                ? "ARENA  |  CLICK PAWN, THEN A HIGHLIGHTED SQUARE\nCLICK BESIDE PAWN TO TURN  |  W MOVE\nA/D TURN  |  Q/R CAPTURE  |  HINT / DOUBLE MOVE BUTTONS"
                : hubControls());
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
            transform.getSize().set(
                ChessPieceFactory.ARENA_CELL_SIZE,
                ChessPieceFactory.ARENA_CELL_SIZE
            );
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
        shape.dispose();// !!! Important !!!
        // This does not work if the Physic component gets removed from an entity
        // because the component is no longer accessible here.
        // This ONLY works when an entity with a Physic component gets removed entirely from the engine.
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
        finishBoardMotions();
        gameView.setArenaActive(false);
        arenaResultPending = false;
        transitionPending = true;
        try {
            if (victory) {
                gameView.showResultMessage("Victory! Arena restarting.");
                chessPieceSystem.resetLives();
                changeLevel(PAWN_ARENA_LEVEL);
            } else {
                defeatedChallengers.clear();
                chessPieceSystem.resetProgression();
                gameView.hideArenaResult();
                gameView.showResultMessage("Defeated. Returning to your spawn.");
                changeLevel(OPEN_WORLD_LEVEL);
            }
        } finally {
            transitionPending = false;
        }
    }

    private void dismissResultWithKeyboard() {
        if (arenaResultPending) {
            arenaResultPending = false;
            transitionPending = true;
            gameView.hideArenaResult();
            try {
                changeLevel(OPEN_WORLD_LEVEL);
                if (!arenaResultWasVictory) chessPieceSystem.resetLives();
            } finally {
                transitionPending = false;
            }
            return;
        }
        if (duelResultPending) {
            duelResultPending = false;
            gameView.hideArenaResult();
            chessDuelSystem.dismissFinishedDuel();
            controllerSystem.setChessDuel(false);
            gameView.setDuelActive(false);
            viewModel.setControls(hubControls());
            updateInteractionPrompt();
        }
    }

    private void onChallengerChanged(Entity challenger) {
        if (!OPEN_WORLD_LEVEL.equals(activeLevel)) {
            nearbyChallenger = null;
            return;
        }
        if (nearbyChallenger == challenger) return;
        nearbyChallenger = challenger;
        if (challenger != null) duelResultPending = false;
        updateInteractionPrompt();
    }

    private void onArenaPortalChanged(Entity portal) {
        if (!OPEN_WORLD_LEVEL.equals(activeLevel)) {
            nearbyArenaPortal = null;
            updateInteractionPrompt();
            return;
        }
        nearbyArenaPortal = portal;
        updateInteractionPrompt();
    }

    private void updateInteractionPrompt() {
        if (!OPEN_WORLD_LEVEL.equals(activeLevel) || chessDuelSystem.isActive()
            || duelResultPending || arenaResultPending) {
            gameView.setInteractionBubble(null, null, game.getViewport());
            return;
        }
        if (nearbyChallenger != null) {
            ChessChallenger details = ChessChallenger.MAPPER.get(nearbyChallenger);
            if (details.isDefeated()) {
                gameView.setCenteredInteractionMessage(details.getName() + "\nAlready defeated");
            } else if (isPuzzleProgressionLocked()) {
                gameView.setCenteredInteractionMessage("Complete earlier chess lessons first");
            } else {
                gameView.setCenteredInteractionMessage(
                    "E: Challenge " + details.getName() + "\nWin: +1 life");
            }
        } else if (nearbyArenaPortal != null) {
            gameView.setCenteredInteractionMessage("E: Enter Arena");
        } else {
            gameView.setInteractionBubble(null, null, game.getViewport());
        }
    }

    private boolean isPuzzleProgressionLocked() {
        return defeatedChallengers.size() >= ChessDuelSystem.PUZZLE_COUNT;
    }

    private void handleInteraction() {
        if (!OPEN_WORLD_LEVEL.equals(activeLevel) || chessDuelSystem.isActive()
            || duelResultPending || arenaResultPending) return;
        if (nearbyChallenger == null) {
            triggerSystem.interactWithArenaPortal();
            return;
        }
        ChessChallenger challenger = ChessChallenger.MAPPER.get(nearbyChallenger);
        if (challenger == null || challenger.isDefeated()
            || isPuzzleProgressionLocked()) return;
        ImmutableArray<Entity> players = engine.getEntitiesFor(Family.all(Player.class, Transform.class).get());
        if (players.size() == 0) return;
        duelResultPending = false;
        controllerSystem.setChessDuel(true);
        gameView.setDuelActive(true);
        gameView.setInteractionBubble(null, null, game.getViewport());
        chessDuelSystem.start(engine, Transform.MAPPER.get(players.first()).getPosition(),
            challenger.getName(), defeatedChallengers.size());
    }

    private boolean handleHubClick(Vector2 worldPosition) {
        if (!OPEN_WORLD_LEVEL.equals(activeLevel) || duelResultPending || arenaResultPending) return false;
        ImmutableArray<Entity> challengers = engine.getEntitiesFor(Family.all(ChessChallenger.class, Transform.class).get());
        for (Entity entity : challengers) {
            ChessChallenger challenger = ChessChallenger.MAPPER.get(entity);
            if (challenger.isDefeated()) continue;
            Transform transform = Transform.MAPPER.get(entity);
            Vector2 position = transform.getPosition();
            Vector2 size = transform.getSize();
            if (worldPosition.x < position.x || worldPosition.x > position.x + size.x
                || worldPosition.y < position.y || worldPosition.y > position.y + size.y) continue;
            nearbyChallenger = entity;
            handleInteraction();
            return true;
        }
        return false;
    }

    private boolean handleHelpClick(Vector2 screenPosition) {
        return gameView.dismissControlsAtScreenPosition(screenPosition.x, screenPosition.y);
    }

    private boolean handleAbilityClick(Vector2 screenPosition) {
        ChessDuelAbility ability =
            gameView.abilityAtScreenPosition(screenPosition.x, screenPosition.y);
        if (ability == null) return false;
        if (chessDuelSystem.isActive()) {
            chessDuelSystem.activateAbility(ability);
        } else if (chessPieceSystem.isArenaActive()) {
            chessPieceSystem.activateAbility(ability);
        }
        return true;
    }

    private void handleDuelFinished(ChessDuelSystem.Result result) {
        gameView.setDuelActive(false);
        if (result == ChessDuelSystem.Result.LOSS) {
            finishBoardMotions();
            duelResultPending = true;
            viewModel.setControls("GAME OVER  |  PRESS ANY KEY TO RETURN TO THE HUB");
            gameView.showGameOver(viewModel.getGameMessage());
            return;
        }
        controllerSystem.setChessDuel(false);
        duelResultPending = false;
        viewModel.setControls(hubControls());
        if (result == ChessDuelSystem.Result.WIN) {
            ChessChallenger challenger = ChessChallenger.MAPPER.get(nearbyChallenger);
            if (challenger != null) {
                challenger.setDefeated(true);
                defeatedChallengers.add(challenger.getId());
                chessPieceSystem.awardLife();
                String reward = switch (chessDuelSystem.getPuzzleIndex()) {
                    case 0 -> {
                        chessPieceSystem.unlockHint();
                        yield " Hint unlocked for the pawn arena.";
                    }
                    case 1 -> {
                        chessPieceSystem.unlockDoubleMove();
                        yield " Double Move unlocked for the pawn arena.";
                    }
                    default -> "";
                };
                gameView.showResultMessage("Puzzle solved! +1 life." + reward);
            }
            nearbyChallenger = null;
        } else if (result == ChessDuelSystem.Result.DRAW) {
            gameView.showResultMessage("Draw. Press E to try again.");
        } else {
            gameView.showResultMessage("Lost. Press E to try again.");
        }
        updateInteractionPrompt();
    }

    private void finishBoardMotions() {
        ImmutableArray<Entity> movingEntities =
            engine.getEntitiesFor(Family.all(BoardMotion.class, Transform.class).get());
        ArrayList<Entity> toFinish = new ArrayList<>();
        for (int i = 0; i < movingEntities.size(); i++) toFinish.add(movingEntities.get(i));
        for (Entity entity : toFinish) {
            BoardMotion motion = BoardMotion.MAPPER.get(entity);
            Transform.MAPPER.get(entity).getPosition().set(motion.getTarget());
            entity.remove(BoardMotion.class);
        }
    }

    private static String hubControls() {
        return "HUB  |  WASD / ARROWS MOVE  |  SHIFT SPRINT\nE CHALLENGE OR ENTER ARENA  |  CLICK A DUELIST";
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
        gameView.resize();
    }

    @Override
    public void render(float delta) {
        if (arenaResultPending || duelResultPending) {
            engine.getSystem(RenderSystem.class).update(0f);
            lightingSystem.update(0f);
        } else {
            float frameDelta = Math.min(1 / 30f, delta);
            chessDuelSystem.update(frameDelta);
            engine.update(frameDelta);
        }
        updateInteractionPrompt();
        updateDirectionIndicator();
        gameView.updateAbilityState(
            chessPieceSystem.isHintEarned(),
            chessPieceSystem.isHintAvailable(),
            chessPieceSystem.isDoubleMoveEarned(),
            chessPieceSystem.isDoubleMoveAvailable(),
            chessDuelSystem.isUndoAvailable(),
            chessDuelSystem.isUndoSpent());
        physicsDebugSystem.render();
        uiViewport.apply();
        stage.getBatch().setColor(Color.WHITE);
        stage.act(delta);
        stage.draw();
    }

    private void updateDirectionIndicator() {
        if (!PAWN_ARENA_LEVEL.equals(activeLevel)) {
            gameView.setDirectionIndicator(null);
            return;
        }
        ImmutableArray<Entity> players = engine.getEntitiesFor(Family.all(Player.class, Transform.class).get());
        if (players.size() == 0) {
            gameView.setDirectionIndicator(null);
            return;
        }
        Entity player = players.first();
        Facing facing = Facing.MAPPER.get(player);
        if (facing == null) {
            gameView.setDirectionIndicator(null);
            return;
        }
        String direction = switch (facing.getDirection()) {
            case UP -> "Facing: UP";
            case DOWN -> "Facing: DOWN";
            case LEFT -> "Facing: LEFT";
            case RIGHT -> "Facing: RIGHT";
        };
        gameView.setDirectionIndicator(direction);
    }

    @Override
    public void dispose() {
        super.dispose();
        for (EntitySystem system : engine.getSystems()) {
            if (system instanceof Disposable disposable) disposable.dispose();
        }
        physicsDebugSystem.dispose();
        physicsWorld.dispose();
        ldtkService.dispose();
        entityConfigurator.dispose();
        chessPieceFactory.dispose();
        challengerFactory.dispose();
        infernalUi.dispose();
        stage.dispose();
    }
}
