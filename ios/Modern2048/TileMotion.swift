import Foundation

struct TileMotion: Equatable {
    let from: Int
    let to: Int
    let value: Int
    let merged: Bool
}

enum TileMotionPlanner {
    static func plan(_ board: [Int], _ direction: MoveDirection) -> [TileMotion] {
        precondition(board.count == 16)
        var motions: [TileMotion] = []
        for line in 0..<4 {
            let indices: [Int] = (0..<4).map { offset in
                switch direction {
                case .left: return line * 4 + offset
                case .right: return line * 4 + (3 - offset)
                case .up: return offset * 4 + line
                case .down: return (3 - offset) * 4 + line
                }
            }
            let occupied = indices.filter { board[$0] != 0 }
            var source = 0
            var target = 0
            while source < occupied.count {
                let first = occupied[source]
                let merge = source + 1 < occupied.count && board[first] == board[occupied[source + 1]]
                let destination = indices[target]
                motions.append(TileMotion(from: first, to: destination, value: board[first], merged: merge))
                if merge {
                    let second = occupied[source + 1]
                    motions.append(TileMotion(from: second, to: destination, value: board[second], merged: true))
                }
                source += merge ? 2 : 1
                target += 1
            }
        }
        return motions
    }
}
