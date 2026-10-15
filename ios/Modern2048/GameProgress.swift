import Foundation

struct GameProgress: Codable, Equatable {
    var moves = 0
    var merges = 0
    var gamesStarted = 1
    var bestTile = 0
    var totalPoints = 0

    mutating func recordMove(after: GameState, scoreDelta: Int, mergedCount: Int) {
        moves += 1
        merges += mergedCount
        bestTile = max(bestTile, after.board.max() ?? 0)
        totalPoints += max(0, scoreDelta)
    }

    mutating func nextGame() { gamesStarted += 1 }
}

struct GameAchievement: Identifiable {
    let id: String
    let title: String
    let unlocked: Bool
}

enum GameAchievements {
    static func list(_ progress: GameProgress) -> [GameAchievement] {
        [
            .init(id: "first_merge", title: "İlk Birleşme", unlocked: progress.merges >= 1),
            .init(id: "hundred_moves", title: "100 Hamle", unlocked: progress.moves >= 100),
            .init(id: "tile_128", title: "128 Taşı", unlocked: progress.bestTile >= 128),
            .init(id: "tile_512", title: "512 Taşı", unlocked: progress.bestTile >= 512),
            .init(id: "tile_2048", title: "2048 Ustası", unlocked: progress.bestTile >= 2048),
            .init(id: "five_games", title: "5 Oyun", unlocked: progress.gamesStarted >= 5)
        ]
    }
}
