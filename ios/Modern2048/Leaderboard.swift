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
    @Published var nickname: String = UserDefaults.standard.string(forKey: "modern2048.leaderboard.nickname") ?? ""
    func saveNickname(_ value: String) {
        nickname = value.trimmingCharacters(in: .whitespacesAndNewlines)
        UserDefaults.standard.set(nickname, forKey: "modern2048.leaderboard.nickname")
    }
    func updateNickname(_ value: String) async -> Bool {
        let name = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard name.range(of: #"^[\p{L}\p{N} _.-]{2,20}$"#, options: .regularExpression) != nil,
              let base,
              let sessionId = UserDefaults.standard.string(forKey: "modern2048.leaderboard.proofSessionId") ?? VerifiedSession.load()?.sessionId else {
            error = "Rumuzu güncellemek için önce sıralamalı bir oyun başlatmalısın."
            return false
        }
        do {
            var request = URLRequest(url: base.appendingPathComponent("v2/players/nickname"))
            request.httpMethod = "PATCH"
            request.timeoutInterval = 10
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: ["playerId": playerId, "sessionId": sessionId, "nickname": name])
            let (data, response) = try await URLSession.shared.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200,
                  (try JSONSerialization.jsonObject(with: data) as? [String: Any])?["ok"] as? Bool == true else {
                error = "Rumuz sunucuda güncellenemedi. Tekrar dene."
                return false
            }
            saveNickname(name)
            error = nil
            return true
        } catch {
            error = "Rumuz güncellenemedi. İnternet bağlantını kontrol et."
            return false
        }
    }
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

    func startVerified(mode: GameMode) async -> VerifiedSession? {
        guard let base else { error = "Global sıralama henüz etkin değil"; return nil }
        do {
            var request = URLRequest(url: base.appendingPathComponent("v2/sessions"))
            request.httpMethod = "POST"
            request.timeoutInterval = 7
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: [
                "playerId": playerId, "nickname": nickname.isEmpty ? "Oyuncu-\(playerId.prefix(6))" : nickname, "mode": mode.rawValue
            ])
            let (data, response) = try await URLSession.shared.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 201 else { return nil }
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            guard let id = json?["sessionId"] as? String, let seed = json?["seed"] as? NSNumber else { return nil }
            UserDefaults.standard.set(id, forKey: "modern2048.leaderboard.proofSessionId")
            return VerifiedSession(sessionId: id, playerId: playerId, seed: seed.uint32Value)
        } catch { self.error = "Sıralamalı oyun başlatılamadı"; return nil }
    }

    func finalizeVerified(_ session: VerifiedSession, score: Int) async -> Bool {
        guard let base, !session.moves.isEmpty else { return false }
        do {
            var request = URLRequest(url: base.appendingPathComponent("v2/sessions/finalize"))
            request.httpMethod = "POST"
            request.timeoutInterval = 10
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: [
                "sessionId": session.sessionId, "playerId": session.playerId,
                "moves": session.moves, "score": score
            ])
            let (_, response) = try await URLSession.shared.data(for: request)
            return (response as? HTTPURLResponse)?.statusCode == 200
        } catch { return false }
    }

    /// Read-only status; local SDK reward callbacks are not proof of SSV verification.
    func isRewardVerified(_ session: VerifiedSession) async -> Bool {
        guard let base else { return false }
        var components = URLComponents(url: base.appendingPathComponent("v2/sessions/reward-status"), resolvingAgainstBaseURL: false)
        components?.queryItems = [
            URLQueryItem(name: "sessionId", value: session.sessionId),
            URLQueryItem(name: "playerId", value: session.playerId)
        ]
        guard let url = components?.url else { return false }
        do {
            var request = URLRequest(url: url)
            request.timeoutInterval = 7
            let (data, response) = try await URLSession.shared.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200,
                  let payload = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { return false }
            return payload["credited"] as? Bool == true
        } catch { return false }
    }

    func load(mode: GameMode, finalScore: Int? = nil) async {
        guard let base else {
            error = "Global sıralama henüz etkin değil"
            return
        }
        isLoading = true
        defer { isLoading = false }
        do {
            // Offline scores are not verified. Submit only via a server-seeded replay session.
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
