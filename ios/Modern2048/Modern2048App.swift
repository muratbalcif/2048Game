import SwiftUI

@main
struct Modern2048App: App {
    @AppStorage("modern2048.language") private var selectedLanguage = "system"

    var body: some Scene {
        WindowGroup {
            GameScreen()
                .environment(\.locale, selectedLanguage == "system"
                    ? Locale.autoupdatingCurrent
                    : Locale(identifier: selectedLanguage))
        }
    }
}
