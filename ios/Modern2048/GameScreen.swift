import SwiftUI

private enum Palette {
    static let canvas = Color(red: 250/255, green: 248/255, blue: 245/255)
    static let ink = Color(red: 36/255, green: 36/255, blue: 58/255)
    static let board = Color(red: 233/255, green: 229/255, blue: 226/255)
    static let empty = Color(red: 245/255, green: 241/255, blue: 237/255)
    static let purple = Color(red: 102/255, green: 87/255, blue: 232/255)
}

struct GameScreen: View {
    @StateObject private var rewardedAds = RewardedAds()
    @State private var effects = GameEffects()
    @State private var rewardedContinue = GameStorage.loadReward()
    @State private var game = GameStorage.load() ?? GameEngine.newGame()
    @State private var bestScore = GameStorage.best
    @State private var previous: GameState?
    @State private var winAcknowledged = false
    @State private var confirmRestart = false
    @State private var slidingTiles: [TileMotion] = []
    @State private var slideProgress: CGFloat = 0
    @State private var slideInProgress = false
    @State private var revealedCells: Set<Int> = []
    @State private var mergedCells: Set<Int> = []
    @AppStorage("modern2048.sound") private var soundEnabled = true
    @AppStorage("modern2048.haptics") private var hapticsEnabled = true
    @State private var showSettings = false
    @AppStorage("modern2048.reducedMotion") private var reducedMotion = false
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion

