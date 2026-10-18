# Pawn World

A small LibGDX prototype with a grassy hub, chess challengers, and a pawn-vs-army arena. Challenge opponents in standard chess; rewards can grant an extra arena life or a one-use double move for a chess duel. Interact with the glowing portal to enter an 8×8 board. You control the only white piece, a pawn on e2, against the complete black army. Capture the black king to win; any black piece can capture you.

## Controls

- In the hub, WASD or arrow keys: move
- Press E near a chess challenger to accept the challenge, or click the challenger
- Press E near the glowing portal to enter the pawn arena
- In a standard chess duel, click a white piece and then a highlighted destination. You play white; the challenger plays black. If unlocked, right-click once per duel to use the double-move ability: make two consecutive legal moves before black responds.
- In the arena, A/D: rotate; W/S: move forward/backward; Q/R: capture diagonally left/right
- Arena lives are shown as hearts, and your current facing direction appears as a small fixed indicator in the corner.
- Hub interaction prompts appear beside the nearby challenger or portal.
- If you are checkmated in a chess duel, the final board stays visible so you can inspect it; press any keyboard key to return to the hub.
- Capturing the black king opens a simple "Ruin Challenge Complete" screen; losing all hearts opens the game-over screen. Both offer retry or return to the main menu.
- Chess challenger wins show a brief upgrade message in the hub. Losing or drawing shows a short retry message; press E nearby to challenge again.
- Each valid arena action takes one turn. One black piece moves after each player action.
- A/D rotates the pawn and also spends a turn.
- The arena highlights the predicted enemy destination on the board. If the enemy reaches your square, it captures you immediately on that turn.
- You start with two lives. An immediate capture costs one life and respawns you on the nearest safe square; losing all lives returns you to the main world. Challenger rewards are shown before accepting each match.
- Player and enemy pieces move between board squares with a short animation.
- Enemy move selection is powered by the local `chess-engine/` Java module, with legal chess moves and an alpha-beta search.
- The predicted source and destination squares are highlighted before your move; the chosen engine move is also printed in the console and is executed after your action.

## Maps

The game uses `PoserSeDane/PoserSeDane.ldtk` and its assets. It starts in `Level_0`; the placed `Kupec` merchants are chess challengers and use the supplied merchant sprite. If there are no placed challengers, three defaults spawn around the player. The portal leads to `Level_1`, where the 8×8 board, the player's pawn, and the black army are created when the arena loads. LDtk layers are composited back-to-front, including entities at their own layer depth. Arena and duel pieces use the supplied `pawn.png`, `horse.png`, `rook.png`, `rock.png`, and `pesak.png` art; the queen reuses `rook.png` because no separate queen image is supplied. A placed `ChessPawn` entity is a static pawn and falls back to `pawn.png` when it has no LDtk tile.

### Chess challenger entity

In LDtk, create an entity definition named `ChessChallenger`, then place its instances in `Level_0`. Add these custom fields:

| Field | LDtk type | Example | Meaning |
|---|---|---|---|
| `challengerId` | String | `guard-01` | Stable, unique ID used to remember that this challenger was defeated. |
| `challengerName` | String | `The Guardian` | Name shown when the player is challenged. |
| `searchDepth` | Int | `2` | Chess AI search depth, from 1 to 5. Higher values take longer to calculate. |
| `rewardType` | String | `ABILITY` | `LIFE` adds one arena life; `ABILITY` unlocks the ability below. |
| `ability` | String | `DOUBLE_MOVE` | `DOUBLE_MOVE` grants one use per chess duel; use `NONE` with a `LIFE` reward. |
| `sprite` | String | `training_dummy/idle_down` | Optional `objects.atlas` region; this is the default. |
| `color` | String | `ffc878` | Optional LibGDX color hex, without `#`; defaults to white. |

Keep `challengerId` unique between placed NPCs. If the level has any `ChessChallenger` entity, only the challengers placed in LDtk are spawned.

LDtk tileset paths exported from the original project are resolved against the available `assets/` files, including the supplied map folder.

## Run

```sh
./gradlew lwjgl3:run
```
