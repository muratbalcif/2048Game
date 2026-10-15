import Foundation

enum GameStorage {
    static func loadProgress() -> GameProgress {
        guard let data = UserDefaults.standard.data(forKey: "modern2048.progress"),
              let progress = try? JSONDecoder().decode(GameProgress.self, from: data),
              progress.moves >= 0, progress.merges >= 0, progress.gamesStarted >= 1,
              progress.bestTile >= 0, progress.totalPoints >= 0 else { return GameProgress() }
        return progress
    }

    static func saveProgress(_ progress: GameProgress) {
        if let data = try? JSONEncoder().encode(progress) {
            UserDefaults.standard.set(data, forKey: "modern2048.progress")
        }
    }

    private static let gameKey = "modern2048.game"
    private static let bestKey = "modern2048.best"

    static func load() -> GameState? {
        guard let data = UserDefaults.standard.data(forKey: gameKey),
              let snapshot = try? JSONDecoder().decode(Snapshot.self, from: data),
              snapshot.board.count == 16,
              snapshot.board.allSatisfy({ $0 == 0 || ($0 >= 2 && ($0 & ($0 - 1)) == 0) }),
              snapshot.score >= 0 else { return nil }
        return GameState(board: snapshot.board, score: snapshot.score,
                         won: snapshot.won, lost: !GameEngine.canMove(snapshot.board))
    }

    static func save(_ state: GameState) {
        let snapshot = Snapshot(board: state.board, score: state.score,
                                won: state.won, lost: state.lost)
        if let data = try? JSONEncoder().encode(snapshot) {
            UserDefaults.standard.set(data, forKey: gameKey)
        }
    }

    static func loadReward() -> RewardedContinue {
        guard let data = UserDefaults.standard.data(forKey: "modern2048.reward"),
              let record = try? JSONDecoder().decode(RewardRecord.self, from: data),
              record.history.count <= 3,
              record.history.allSatisfy({ state in
                  state.board.count == 16 && state.score >= 0 &&
                  state.board.allSatisfy({ $0 == 0 || ($0 >= 2 && ($0 & ($0 - 1)) == 0) })
              }) else { return RewardedContinue() }
        return RewardedContinue(
            history: record.history.map { GameState(board: $0.board, score: $0.score, won: $0.won, lost: !GameEngine.canMove($0.board)) },
            used: record.used
        )
    }

    static func saveReward(_ reward: RewardedContinue) {
        let record = RewardRecord(
            history: reward.history.map { Snapshot(board: $0.board, score: $0.score, won: $0.won, lost: $0.lost) },
            used: reward.used
        )
        if let data = try? JSONEncoder().encode(record) {
            UserDefaults.standard.set(data, forKey: "modern2048.reward")
        }
    }

    private struct RewardRecord: Codable {
        let history: [Snapshot]
        let used: Bool
    }

    static var best: Int { UserDefaults.standard.integer(forKey: bestKey) }

    static func saveBest(_ score: Int) {
        UserDefaults.standard.set(max(best, score), forKey: bestKey)
    }

    private struct Snapshot: Codable {
        let board: [Int]
        let score: Int
        let won: Bool
        let lost: Bool
    }
}
