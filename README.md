# Pawn World

A small LibGDX prototype with an empty grassy hub and a chessboard arena. Walk through the glowing portal to enter an 8×8 board. You control the only white piece, a pawn on e2, against the complete black army. Capture the black king to win; any black piece can capture you.

## Controls

- In the hub, WASD or arrow keys: move
- In the arena, A/D: rotate; W/S: move forward/backward; Q/E: capture diagonally left/right
- Each valid arena action takes one turn. One black piece moves after each player action.
- A/D rotates the pawn and also spends a turn.
- The arena highlights the predicted enemy destination on the board. A threatened capture is delayed for one player action so you can move off the highlighted square.
- You have two lives. If you stay on a threatened square through the response turn, the enemy captures that square and you respawn on the nearest safe square; losing both lives returns you to the main world.
- Enemy move selection is powered by the local `chess-engine/` Java module, with legal chess moves and an alpha-beta search.
- The predicted source and destination squares are highlighted before your move; the chosen engine move is also printed in the console and is executed after your action.

## Maps

Edit `tilemap.ldtk` in LDtk. The game starts in `Level_0`, which contains the player and the portal. The portal leads to `Level_1`, where the 8×8 board, the player's pawn, and the black army are created when the arena loads.

Gradle copies `tilemap.ldtk` and `assets_raw/` into `assets/` before resource processing.

## Run

```sh
./gradlew lwjgl3:run
```
