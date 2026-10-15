import Foundation

struct VerifiedSession {
    let sessionId: String
    let playerId: String
    let seed: UInt32
    var moves = ""
}
enum VerifiedEngine {
    private static func next(_ value: UInt32) -> UInt32 {
        var x = value
        x ^= x &<< 13
        x ^= x >> 17
        x ^= x &<< 5
        return x
    }
    private static func spawn(_ board: [Int], _ seed: UInt32) -> ([Int], UInt32) {
        let empty = board.indices.filter { board[$0] == 0 }
        guard !empty.isEmpty else { return (board, seed) }
        let indexSeed = next(seed)
        let valueSeed = next(indexSeed)
        var result = board
        result[empty[Int(indexSeed % UInt32(empty.count))]] = valueSeed % 10 == 0 ? 4 : 2
        return (result, valueSeed)
    }
    static func initial(_ seed: UInt32) -> GameState {
        let (first, s1) = spawn(Array(repeating: 0, count: 16), seed)
        let (second, _) = spawn(first, s1)
        return GameState(board: second)
    }
    static func replay(_ seed: UInt32, _ moves: String) -> GameState? {
        guard moves.count <= 20000 else { return nil }
        let (first, s1) = spawn(Array(repeating: 0, count: 16), seed)
        let (second, s2) = spawn(first, s1)
        var state = GameState(board: second)
        var random = s2
        for character in moves {
            let direction: MoveDirection
            switch character {
            case "U": direction = .up
            case "D": direction = .down
            case "L": direction = .left
            case "R": direction = .right
            default: return nil
            }
            let result = GameEngine.moveWithoutSpawn(state, direction)
            guard result.moved else { return nil }
            let (board, nextSeed) = spawn(result.state.board, random)
            random = nextSeed
            state = GameState(board: board, score: result.state.score,
                              won: result.state.won || board.contains(where: { $0 >= 2048 }),
                              lost: !GameEngine.canMove(board))
        }
        return state
    }
    static func letter(_ direction: MoveDirection) -> String {
        switch direction {
        case .up: return "U"
        case .down: return "D"
        case .left: return "L"
        case .right: return "R"
        }
    }
}
