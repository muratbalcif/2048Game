export type Direction = "U" | "D" | "L" | "R";
export type ReplayState = { board: number[]; score: number; lost: boolean; won: boolean };
export function nextRandom(seed: number): number {
  let x = seed >>> 0;
  x ^= (x << 13) >>> 0;
  x ^= x >>> 17;
  x ^= (x << 5) >>> 0;
  return x >>> 0;
}
export function spawn(board: number[], seed: number): { board: number[]; seed: number } {
  const empty = board.flatMap((value, index) => value === 0 ? [index] : []);
  if (!empty.length) return { board, seed };
  const indexSeed = nextRandom(seed);
  const valueSeed = nextRandom(indexSeed);
  const result = [...board];
  result[empty[indexSeed % empty.length]] = valueSeed % 10 === 0 ? 4 : 2;
  return { board: result, seed: valueSeed };
}
export function newGame(seed: number): { state: ReplayState; seed: number } {
  let board = Array(16).fill(0) as number[];
  let current = seed >>> 0;
  for (let i = 0; i < 2; i++) {
    const result = spawn(board, current);
    board = result.board;
    current = result.seed;
  }
  return { state: { board, score: 0, won: false, lost: false }, seed: current };
}
export function canMove(board: number[]): boolean {
  return board.some(v => v === 0) || board.some((v, i) =>
    (i % 4 < 3 && v === board[i + 1]) || (Math.floor(i / 4) < 3 && v === board[i + 4]));
}
export function move(state: ReplayState, direction: Direction, seed: number): { state: ReplayState; seed: number; moved: boolean } {
  if (state.lost) return { state, seed, moved: false };
  const board = [...state.board];
  let gained = 0;
  for (let line = 0; line < 4; line++) {
    const positions = Array.from({length: 4}, (_, offset) => {
      switch (direction) {
        case "L": return line * 4 + offset;
        case "R": return line * 4 + (3 - offset);
        case "U": return offset * 4 + line;
        case "D": return (3 - offset) * 4 + line;
      }
    });
    const values = positions.map(i => state.board[i]).filter(v => v !== 0);
    const merged: number[] = [];
    for (let i = 0; i < values.length; i++) {
      if (i + 1 < values.length && values[i] === values[i + 1]) {
        const combined = values[i] * 2;
        merged.push(combined);
        gained += combined;
        i++;
      } else merged.push(values[i]);
    }
    positions.forEach((position, index) => { board[position] = merged[index] ?? 0; });
  }
  if (board.every((v, i) => v === state.board[i])) return { state, seed, moved: false };
  const spawned = spawn(board, seed);
  const next = spawned.board;
  return {
    state: { board: next, score: state.score + gained, won: state.won || next.some(v => v >= 2048), lost: !canMove(next) },
    seed: spawned.seed,
    moved: true
  };
}
export function replay(seed: number, directions: string): { state: ReplayState; validMoves: number } | null {
  if (!/^[UDLR]{1,20000}$/.test(directions)) return null;
  let game = newGame(seed);
  let validMoves = 0;
  for (const direction of directions as string) {
    const result = move(game.state, direction as Direction, game.seed);
    if (!result.moved) return null;
    game = { state: result.state, seed: result.seed };
    validMoves++;
  }
  return { state: game.state, validMoves };
}
