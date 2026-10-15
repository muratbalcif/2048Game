# Game rules (v1)

- Grid: 4x4, initially two spawned tiles.
- Each move slides all tiles in one direction.
- Equal adjacent tiles merge once per move. Example: [2,2,2,2] -> [4,4,0,0] moving left.
- Score increases by the value of each newly merged tile.
- A random tile spawns only when the board changes: 2 with probability 0.9, 4 with probability 0.1.
- Win is reached when a tile becomes 2048. Player may choose to continue.
- Loss occurs when there are no empty cells and no equal orthogonally adjacent cells.
- Undo restores the prior board and score; v1 supports one-step undo.
- Persist in-progress game on each successful move.
- No new tile on a no-op swipe.

Test cases:
[2,2,4,4] left -> [4,8,0,0] (score +12)
[2,2,2,0] left -> [4,2,0,0] (score +4)
[4,4,4,4] right -> [0,0,8,8] (score +16)
[2,4,8,16] left -> unchanged, no spawn.
