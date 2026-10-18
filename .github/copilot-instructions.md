# Copilot instructions

## Build, run, and test

Use the Gradle wrapper from the repository root (Java 17; wrapper uses Gradle 8.14.1):

```sh
./gradlew build
./gradlew lwjgl3:run
./gradlew check
```

There are currently no test source files or test-framework dependencies. After adding a test framework and tests under the relevant module's `src/test/java` tree, use Gradle's test filter to run one test, for example:

```sh
./gradlew :chess-engine:test --tests 'io.github.com.quillraven.chessengine.PositionTest'
./gradlew :chess-engine:test --tests 'io.github.com.quillraven.chessengine.PositionTest.testLegalMoves'
```

No dedicated lint plugin or lint task is configured; `check` runs the standard Gradle verification tasks.

## Architecture

- This is a multi-project Java/libGDX game. `lwjgl3` is the desktop launcher and depends on `core`; `core` contains the game; `chess-engine` is the independent chess rules/search module consumed by `core`; `util` contains the libGDX texture-packing tool.
- `GdxGame` owns shared services, rendering resources, and cached screens. `GameScreen` composes the Ashley `Engine`, Box2D `World`, map-loading and asset services, input, and gameplay/UI systems. Keep gameplay behavior in ECS components and systems rather than growing the launcher.
- `LdtkService` loads the LDtk map and manages map collision geometry. `LdtkAshleyConfigurator` maps LDtk entity instances and fields to Ashley entities/components. `GameScreen` connects map changes to rendering, audio, player setup, and arena initialization.
- `ChessPieceSystem` adapts the game board and entities to the pure Java `chess-engine` model, handles player/enemy turns, and sends completion state back to the screen. Keep chess legality/search logic in `chess-engine`, not in rendering or input code.
- The UI follows a view-model/view split (`ui/model` and `ui/view`); controller input is translated into `Command`s and consumed by controller states and systems.
- Edit `tilemap.ldtk` and root `assets_raw/` as source assets. Gradle's `syncLdtkAssets` copies them into `assets/`; resource processing also generates `assets/assets.txt`. Avoid treating generated copies or `assets.txt` as the source of truth.
- The game starts in LDtk `Level_0`; the portal enters `Level_1`, where the chess arena is initialized. See `README.md` for player controls and gameplay details.

## Code conventions

- Java sources use the `io.github.com.quillraven` package tree, with gameplay code in `core` and chess rules in `chess-engine`.
- ECS components expose a static Ashley `ComponentMapper` named `MAPPER`; systems select entities with Ashley `Family` queries and read/write components through those mappers.
- LDtk entity identifiers and custom fields are part of the map-to-game contract. When changing an entity's expected identifier or field, update the LDtk map and its consumer/configurator together; preserve the explicit runtime validation for unsupported values.
- Keep desktop-specific startup/native dependencies in `lwjgl3`. `core` is the shared game implementation and `chess-engine` has no libGDX dependency.
- Root `tilemap.ldtk` and `assets_raw/` feed `assets/` automatically through `generateAssetList`/`processResources`; the generated asset list is sorted and should not be hand-maintained.
