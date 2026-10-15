import { describe, it } from "node:test";
import { strict as assert } from "node:assert";
import { canMove, move, newGame, replay, spawn } from "./replay";

describe("deterministic verified replay", () => {
  it("produces identical initial boards for the same seed", () => {
    assert.deepEqual(newGame(123456), newGame(123456));
    assert.equal(newGame(123456).state.board.filter(Boolean).length, 2);
  });
  it("replays a valid move exactly", () => {
    const seed = 17;
    const game = newGame(seed);
    const directions = ["L", "R", "U", "D"] as const;
    const direction = directions.find(d => move(game.state, d, game.seed).moved);
    assert.ok(direction);
    const result = move(game.state, direction, game.seed);
    assert.deepEqual(replay(seed, direction)?.state, result.state);
  });
  it("rejects invalid moves and directions", () => {
    assert.equal(replay(1, ""), null);
    assert.equal(replay(1, "X"), null);
    const game = newGame(1);
    assert.equal(canMove(game.state.board), true);
  });
  it("is deterministic across repeat calls", () => {
    const a = spawn(Array(16).fill(0), 123);
    const b = spawn(Array(16).fill(0), 123);
    assert.deepEqual(a, b);
  });
});
