import UIKit
import AudioToolbox

@MainActor
final class GameEffects {
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let success = UINotificationFeedbackGenerator()

    func play(scoreDelta: Int, won: Bool) {
        guard !UIAccessibility.isReduceMotionEnabled else { return }
        if won {
            success.notificationOccurred(.success)
            AudioServicesPlaySystemSound(1057)
        } else if scoreDelta >= 64 {
            medium.impactOccurred()
            AudioServicesPlaySystemSound(1104)
        } else if scoreDelta > 0 {
            light.impactOccurred()
            AudioServicesPlaySystemSound(1104)
        }
    }
}
