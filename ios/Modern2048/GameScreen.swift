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
    @State private var progress = GameStorage.loadProgress()
    @State private var showProgress = false
    @AppStorage("modern2048.mode") private var modeRaw = GameMode.classic.rawValue
    @State private var showModePicker = false
    private var mode: GameMode { GameMode(rawValue: modeRaw) ?? .classic }
    @State private var previous: GameState?
    @State private var winAcknowledged = false
    @State private var confirmRestart = false
    @State private var slidingTiles: [TileMotion] = []
    @State private var slideProgress: CGFloat = 0
    @State private var slideInProgress = false
    @State private var animationToken = UUID()
    @State private var revealedCells: Set<Int> = []
    @State private var mergedCells: Set<Int> = []
    @AppStorage("modern2048.highContrast") private var highContrast = false
    @AppStorage("modern2048.largeNumbers") private var largeNumbers = false
    @AppStorage("modern2048.directionalControls") private var directionalControls = false
    @AppStorage("modern2048.theme") private var themeRaw = GameTheme.sunset.rawValue
    private var theme: GameTheme { GameTheme(rawValue: themeRaw) ?? .sunset }
    @AppStorage("modern2048.sound") private var soundEnabled = true
    @AppStorage("modern2048.haptics") private var hapticsEnabled = true
    @State private var showSettings = false
    @AppStorage("modern2048.reducedMotion") private var reducedMotion = false
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ScrollView {
        VStack(spacing: 12) {
            Text("2048")
                .font(.system(size: 48, weight: .black, design: .rounded))
                .foregroundStyle(theme.accent)
            Button(mode.title + "  ▾") { showModePicker = true }
                .disabled(slideInProgress)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(theme.ink)
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
                                    .fill(slideInProgress ? theme.empty : (value == 0 ? theme.empty : (highContrast ? contrastTile(value) : theme.tile(value))))
                                if value != 0 && !slideInProgress {
                                    Text("\(value)")
                                        .font(.system(size: value >= 1024 ? (largeNumbers ? 22 : 19) : (largeNumbers ? 30 : 25), weight: .bold, design: .rounded))
                                         .foregroundStyle(highContrast ? Color.black : (theme == .midnight ? Color.black : theme.ink))
                                }
                            }
                            .frame(height: side)
                            .accessibilityLabel(value == 0 ? "Boş hücre" : String(value) + " taşı")
                            .scaleEffect((revealedCells.contains(index) || mergedCells.contains(index)) && !reducedMotion && !systemReduceMotion ? 1.12 : 1)
                            .animation((reducedMotion || systemReduceMotion) ? nil : .spring(response: 0.23, dampingFraction: 0.7), value: value)
                        }
                    }
                    if slideInProgress {
                        ForEach(Array(slidingTiles.enumerated()), id: \.offset) { _, tile in
                            ZStack {
                                RoundedRectangle(cornerRadius: 14).fill((highContrast ? contrastTile(tile.value) : theme.tile(tile.value)))
                                Text("\(tile.value)")
                                    .font(.system(size: tile.value >= 1024 ? (largeNumbers ? 22 : 19) : (largeNumbers ? 30 : 25), weight: .bold, design: .rounded))
                                    .foregroundStyle(highContrast ? Color.black : (theme == .midnight ? Color.black : theme.ink))
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
                .background(theme.board, in: RoundedRectangle(cornerRadius: 24))
                .gesture(DragGesture(minimumDistance: 20).onEnded { gesture in
                    guard !slideInProgress && !game.lost else { return }
                    let dx = gesture.translation.width
                    let dy = gesture.translation.height
                    let direction: MoveDirection = abs(dx) > abs(dy)
                        ? (dx > 0 ? .right : .left)
                        : (dy > 0 ? .down : .up)
                    performMove(direction)
                })
            }
            .aspectRatio(1, contentMode: .fit)
            if directionalControls {
                VStack(spacing: 2) {
                    Button("↑ Yukarı") { performMove(.up) }
                    HStack(spacing: 16) {
                        Button("← Sol") { performMove(.left) }
                        Button("↓ Aşağı") { performMove(.down) }
                        Button("Sağ →") { performMove(.right) }
                    }
                }
                .buttonStyle(.bordered)
                .disabled(slideInProgress)
            }
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
            Button("İstatistikler ve Başarımlar") { showProgress = true }
                .disabled(slideInProgress)
                .font(.footnote)
        }
        .padding(16)
        .frame(maxWidth: 560)
        .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.hidden)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.canvas.ignoresSafeArea())
        .onAppear { rewardedAds.load() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background || phase == .inactive {
                if !slideInProgress { GameStorage.save(game) }
                GameStorage.saveReward(rewardedContinue)
                GameStorage.saveProgress(progress)
            }
        }
        .confirmationDialog("Oyun Modu Seç", isPresented: $showModePicker) {
            ForEach(GameMode.allCases) { candidate in
                Button(candidate.title) {
                    guard candidate != mode else { return }
                    modeRaw = candidate.rawValue
                    restart()
                }
            }
            Button("Vazgeç", role: .cancel) {}
        } message: { Text("Mod değiştirildiğinde yeni oyun başlar.") }
        .sheet(isPresented: $showProgress) {
            NavigationStack {
                List {
                    Section("İstatistikler") {
                        LabeledContent("Hamle", value: "\(progress.moves)")
                        LabeledContent("Birleşme", value: "\(progress.merges)")
                        LabeledContent("Oyun", value: "\(progress.gamesStarted)")
                        LabeledContent("En büyük taş", value: "\(progress.bestTile)")
                        LabeledContent("Toplam puan", value: "\(progress.totalPoints)")
                    }
                    Section("Başarımlar") {
                        ForEach(GameAchievements.list(progress)) { achievement in
                            HStack {
                                Image(systemName: achievement.unlocked ? "checkmark.seal.fill" : "lock")
                                    .foregroundStyle(achievement.unlocked ? .green : .secondary)
                                Text(achievement.title)
                            }
                        }
                    }
                }
                .navigationTitle("İstatistikler")
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Bitti") { showProgress = false } } }
            }
        }
        .sheet(isPresented: $showSettings) {
            NavigationStack {
                Form {
                    Section("Görünüm") {
                        Picker("Tema", selection: $themeRaw) {
                            ForEach(GameTheme.allCases) { option in
                                Text(option.title).tag(option.rawValue)
                            }
                        }
                        .pickerStyle(.segmented)
                    }
                    Section("Erişilebilirlik") {
                        Toggle("Yüksek kontrast", isOn: $highContrast)
                        Toggle("Büyük taş sayıları", isOn: $largeNumbers)
                        Toggle("Yön tuşlarını göster", isOn: $directionalControls)
                    }
                    Section("Oyun") {
                    Toggle("Ses efektleri", isOn: $soundEnabled)
                    Toggle("Titreşim", isOn: $hapticsEnabled)
                    Toggle("Animasyonları azalt", isOn: $reducedMotion)
                    }
                }
                .navigationTitle("Oyun Ayarları")
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Bitti") { showSettings = false } } }
            }
            .presentationDetents([.medium])
        }
        .alert("Hedefe Ulaştın!", isPresented: Binding(
            get: { mode.reached(game.board) && !winAcknowledged && !game.lost },
            set: { if !$0 { winAcknowledged = true } }
        )) {
            Button("Devam Et") { winAcknowledged = true }
            Button("Yeni Oyun") { restart() }
        } message: {
            Text("\(mode.target ?? 2048) taşına ulaştın. Daha yüksek sayılar için devam edebilirsin.")
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

    private func performMove(_ direction: MoveDirection) {
        guard !slideInProgress && !game.lost else { return }
        let result = GameEngine.move(game, direction)
        guard result.moved else { return }
        let old = game
        rewardedContinue.record(old)
        GameStorage.saveReward(rewardedContinue)
        previous = old
        let mergedCount = TileMotionPlanner.plan(old.board, direction).filter(\.merged).count / 2
        progress.recordMove(after: result.state, scoreDelta: result.scoreDelta, mergedCount: mergedCount)
        GameStorage.saveProgress(progress)
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
            let token = UUID()
            animationToken = token
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 180_000_000)
                guard animationToken == token else { return }
                revealedCells = Set(result.state.board.indices.filter { index in result.state.board[index] != 0 && !slidingTiles.contains(where: { tile in tile.to == index }) })
                mergedCells = Set(slidingTiles.filter { $0.merged }.map(\.to))
                game = result.state
                slideInProgress = false
                slidingTiles = []
                slideProgress = 0
                try? await Task.sleep(nanoseconds: 180_000_000)
                guard animationToken == token else { return }
                revealedCells = []
                mergedCells = []
            }
        }
    }

    private func contrastTile(_ value: Int) -> Color {
        if value == 0 { return theme == .midnight ? Color.black : Color.white }
        return value >= 128 ? Color(red: 0.98, green: 0.77, blue: 0.13) : Color(red: 0.16, green: 0.72, blue: 0.88)
    }

    private func restart() {
        progress.nextGame()
        GameStorage.saveProgress(progress)
        animationToken = UUID()
        slideInProgress = false
        slidingTiles = []
        revealedCells = []
        mergedCells = []
        slideProgress = 0
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
                .foregroundStyle(theme.ink)
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .background(theme == .midnight ? theme.board : .white, in: RoundedRectangle(cornerRadius: 20))
    }
}
