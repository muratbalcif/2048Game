# Architecture

Android: Kotlin, Jetpack Compose, Material 3, ViewModel, DataStore.
iOS: Swift, SwiftUI, ObservableObject, UserDefaults.

Each platform owns a native implementation of the same deterministic rules contract. UI never performs merges itself. Engine API:
- newGame(random)
- move(direction, random) -> board, scoreDelta, moved, spawned, won, lost
- restore(snapshot)

Use injected seeded randomness in tests. Preserve stable tile identifiers for movement animation. Store board, score, bestScore, moveCount, won, keepPlaying, and undo snapshot atomically.

Modules:
- game/engine: pure domain model and algorithms
- game/presentation: state + intents
- game/ui: screens and animations
- core/design: colors, typography, dimensions
- core/storage: persistence adapter

No network or authentication in v1.
