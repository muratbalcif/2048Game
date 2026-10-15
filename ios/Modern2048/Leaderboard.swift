import Combine
import Foundation

struct LeaderboardEntry: Decodable, Identifiable {
    let playerId: String
    let nickname: String
    let score: Int
    let rank: Int
    var id: String { playerId }
}
struct LeaderboardMe: Decodable {
    let rank: Int
    let score: Int
}
struct LeaderboardResponse: Decodable {
    let entries: [LeaderboardEntry]
    let me: LeaderboardMe?
}
@MainActor
final class LeaderboardClient: ObservableObject {
    @Published var entries: [LeaderboardEntry] = []
    @Published var myRank: Int?
    @Published var isLoading = false
    @Published var error: String?
    private let idKey = "modern2048.leaderboard.playerId"
    private var playerId: String {
        if let id = UserDefaults.standard.string(forKey: idKey) { return id }
        let id = UUID().uuidString.lowercased()
        UserDefaults.standard.set(id, forKey: idKey)
        return id
    }
    private var base: URL? {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "LeaderboardBaseURL") as? String,
              let url = URL(string: raw), url.scheme == "https", url.host != nil else { return nil }
        return url
    }

    func load(mode: GameMode, finalScore: Int? = nil) async {
        guard let base else {
            error = "Global sıralama henüz etkin değil"
            return
        }
        isLoading = true
        defer { isLoading = false }
        do {
            let key = "modern2048.leaderboard.submitted.\(mode.rawValue)"
            if let finalScore, finalScore > UserDefaults.standard.integer(forKey: key) {
                var request = URLRequest(url: base.appendingPathComponent("v1/scores"))
                request.httpMethod = "POST"
                request.setValue("application/json", forHTTPHeaderField: "Content-Type")
                request.timeoutInterval = 7
                request.httpBody = try JSONSerialization.data(withJSONObject: [
                    "playerId": playerId,
                    "nickname": "Oyuncu-\(playerId.prefix(6))",
                    "mode": mode.rawValue,
                    "score": finalScore
                ])
                let (_, response) = try await URLSession.shared.data(for: request)
                if let response = response as? HTTPURLResponse, (200...299).contains(response.statusCode) {
                    UserDefaults.standard.set(finalScore, forKey: key)
                }
            }
            var components = URLComponents(url: base.appendingPathComponent("v1/leaderboard"), resolvingAgainstBaseURL: false)!
            components.queryItems = [
                URLQueryItem(name: "mode", value: mode.rawValue),
                URLQueryItem(name: "playerId", value: playerId)
            ]
            var request = URLRequest(url: components.url!)
            request.timeoutInterval = 7
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { throw URLError(.badServerResponse) }
            let result = try JSONDecoder().decode(LeaderboardResponse.self, from: data)
            entries = Array(result.entries.prefix(5))
            myRank = result.me?.rank
            error = nil
        } catch {
            self.error = "Sıralama yüklenemedi. İnternet bağlantını kontrol et."
        }
    }

    func isMe(_ entry: LeaderboardEntry) -> Bool { entry.playerId.caseInsensitiveCompare(playerId) == .orderedSame }
}