    var body: some View {
        VStack(spacing: 14) {
            Text("2048")
                .font(.system(size: 48, weight: .black, design: .rounded))
                .foregroundStyle(Palette.purple)
            Text("Modern Puzzle").foregroundStyle(Palette.ink)
            HStack(spacing: 12) {
                scoreCard("SKOR", "\(game.score)")
                scoreCard("REKOR", "\(bestScore)")
            }
            GeometryReader { geometry in
                let gap: CGFloat = 10
                let side = (geometry.size.width - 20 - gap * 3) / 4
                let step = side + gap
                ZStack(alignment: .topLeading) {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: gap), count: 4), spacing: gap) {
                        ForEach(0..<16, id: \.self) { index in
                            let value = game.board[index]
                            ZStack {
                                RoundedRectangle(cornerRadius: 14)
                                    .fill(slideInProgress ? Palette.empty : (value == 0 ? Palette.empty : tileColor(value)))
                                if value != 0 && !slideInProgress {
                                    Text("\(value)")
                                        .font(.system(size: value >= 1024 ? 19 : 25, weight: .bold, design: .rounded))
                                        .foregroundStyle(Palette.ink)
                                }
                            }
                            .frame(height: side)
                            .scaleEffect((revealedCells.contains(index) || mergedCells.contains(index)) && !reducedMotion && !systemReduceMotion ? 1.12 : 1)
                            .animation((reducedMotion || systemReduceMotion) ? nil : .spring(response: 0.23, dampingFraction: 0.7), value: value)
                        }
                    }
                    if slideInProgress {
                        ForEach(Array(slidingTiles.enumerated()), id: \.offset) { _, tile in
                            ZStack {
                                RoundedRectangle(cornerRadius: 14).fill(tileColor(tile.value))
                                Text("\(tile.value)")
                                    .font(.system(size: tile.value >= 1024 ? 19 : 25, weight: .bold, design: .rounded))
                                    .foregroundStyle(Palette.ink)
                            }
                            .frame(width: side, height: side)
                            .offset(
                                x: CGFloat(tile.from % 4) * step + CGFloat(tile.to % 4 - tile.from % 4) * step * slideProgress,
                                y: CGFloat(tile.from / 4) * step + CGFloat(tile.to / 4 - tile.from / 4) * step * slideProgress
                            )
                        }
                    }
                }
                .padding(10)
                .background(Palette.board, in: RoundedRectangle(cornerRadius: 24))
                .gesture(DragGesture(minimumDistance: 20).onEnded { gesture in
                    guard !slideInProgress else { return }
                    let dx = gesture.translation.width
                    let dy = gesture.translation.height
                    let direction: MoveDirection = abs(dx) > abs(dy)
                        ? (dx > 0 ? .right : .left)
                        : (dy > 0 ? .down : .up)
                    let result = GameEngine.move(game, direction)
                    guard result.moved else { return }
                    let old = game
                    rewardedContinue.record(old)
                    GameStorage.saveReward(rewardedContinue)
                    previous = old
                    effects.play(scoreDelta: result.scoreDelta, won: !old.won && result.state.won, sound: soundEnabled, haptics: hapticsEnabled)
                    bestScore = max(bestScore, result.state.score)
                    GameStorage.save(result.state)
                    GameStorage.saveBest(bestScore)
                    if reducedMotion || systemReduceMotion {
                        game = result.state
                    } else {
                        slidingTiles = TileMotionPlanner.plan(old.board, direction)
                        slideProgress = 0
                        slideInProgress = true
                        withAnimation(.easeInOut(duration: 0.16)) { slideProgress = 1 }
                        Task { @MainActor in
                            try? await Task.sleep(nanoseconds: 180_000_000)
                            revealedCells = Set(result.state.board.indices.filter { index in result.state.board[index] != 0 && !slidingTiles.contains(where: { tile in tile.to == index }) })
                            mergedCells = Set(slidingTiles.filter { $0.merged }.map(\.to))
                            game = result.state
                            slideInProgress = false
                            slidingTiles = []
                            slideProgress = 0
                            try? await Task.sleep(nanoseconds: 180_000_000)
                            revealedCells = []
                            mergedCells = []
                        }
                    }
                })
            }
            .aspectRatio(1, contentMode: .fit)
            Text("Kaydırarak aynı sayıları birleştir").foregroundStyle(Palette.ink)
            HStack(spacing: 8) {
                Button("Geri Al") {
                    if let previous { game = previous; self.previous = nil; if !rewardedContinue.history.isEmpty { rewardedContinue.history.removeLast() }; GameStorage.saveReward(rewardedContinue); GameStorage.save(game) }
                }
                .disabled(previous == nil || slideInProgress)
                Button("Yeni Oyun") { confirmRestart = true }
                    .disabled(slideInProgress)
                Button("Ayarlar") { showSettings = true }
                    .disabled(slideInProgress)
            }
            .font(.system(size: 13, weight: .semibold))
            .buttonStyle(.borderedProminent)
            Spacer()
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Palette.canvas.ignoresSafeArea())
        .onAppear { rewardedAds.load() }
        .sheet(isPresented: $showSettings) {
            NavigationStack {
                Form {
                    Toggle("Ses efektleri", isOn: $soundEnabled)
                    Toggle("Titreşim", isOn: $hapticsEnabled)
                    Toggle("Animasyonları azalt", isOn: $reducedMotion)
                }
                .navigationTitle("Oyun Ayarları")
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Bitti") { showSettings = false } } }
            }
            .presentationDetents([.medium])
        }
        .alert("2048! Harika!", isPresented: Binding(
            get: { game.won && !winAcknowledged && !game.lost },
            set: { if !$0 { winAcknowledged = true } }
        )) {
            Button("Devam Et") { winAcknowledged = true }
            Button("Yeni Oyun") { restart() }
        } message: {
            Text("2048 taşına ulaştın. Daha yüksek sayılar için devam edebilirsin.")
        }
        .alert("Oyun Bitti", isPresented: Binding(
            get: { game.lost },
            set: { _ in }
        )) {
            if rewardedAds.isReady && rewardedContinue.available {
                Button("Reklam İzle · 3 Hamle Geri Al") {
                    let lostSnapshot = game
                    rewardedAds.show {
                        guard game == lostSnapshot else { return }
                        if let restored = rewardedContinue.redeem(current: game) {
                            game = restored
                            GameStorage.saveReward(rewardedContinue)
                            previous = nil
                            GameStorage.save(game)
                        }
                    }
                }
            }
            Button("Tekrar Oyna") { restart() }
            if previous != nil {
                Button("Geri Al") {
                    if let previous { game = previous; self.previous = nil; if !rewardedContinue.history.isEmpty { rewardedContinue.history.removeLast() }; GameStorage.saveReward(rewardedContinue); GameStorage.save(game) }
                }
            }
        } message: {
            Text("Skorun: \(game.score)")
        }
        .confirmationDialog("Yeni oyun başlatılsın mı?", isPresented: $confirmRestart) {
            Button("Yeni Oyun", role: .destructive) { restart() }
            Button("Vazgeç", role: .cancel) {}
        } message: {
            Text("Mevcut ilerlemen sıfırlanacak. Rekorun korunacak.")
        }
    }

    private func restart() {
        game = GameEngine.newGame()
        previous = nil
        rewardedContinue.reset()
        GameStorage.saveReward(rewardedContinue)
        winAcknowledged = false
        GameStorage.save(game)
    }

    private func tileColor(_ value: Int) -> Color {
        switch value {
        case 2: return Color(red: 1, green: 0.94, blue: 0.85)
        case 4: return Color(red: 1, green: 0.88, blue: 0.73)
        case 8: return Color(red: 1, green: 0.69, blue: 0.43)
        case 16: return Color(red: 1, green: 0.53, blue: 0.44)
        case 32: return Color(red: 1, green: 0.43, blue: 0.58)
        case 64: return Color(red: 0.85, green: 0.47, blue: 0.93)
        default: return Color(red: 0.4, green: 0.72, blue: 0.96)
        }
    }

    private func scoreCard(_ label: String, _ value: String) -> some View {
        VStack {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.system(size: 26, weight: .bold, design: .rounded))
                .foregroundStyle(Palette.ink)
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .background(.white, in: RoundedRectangle(cornerRadius: 20))
    }
}
