# Pawn World

A small LibGDX prototype with a grassy hub, beginner-friendly chess puzzles, and a pawn-vs-army arena. Challenge the chess players in order: solve an easy mate in two, a more involved mate in two, and a final mate in three. Each puzzle displays a piece-movement guide; play any legal moves and win by checkmating Black within the move limit. Black responds after each move. Each solved puzzle grants an extra arena life. Interact with the glowing portal to enter an 8×8 board. You control the only white piece, a pawn on e2, against the complete black army. Capture the black king to win; any black piece can capture you.

## Controls

- In the hub, WASD or arrow keys: move; hold Shift while moving to sprint.
- Press E near a chess challenger to accept the challenge, or click the challenger
- Press E near the glowing portal to enter the pawn arena
- In a chess puzzle, click any white piece and then a legal destination. The on-screen guide explains every piece. Checkmate Black within the displayed move limit to win; otherwise the attempt ends when you run out of moves. Undo restores the position before your latest turn, including cancelling a pending Black reply. Press any key to return and retry with a different randomly selected puzzle position, or Backspace to give up.
- In the pawn arena, click your pawn and then a highlighted square to move or capture; click an adjacent side square to rotate. Keyboard controls remain available: A/D rotate, W moves forward, and Q/R capture diagonally left/right. Backward movement is not allowed. Winning the first chess puzzle unlocks Hint, which marks the engine's best legal pawn action; winning the second unlocks Double Move, which lets you make two valid actions before enemies respond once. Both abilities are usable once per arena battle and never during chess puzzles.
- The main menu's Approx. Opponent Elo slider sets arena opponent strength from 400 to 2000. It is an approximate difficulty control, not a tournament-calibrated rating. Arena opponents wait one second before responding.
- Press F3 during gameplay to toggle the Box2D physics collider overlay.
- Arena lives are shown as hearts, and your current facing direction appears as a small fixed indicator in the corner.
- Hub interaction prompts appear beside the nearby challenger or portal.
- A compact control guide stays visible at the bottom of the screen and updates for the hub, arena, and duels.
- Mate-in-two and mate-in-three puzzles unlock one at a time, in increasing difficulty. Each stage has several puzzle positions; retries choose a different position from the previous attempt. Completing a puzzle marks its challenger with a green check, marks them defeated, and grants one arena life.
- Capturing the black king opens a simple "Ruin Challenge Complete" screen; losing all hearts opens the game-over screen. Both offer retry or return to the main menu.
- Giving up a puzzle shows the game-over screen; press any key to return to the hub and retry it.
- Each valid arena action takes one turn; one black piece moves after each player action unless Double Move is active.
- A/D rotates the pawn and also spends a turn.
- The arena highlights the predicted enemy destination on the board. If the enemy reaches your square, it captures you immediately on that turn.
- You start with two lives. An immediate capture costs one life and respawns you on the nearest safe square; losing all lives returns you to the main world. Challenger rewards are shown before accepting each match.
- Player and enemy pieces move between board squares with a short animation.
- Enemy move selection is powered by the local `chess-engine/` Java module, with legal chess moves and an alpha-beta search.
- The predicted source and destination squares are highlighted before your move; the chosen engine move is also printed in the console and is executed after your action.

## Maps

The game uses `PoserSeDane/PoserSeDane.ldtk` and its assets. It starts in the hub (`Level_0`); the placed `Kupec` merchants are chess challengers and use the alternate `assets/jitka.png` sprite. If there are no placed challengers, three defaults spawn around the player. The portal leads to `Level_1`; the arena places pieces directly on the existing 8×8 floor board and does not generate a replacement board. The player's chess pieces use their original procedural ivory art. Enemy pieces use the supplied `pawn.png`, `horse.png`, `rook.png`, `bishop.png`, `king.png`, and `queen.png` images; if `queen.png` is unavailable, a distinct generated queen is used instead of the rook. A placed `ChessPawn` entity is a static enemy pawn and falls back to `pawn.png` when it has no LDtk tile.

### Chess challenger entity

In LDtk, create an entity definition named `ChessChallenger`, then place its instances in `Level_0`. Add these custom fields:

| Field | LDtk type | Example | Meaning |
|---|---|---|---|
| `challengerId` | String | `guard-01` | Stable, unique ID used to remember that this challenger was defeated. |
| `challengerName` | String | `The Guardian` | Name shown when the player is challenged. |
| `sprite` | String | `training_dummy/idle_down` | Optional `objects.atlas` region; this is the default. |
| `color` | String | `ffc878` | Optional LibGDX color hex, without `#`; defaults to white. |

Keep `challengerId` unique between placed NPCs. Puzzle progression follows the order in which puzzles are solved, so any three challengers can host the easy mate-in-two, medium mate-in-two, and challenging mate-in-three puzzles. If the level has any `ChessChallenger` entity, only the challengers placed in LDtk are spawned.

Solid LDtk entities can use `colliderWidth` and `colliderHeight` to set the base collider size, and `colliderOffsetX` / `colliderOffsetY` to move it from the entity center. `colliderCutoutWidth` and `colliderCutoutHeight` create a rectangular opening; `colliderCutoutOffsetX` and `colliderCutoutOffsetY` move the opening from the collider center. Keep `disableCollider` false to retain the remaining collider.

LDtk tileset paths exported from the original project are resolved against the available `assets/` files, including the supplied map folder.

## Run

```sh
./gradlew lwjgl3:run
```
