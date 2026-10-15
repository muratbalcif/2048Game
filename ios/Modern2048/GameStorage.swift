import Foundation

enum GameStorage {
    private static let gameKey = "modern2048.game"
    private static let bestKey = "modern2048.best"

    static func load() -> GameState? {
        guard let data = UserDefaults.standard.data(forKey: gameKey),
              let snapshot = try? JSONDecoder().decode(Snapshot.self, from: data),
              snapshot.board.count == 16,
              snapshot.board.allSatisfy({ $0 == 0 || ($0 > 0 && ($0 & ($0 - 1)) == 0) }),
              snapshot.score >= 0 else { return nil }
        return GameState(board: snapshot.board, score: snapshot.score,
                         won: snapshot.won, lost: snapshot.lost)
    }

    static func save(_ state: GameState) {
        let snapshot = Snapshot(board: state.board, score: state.score,
                                won: state.won, lost: state.lost)
        if let data = try? JSONEncoder().encode(snapshot) {
            UserDefaults.standard.set(data, forKey: gameKey)
        }
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
