import Foundation

@main
enum RewardedContinueSmoke {
    static func main() {
        func state(_ score: Int, lost: Bool = false) -> GameState {
            GameState(board: Array(repeating: 0, count: 16), score: score, won: false, lost: lost)
        }

        var reward = RewardedContinue()
        for score in 1...5 { reward.record(state(score)) }
        assert(reward.history.map(\.score) == [3, 4, 5], "History must retain only the last three moves")
        assert(reward.available, "Reward must be available before redemption")
        assert(reward.redeem(current: state(6)) == nil, "Cannot redeem while playing")
        let restored = reward.redeem(current: state(6, lost: true))
        assert(restored?.score == 3, "Must restore the oldest retained snapshot")
        assert(reward.used && !reward.available, "Reward must be one-time")
        assert(reward.redeem(current: state(7, lost: true)) == nil, "Second redemption must fail")
        reward.reset()
        assert(!reward.used && reward.history.isEmpty, "New game must reset reward state")
        print("RewardedContinue smoke tests passed")
    }
}
