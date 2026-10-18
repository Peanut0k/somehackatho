# Pawn World

A top-down 2D RPG prototype built with LibGDX. Explore the forest hub, speak with the villager, search the old grove for a lost soul, collect soul fragments, and gain experience. The chess arena is an optional side activity reached through the glowing portal.

## Controls

- In the world, WASD or arrow keys: move; Space: interact with nearby characters and locations
- In the arena, A/D: rotate; W/S: move forward/backward; Q/E: capture diagonally left/right
- Each valid arena action takes one turn. One black piece moves after each player action.
- A/D rotates the pawn and also spends a turn.
- The arena highlights the predicted enemy destination on the board. A threatened capture is delayed for one player action so you can move off the highlighted square.
- You have two lives. If you stay on a threatened square through the response turn, the enemy captures that square and you respawn on the nearest safe square; losing both lives returns you to the main world.
- Enemy move selection is powered by the local `chess-engine/` Java module, with legal chess moves and an alpha-beta search.
- The predicted source and destination squares are highlighted before your move; the chosen engine move is also printed in the console and is executed after your action.

## RPG quest

1. Find the villager near the starting area and press Space.
2. Search the marked old grove with Space to find a soul fragment and gain XP.
3. Return to the villager and press Space to claim the quest reward.
4. Your level, XP, and soul fragments are shown in the HUD.

## Maps

Edit `tilemap.ldtk` in LDtk. The game starts in `Level_0`, which contains the player and the portal. RPG quest markers are placed in the forest hub. The portal leads to `Level_1`, where the 8×8 board, the player's pawn, and the black army are created when the arena loads.

Gradle copies `tilemap.ldtk` and `assets_raw/` into `assets/` before resource processing.

## Run

```sh
./gradlew lwjgl3:run
```
