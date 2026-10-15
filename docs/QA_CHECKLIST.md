# QA checklist — 2048 Modern Puzzle

## Build prerequisites
- [ ] Android: generate and commit Gradle wrapper; run `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
- [ ] iOS: run `xcodegen generate` inside `ios/`; run `xcodebuild -project Modern2048.xcodeproj -scheme Modern2048 -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build`.
- [ ] Confirm both apps launch on simulator/emulator.

## Game behavior
- [ ] Two tiles spawn at start.
- [ ] Every direction slides and merges correctly.
- [ ] No-op swipe does not spawn.
- [ ] [2,2,2,2] left produces [4,4,0,0] before spawn.
- [ ] Undo restores board and score.
- [ ] Best score survives new game and relaunch.
- [ ] Board survives process death.
- [ ] Win alert can be dismissed by continuing.
- [ ] Loss dialog allows restart or undo.
- [ ] New-game confirmation cancellation preserves state.
- [ ] Small screens, landscape, dark mode, accessibility font sizes.

## Known limitations
- Neither platform has been compiled in CI yet.
- Android Gradle wrapper is missing.
- iOS Xcode project must be generated from project.yml.
- Undo snapshot is in memory only and does not survive relaunch.
- Android and iOS storage currently use SharedPreferences/UserDefaults.
- Tile motion animation, audio, haptics and accessibility labels are not finished.
- Swift game engine tests and complete Android tests remain pending.
- Win acknowledgement is not persisted across relaunch.
