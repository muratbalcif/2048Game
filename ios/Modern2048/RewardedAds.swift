import Foundation
import Combine
import GoogleMobileAds
import UIKit
import SwiftUI

@MainActor
final class RewardedAds: NSObject, ObservableObject, FullScreenContentDelegate {
    @Published private(set) var isReady = false
    private var ad: RewardedAd?
    private var loading = false
    private var presenting = false
    private var earned = false
    private var presentationID = 0
    private var loadGeneration = 0

    func load() {
        #if DEBUG
        guard !loading, !presenting, ad == nil else { return }
        loading = true
        loadGeneration += 1
        let generation = loadGeneration
        Task {
            do {
                let loaded = try await RewardedAd.load(
                    with: "ca-app-pub-3940256099942544/1712485313",
                    request: Request()
                )
                guard generation == loadGeneration else { return }
                ad = loaded
                isReady = true
            } catch {
                guard generation == loadGeneration else { return }
                ad = nil
                isReady = false
            }
            loading = false
        }
        #endif
    }

    func show(onRewardEarned: @escaping () -> Void, sessionId: String? = nil, playerId: String? = nil) {
        guard let ready = ad, !presenting,
              let scene = UIApplication.shared.connectedScenes
                  .compactMap({ $0 as? UIWindowScene }).first,
              let root = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController else { return }
        if let sessionId, let playerId {
            let options = ServerSideVerificationOptions()
            options.customRewardString = sessionId
            options.userIdentifier = playerId
            ready.serverSideVerificationOptions = options
        }
        presenting = true
        presentationID += 1
        let currentPresentation = presentationID
        earned = false
        ready.fullScreenContentDelegate = self
        ad = nil
        isReady = false
        ready.present(from: root) { [weak self] in
            guard let self, self.presentationID == currentPresentation, !self.earned else { return }
            self.earned = true
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
