import Foundation
import Combine
import GoogleMobileAds
import UIKit

@MainActor
final class RewardedAds: NSObject, ObservableObject, FullScreenContentDelegate {
    @Published private(set) var isReady = false
    private var ad: RewardedAd?
    private var loading = false
    private var presenting = false

    func load() {
        guard !loading, !presenting, ad == nil else { return }
        loading = true
        Task {
            do {
                let loaded = try await RewardedAd.load(
                    with: "ca-app-pub-3940256099942544/1712485313",
                    request: Request()
                )
                ad = loaded
                isReady = true
            } catch {
                ad = nil
                isReady = false
            }
            loading = false
        }
    }

    func show(onRewardEarned: @escaping () -> Void) {
        guard let ready = ad, !presenting,
              let scene = UIApplication.shared.connectedScenes
                  .compactMap({ $0 as? UIWindowScene }).first,
              let root = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController else { return }
        presenting = true
        ready.fullScreenContentDelegate = self
        ad = nil
        isReady = false
        ready.present(from: root) { [weak self] in
            guard let self else { return }
            onRewardEarned()

        }
        // No reward is granted on dismissal; only this SDK reward callback.
    }

    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        presenting = false
        load()
    }

    func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        presenting = false
        load()
    }
}
