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
            .init(id: "first_merge", title: NSLocalizedString("İlk Birleşme", comment: "Achievement"), unlocked: progress.merges >= 1),
            .init(id: "merge_100", title: NSLocalizedString("Birleşme Ustası", comment: "Achievement"), unlocked: progress.merges >= 100),
            .init(id: "merge_500", title: NSLocalizedString("Kombinasyon Ustası", comment: "Achievement"), unlocked: progress.merges >= 500),
            .init(id: "merge_2000", title: NSLocalizedString("Efsane Birleştirici", comment: "Achievement"), unlocked: progress.merges >= 2000),
            .init(id: "tile_64", title: NSLocalizedString("İlk Adım", comment: "Achievement"), unlocked: progress.bestTile >= 64),
            .init(id: "tile_128", title: NSLocalizedString("128 Taşı", comment: "Achievement"), unlocked: progress.bestTile >= 128),
            .init(id: "tile_256", title: NSLocalizedString("256 Taşı", comment: "Achievement"), unlocked: progress.bestTile >= 256),
            .init(id: "tile_512", title: NSLocalizedString("512 Taşı", comment: "Achievement"), unlocked: progress.bestTile >= 512),
            .init(id: "tile_1024", title: NSLocalizedString("1024 Yolcusu", comment: "Achievement"), unlocked: progress.bestTile >= 1024),
            .init(id: "tile_2048", title: NSLocalizedString("2048 Ustası", comment: "Achievement"), unlocked: progress.bestTile >= 2048),
            .init(id: "tile_4096", title: NSLocalizedString("4096 Efsanesi", comment: "Achievement"), unlocked: progress.bestTile >= 4096),
            .init(id: "hundred_moves", title: NSLocalizedString("100 Hamle", comment: "Achievement"), unlocked: progress.moves >= 100),
            .init(id: "moves_500", title: NSLocalizedString("500 Hamle", comment: "Achievement"), unlocked: progress.moves >= 500),
            .init(id: "moves_1000", title: NSLocalizedString("1000 Hamle", comment: "Achievement"), unlocked: progress.moves >= 1000),
            .init(id: "moves_5000", title: NSLocalizedString("Sabır Ustası", comment: "Achievement"), unlocked: progress.moves >= 5000),
            .init(id: "five_games", title: NSLocalizedString("5 Oyun", comment: "Achievement"), unlocked: progress.gamesStarted >= 5),
            .init(id: "games_25", title: NSLocalizedString("Deneyimli Oyuncu", comment: "Achievement"), unlocked: progress.gamesStarted >= 25),
            .init(id: "games_100", title: NSLocalizedString("100 Oyun", comment: "Achievement"), unlocked: progress.gamesStarted >= 100),
            .init(id: "points_10000", title: NSLocalizedString("Puan Avcısı", comment: "Achievement"), unlocked: progress.totalPoints >= 10000),
            .init(id: "points_100000", title: NSLocalizedString("Puan Efsanesi", comment: "Achievement"), unlocked: progress.totalPoints >= 100000)
        ]
    }
}
