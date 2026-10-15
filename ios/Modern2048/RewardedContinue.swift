import Foundation

/// Redeem only after the rewarded-ad SDK invokes its earned-reward callback.
struct RewardedContinue {
    var history: [GameState] = []
    var used = false

    var available: Bool { !used && !history.isEmpty }

    mutating func record(_ previous: GameState) {
        history.append(previous)
        if history.count > 3 { history.removeFirst(history.count - 3) }
    }

    mutating func redeem(current: GameState) -> GameState? {
        guard current.lost && available else { return nil }
        used = true
        let restored = history[0]
        history.removeAll()
        return restored
    }

    mutating func reset() {
        history.removeAll()
        used = false
    }
}
