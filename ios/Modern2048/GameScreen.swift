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
    @StateObject private var leaderboard = LeaderboardClient()
    @State private var verifiedSession: VerifiedSession? = VerifiedSession.load()
    @State private var startingVerified = false
    @State private var effects = GameEffects()
    @State private var rewardedContinue = GameStorage.loadReward()
    @State private var game = GameStorage.load() ?? GameEngine.newGame()
    @State private var bestScore = GameStorage.best
    @AppStorage("modern2048.doubleScoreGame") private var doubledGameKey = ""
    @State private var progress = GameStorage.loadProgress()
    @State private var showProgress = false
    @State private var showLeaderboard = false
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
                .simultaneousGesture(DragGesture(minimumDistance: 20).onEnded { gesture in
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
            Text("Kaydırarak aynı sayıları birleştir").foregroundStyle(theme.ink)
            HStack(spacing: 8) {
                Button("Geri Al") {
                    if verifiedSession == nil, let previous { game = previous; self.previous = nil; if !rewardedContinue.history.isEmpty { rewardedContinue.history.removeLast() }; GameStorage.saveReward(rewardedContinue); GameStorage.save(game) }
                }
                .disabled(previous == nil || slideInProgress || verifiedSession != nil)
                Button("Yeni Oyun") { confirmRestart = true }
                Button("Ayarlar") { showSettings = true }
                    .disabled(slideInProgress)
            }
            .font(.system(size: 13, weight: .semibold))
            .buttonStyle(.borderedProminent)
            HStack(spacing: 14) {
                Button("🏆 Sıralama") { showLeaderboard = true }
                Button("İstatistikler ve Başarımlar") { showProgress = true }
            }
                .disabled(slideInProgress)
                .font(.footnote)
        }
        .padding(16)
        .padding(.bottom, 32)
        .frame(maxWidth: 560)
        .frame(maxWidth: .infinity)
        }
        .safeAreaInset(edge: .bottom) { Color.clear.frame(height: 12) }
        .scrollIndicators(.hidden)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.canvas.ignoresSafeArea())
        .onAppear {
            rewardedAds.load()
            if verifiedSession == nil && !game.lost { startRanked() }
            if let session = verifiedSession {
                guard let replayed = VerifiedEngine.replay(session.seed, session.moves),
                      replayed.board == game.board && replayed.score == game.score else {
                    verifiedSession = nil
                    VerifiedSession.save(nil)
                    return
                }
            }
        }
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
                    verifiedSession = nil
                    VerifiedSession.save(nil)
                    modeRaw = candidate.rawValue
                    restart()
                }
            }
            Button("Vazgeç", role: .cancel) {}
        } message: { Text("Mod değiştirildiğinde yeni oyun başlar.") }
        .sheet(isPresented: $showProgress) {
            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        Text("Her hamle gelişiminin bir parçası")
                            .font(.subheadline).foregroundStyle(theme.ink.opacity(0.65))
                        HStack(spacing: 12) {
                            scoreCard("TOPLAM PUAN", progress.totalPoints.formatted())
                            scoreCard("EN BÜYÜK TAŞ", "\(progress.bestTile)")
                        }
                        HStack(spacing: 12) {
                            scoreCard("OYUN", "\(progress.gamesStarted)")
                            scoreCard("HAMLE", "\(progress.moves)")
                        }
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Oyun performansı").font(.headline)
                            Label("Toplam birleşme: \(progress.merges)", systemImage: "square.on.square")
                            Label("Oyun başına puan: \(progress.totalPoints / max(1, progress.gamesStarted))", systemImage: "chart.bar.fill")
                            Label("Hamle başına birleşme: %\(progress.moves == 0 ? 0 : progress.merges * 100 / progress.moves)", systemImage: "arrow.triangle.merge")
                        }
                        .font(.subheadline).foregroundStyle(theme.ink)
                        .padding(18)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(theme.board.opacity(0.75), in: RoundedRectangle(cornerRadius: 20))
                        Text("Başarımlar").font(.title3.bold()).foregroundStyle(theme.ink)
                        ForEach(GameAchievements.list(progress)) { achievement in
                            HStack(spacing: 12) {
                                Image(systemName: achievement.unlocked ? "checkmark.seal.fill" : "lock")
                                    .foregroundStyle(theme.accent)
                                Text(achievement.title).fontWeight(.semibold).foregroundStyle(theme.ink)
                                Spacer()
                            }
                            .padding(16)
                            .background(achievement.unlocked ? theme.accent.opacity(0.12) : theme.board.opacity(0.65),
                                        in: RoundedRectangle(cornerRadius: 16))
                        }
                    }
                    .padding(20)
                }
                .background(theme.canvas.ignoresSafeArea())
                .navigationTitle("İstatistikler")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Bitti") { showProgress = false } } }
            }
            .presentationDetents([.large])
        }
        .sheet(isPresented: $showLeaderboard) {
            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Dünyanın dört bir yanından oyuncular")
                            .font(.subheadline).foregroundStyle(theme.ink.opacity(0.65))
                        HStack {
                            Text(mode.title).font(.headline).foregroundStyle(theme.accent)
                            Spacer()
                            Button("Yenile", systemImage: "arrow.clockwise") {
                                Task { await leaderboard.load(mode: mode) }
                            }.font(.subheadline)
                        }
                        if leaderboard.isLoading { ProgressView().frame(maxWidth: .infinity) }
                        if let rank = leaderboard.myRank {
                            Text("Senin sıralaman  #\(rank)")
                                .font(.title3.bold()).foregroundStyle(theme.accent)
                                .frame(maxWidth: .infinity, alignment: .leading).padding(18)
                                .background(theme.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 18))
                        }
                        if let error = leaderboard.error {
                            Text(error).font(.subheadline).foregroundStyle(theme.ink.opacity(0.7))
                        }
                        if !leaderboard.isLoading && leaderboard.entries.isEmpty && leaderboard.error == nil {
                            Text("Henüz doğrulanmış skor yok. İlk sırayı sen al!")
                                .foregroundStyle(theme.ink)
                        }
                        ForEach(leaderboard.entries) { entry in
                            HStack(spacing: 12) {
                                Text(entry.rank == 1 ? "🥇" : entry.rank == 2 ? "🥈" : entry.rank == 3 ? "🥉" : "#\(entry.rank)")
                                    .font(.headline).frame(width: 44, alignment: .leading)
                                Text(entry.nickname + (leaderboard.isMe(entry) ? " (Sen)" : ""))
                                    .fontWeight(leaderboard.isMe(entry) ? .bold : .medium)
                                Spacer()
                                Text(entry.score.formatted()).font(.headline.bold()).foregroundStyle(theme.accent)
                            }
                            .foregroundStyle(theme.ink)
                            .padding(16)
                            .background(leaderboard.isMe(entry) ? theme.accent.opacity(0.12) : theme.board.opacity(0.7),
                                        in: RoundedRectangle(cornerRadius: 16))
                        }
                    }
                    .padding(20)
                }
                .background(theme.canvas.ignoresSafeArea())
                .navigationTitle("🏆 Global sıralama")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Bitti") { showLeaderboard = false } } }
                .task { await leaderboard.load(mode: mode) }
            }
            .presentationDetents([.large])
        }
        .sheet(isPresented: $showSettings) {
            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 22) {
                        VStack(alignment: .leading, spacing: 5) {
                            Text("Kendine göre oyna")
                                .font(.title2.bold())
                                .foregroundStyle(theme.ink)
                            Text("Görünümü ve oyun deneyimini kişiselleştir.")
                                .font(.subheadline)
                                .foregroundStyle(theme.ink.opacity(0.65))
                        }
                        VStack(alignment: .leading, spacing: 12) {
                            settingsHeading("GÖRÜNÜM")
                            HStack(spacing: 10) {
                                ForEach(GameTheme.allCases) { option in
                                    Button {
                                        themeRaw = option.rawValue
                                    } label: {
                                        VStack(spacing: 9) {
                                            HStack(spacing: 3) {
                                                ForEach([2, 8, 32], id: \.self) { value in
                                                    Text("\(value)")
                                                        .font(.system(size: 10, weight: .bold, design: .rounded))
                                                        .foregroundStyle(Color.black)
                                                        .frame(maxWidth: .infinity)
                                                        .frame(height: 25)
                                                        .background(option.tile(value), in: RoundedRectangle(cornerRadius: 6))
                                                }
                                            }
                                            Text(option.title)
                                                .font(.system(size: 12, weight: .semibold))
                                                .lineLimit(1)
                                            Label(theme == option ? "Seçili" : "Seç", systemImage: theme == option ? "checkmark.circle.fill" : "circle")
                                                .font(.system(size: 11))
                                        }
                                        .foregroundStyle(option.ink)
                                        .frame(maxWidth: .infinity)
                                        .padding(10)
                                        .background(option.board, in: RoundedRectangle(cornerRadius: 18))
                                        .overlay(RoundedRectangle(cornerRadius: 18).stroke(theme == option ? option.accent : .clear, lineWidth: 2))
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel("\(option.title) teması")
                                    .accessibilityAddTraits(theme == option ? .isSelected : [])
                                }
                            }
                        }
                        VStack(alignment: .leading, spacing: 10) {
                            settingsHeading("ERİŞİLEBİLİRLİK")
                            VStack(spacing: 0) {
                                settingsToggle("Yüksek kontrast", subtitle: "Taşları daha belirgin göster", value: $highContrast)
                                Divider()
                                settingsToggle("Büyük taş sayıları", subtitle: "Taş değerlerini daha kolay oku", value: $largeNumbers)
                                Divider()
                                settingsToggle("Yön tuşları", subtitle: "Kaydırmadan da hamle yap", value: $directionalControls)
                            }
                            .padding(.horizontal, 14)
                            .background(theme.board.opacity(0.8), in: RoundedRectangle(cornerRadius: 20))
                        }
                        VStack(alignment: .leading, spacing: 10) {
                            settingsHeading("OYUN DENEYİMİ")
                            VStack(spacing: 0) {
                                settingsToggle("Ses efektleri", subtitle: "Birleşme ve başarı sesleri", value: $soundEnabled)
                                Divider()
                                settingsToggle("Titreşim", subtitle: "Dokunsal geri bildirim", value: $hapticsEnabled)
                                Divider()
                                settingsToggle("Animasyonları azalt", subtitle: "Daha sakin geçişler", value: $reducedMotion)
                            }
                            .padding(.horizontal, 14)
                            .background(theme.board.opacity(0.8), in: RoundedRectangle(cornerRadius: 20))
                        }
                    }
                    .padding(20)
                    .frame(maxWidth: 560)
                    .frame(maxWidth: .infinity)
                }
                .background(theme.canvas.ignoresSafeArea())
                .navigationTitle("Oyun ayarları")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Bitti") { showSettings = false }
                            .fontWeight(.semibold)
                            .tint(theme.accent)
                    }
                }
            }
            .presentationDetents([.large])
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
        .fullScreenCover(isPresented: Binding(
            get: { game.lost && !slideInProgress },
            set: { _ in }
        )) {
            ZStack {
                theme.canvas.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 18) {
                        Spacer(minLength: 24)
                        Image(systemName: "star.fill")
                            .font(.system(size: 34, weight: .bold))
                            .foregroundStyle(theme.accent)
                            .frame(width: 84, height: 84)
                            .background(theme.board, in: RoundedRectangle(cornerRadius: 26))
                        Text("Oyun Bitti!")
                            .font(.system(size: 32, weight: .black, design: .rounded))
                            .foregroundStyle(theme.ink)
                        Text("Harika bir denemeydi! Bir sonraki oyunda rekorunu geçebilirsin.")
                            .font(.subheadline)
                            .foregroundStyle(theme.ink.opacity(0.7))
                            .multilineTextAlignment(.center)
                        HStack(spacing: 12) {
                            scoreCard("SKOR", doubledGameKey == "\(game.score):\(game.board.map(String.init).joined(separator: ","))" ? "\(game.score * 2)" : "\(game.score)")
                            scoreCard("REKOR", "\(bestScore)")
                        }
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Label("Global Liderlik", systemImage: "trophy.fill")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundStyle(theme.ink)
                                Spacer()
                                Button("Yenile") { Task { await leaderboard.load(mode: mode) } }
                                    .font(.caption.weight(.semibold))
                                    .tint(theme.accent)
                            }
                            if leaderboard.isLoading { ProgressView().frame(maxWidth: .infinity) }
                            if let error = leaderboard.error {
                                Text(error).font(.caption).foregroundStyle(theme.ink.opacity(0.7))
                            }
                            ForEach(leaderboard.entries) { entry in
                                HStack {
                                    Text("\(entry.rank).")
                                        .fontWeight(.bold)
                                        .frame(width: 28, alignment: .leading)
                                    Text(entry.nickname + (leaderboard.isMe(entry) ? " (Sen)" : ""))
                                        .fontWeight(leaderboard.isMe(entry) ? .bold : .regular)
                                    Spacer()
                                    Text("\(entry.score)").fontWeight(.semibold)
                                }
                                .font(.system(size: 13, design: .rounded))
                                .foregroundStyle(theme.ink)
                            }
                            if let rank = leaderboard.myRank {
                                Text("Senin sıran: #\(rank)")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(theme.accent)
                            }
                        }
                        .padding(16)
                        .background(theme.board.opacity(0.8), in: RoundedRectangle(cornerRadius: 18))
                        Button {
                            restart()
                        } label: {
                            Label("Yeniden Oyna", systemImage: "arrow.clockwise")
                                .font(.headline)
                                .frame(maxWidth: .infinity)
                                .frame(height: 54)
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(.white)
                        .background(theme.accent, in: RoundedRectangle(cornerRadius: 17))
                        if doubledGameKey == "\(game.score):\(game.board.map(String.init).joined(separator: ","))" {
                            Text("✓ 2× puan ödülü uygulandı")
                                .font(.caption).foregroundStyle(theme.accent)
                        } else if rewardedAds.isReady && game.score > 0 {
                            Button {
                                let snapshot = game
                                let claimKey = "\(game.score):\(game.board.map(String.init).joined(separator: ","))"
                                rewardedAds.show(onRewardEarned: {
                                    guard game == snapshot, game.lost, doubledGameKey != claimKey else { return }
                                    doubledGameKey = claimKey
                                    let doubled = game.score > Int.max / 2 ? Int.max : game.score * 2
                                    bestScore = max(bestScore, doubled)
                                    GameStorage.saveBest(bestScore)
                                }, sessionId: verifiedSession?.sessionId, playerId: verifiedSession?.playerId)
                            } label: {
                                Label("Reklam İzle · 2× Puan", systemImage: "play.rectangle.fill")
                                    .font(.subheadline.weight(.semibold))
                                    .frame(maxWidth: .infinity).frame(height: 50)
                            }
                            .buttonStyle(.bordered).tint(theme.accent)
                        }
                        if verifiedSession == nil && rewardedAds.isReady && rewardedContinue.available {
                            Button {
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
                            } label: {
                                Label("Reklam İzle · 3 Hamle Geri Al", systemImage: "play.rectangle.fill")
                                    .font(.subheadline.weight(.semibold))
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 50)
                            }
                            .buttonStyle(.bordered)
                            .tint(theme.accent)
                        }
                        if verifiedSession == nil, let previous {
                            Button {
                                game = previous
                                self.previous = nil
                                if !rewardedContinue.history.isEmpty { rewardedContinue.history.removeLast() }
                                GameStorage.saveReward(rewardedContinue)
                                GameStorage.save(game)
                            } label: {
                                Label("Son Hamleyi Geri Al", systemImage: "arrow.uturn.backward")
                            }
                            .font(.subheadline.weight(.semibold))
                            .tint(theme.accent)
                        }
                        Spacer(minLength: 24)
                    }
                    .padding(24)
                    .frame(maxWidth: 440)
                    .frame(maxWidth: .infinity)
                }
            }
            .interactiveDismissDisabled()
            .task(id: "\(mode.rawValue)-\(game.score)") {
                if let session = verifiedSession {
                    if VerifiedEngine.replay(session.seed, session.moves)?.score == game.score {
                        if await leaderboard.finalizeVerified(session, score: game.score) {
                            verifiedSession = nil
                            VerifiedSession.save(nil)
                        }
                    }
                    if verifiedSession == nil { VerifiedSession.save(nil) }
                }
                await leaderboard.load(mode: mode)
            }
        }
        .confirmationDialog("Yeni oyun başlatılsın mı?", isPresented: $confirmRestart) {
            Button("Yeni Oyun", role: .destructive) { restart() }
            Button("Vazgeç", role: .cancel) {}
        } message: {
            Text("Mevcut ilerlemen sıfırlanacak. Rekorun korunacak.")
        }
    }

    private func settingsHeading(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 12, weight: .bold, design: .rounded))
            .tracking(1.2)
            .foregroundStyle(theme.accent)
    }

    private func settingsToggle(_ title: String, subtitle: String, value: Binding<Bool>) -> some View {
        Toggle(isOn: value) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.system(size: 15, weight: .semibold)).foregroundStyle(theme.ink)
                Text(subtitle).font(.system(size: 12)).foregroundStyle(theme.ink.opacity(0.65))
            }
        }
        .tint(theme.accent)
        .padding(.vertical, 12)
    }

    private func startRanked() {
        guard !startingVerified && !slideInProgress else { return }
        startingVerified = true
        Task {
            let session = await leaderboard.startVerified(mode: mode)
            startingVerified = false
            guard let session else { return }
            animationToken = UUID()
            slideInProgress = false
            slidingTiles = []
            verifiedSession = session
            game = VerifiedEngine.initial(session.seed)
            VerifiedSession.save(session)
            previous = nil
            rewardedContinue.reset()
            GameStorage.saveReward(rewardedContinue)
            GameStorage.save(game)
            winAcknowledged = false
        }
    }

    private func performMove(_ direction: MoveDirection) {
        guard !slideInProgress && !game.lost else { return }
        let result: MoveResult
        if let session = verifiedSession {
            guard let next = VerifiedEngine.replay(session.seed, session.moves + VerifiedEngine.letter(direction)) else { return }
            result = MoveResult(state: next, moved: next.board != game.board, scoreDelta: next.score - game.score)
        } else {
            result = GameEngine.move(game, direction)
        }
        guard result.moved else { return }
        if verifiedSession != nil {
            verifiedSession?.moves += VerifiedEngine.letter(direction)
            VerifiedSession.save(verifiedSession)
        }
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
        verifiedSession = nil
        VerifiedSession.save(nil)
        game = GameEngine.newGame()
        previous = nil
        rewardedContinue.reset()
        GameStorage.saveReward(rewardedContinue)
        winAcknowledged = false
        GameStorage.save(game)
        startRanked()
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
