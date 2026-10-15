import UIKit
import AudioToolbox

@MainActor
final class GameEffects {
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let success = UINotificationFeedbackGenerator()

    func play(scoreDelta: Int, won: Bool, sound: Bool = true, haptics: Bool = true) {
        if won {
            if haptics { success.notificationOccurred(.success) }
            if sound { AudioServicesPlaySystemSound(1057) }
        } else if scoreDelta >= 64 {
            if haptics { medium.impactOccurred() }
            if sound { AudioServicesPlaySystemSound(1104) }
        } else if scoreDelta > 0 {
            if haptics { light.impactOccurred() }
            if sound { AudioServicesPlaySystemSound(1104) }
        }
    }
}
