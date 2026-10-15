import Foundation

enum MoveDirection { case up, down, left, right }

struct GameState: Equatable {
    var board: [Int] = Array(repeating: 0, count: 16)
    var score: Int = 0
    var won: Bool = false
    var lost: Bool = false
}

struct MoveResult {
    let state: GameState
    let moved: Bool
    let scoreDelta: Int
}

enum GameEngine {
    static func newGame() -> GameState {
        var board = Array(repeating: 0, count: 16)
        spawn(&board)
        spawn(&board)
        return GameState(board: board)
    }

    static func move(_ state: GameState, _ direction: MoveDirection) -> MoveResult { moveInternal(state, direction, spawnTile: true) }
    static func moveWithoutSpawn(_ state: GameState, _ direction: MoveDirection) -> MoveResult { moveInternal(state, direction, spawnTile: false) }
    private static func moveInternal(_ state: GameState, _ direction: MoveDirection, spawnTile: Bool) -> MoveResult {
        precondition(state.board.count == 16)
        guard !state.lost else { return MoveResult(state: state, moved: false, scoreDelta: 0) }
        var output = state.board
        var gained = 0
        for line in 0..<4 {
            let indices: [Int] = (0..<4).map { offset in
                switch direction {
                case .left: return line * 4 + offset
                case .right: return line * 4 + (3 - offset)
                case .up: return offset * 4 + line
                case .down: return (3 - offset) * 4 + line
                }
            }
            let values = indices.map { state.board[$0] }.filter { $0 != 0 }
            var merged: [Int] = []
            var index = 0
            while index < values.count {
                if index + 1 < values.count && values[index] == values[index + 1] {
                    let value = values[index] * 2
                    merged.append(value)
                    gained += value
                    index += 2
                } else {
                    merged.append(values[index])
                    index += 1
                }
            }
            for (offset, position) in indices.enumerated() {
                output[position] = offset < merged.count ? merged[offset] : 0
            }
        }
        guard output != state.board else { return MoveResult(state: state, moved: false, scoreDelta: 0) }
        if spawnTile { spawn(&output) }
        let next = GameState(board: output, score: state.score + gained,
                             won: state.won || output.contains(where: { $0 >= 2048 }),
                             lost: !canMove(output))
        return MoveResult(state: next, moved: true, scoreDelta: gained)
    }

    static func canMove(_ board: [Int]) -> Bool {
        precondition(board.count == 16)
        if board.contains(0) { return true }
        return (0..<16).contains { index in
            (index % 4 < 3 && board[index] == board[index + 1]) ||
            (index / 4 < 3 && board[index] == board[index + 4])
        }
    }

    private static func spawn(_ board: inout [Int]) {
        let empty = board.indices.filter { board[$0] == 0 }
        guard let index = empty.randomElement() else { return }
        board[index] = Double.random(in: 0..<1) < 0.9 ? 2 : 4
    }
}
