# Setup

Prerequisites: macOS with Xcode, Android Studio, JDK 17, Android SDK.

## Android
Create an Empty Activity Compose project inside `android/` with package `com.modern2048.game`, Kotlin and Material 3. Commit generated Gradle wrapper and project files. Do not use an unverified Gradle/AGP version combination.

## iOS
Create an iOS App (SwiftUI, Swift) inside `ios/`, bundle identifier `com.modern2048.game`. Commit the generated Xcode project, assets and Swift sources.

The repository does not yet contain runnable IDE projects: Xcode/Gradle project generation and build verification are Phase 0 remaining tasks.

## Acceptance criteria
Both native apps launch; design tokens match; an empty board screen renders; unit-test runners work; CI builds on macOS and Linux.
