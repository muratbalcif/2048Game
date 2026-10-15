import SwiftUI

private enum Palette {
    static let canvas = Color(red: 250/255, green: 248/255, blue: 245/255)
    static let ink = Color(red: 36/255, green: 36/255, blue: 58/255)
    static let board = Color(red: 233/255, green: 229/255, blue: 226/255)
    static let empty = Color(red: 245/255, green: 241/255, blue: 237/255)
    static let purple = Color(red: 102/255, green: 87/255, blue: 232/255)
}

struct GameScreen: View {
    @State private var game = GameEngine.newGame()
    @State private var bestScore = 0
    @State private var previous: GameState?

    var body: some View {
        VStack(spacing: 24) {
            Text("2048")
                .font(.system(size: 56, weight: .black, design: .rounded))
                .foregroundStyle(Palette.purple)
            Text("Modern Puzzle").foregroundStyle(Palette.ink)
            HStack(spacing: 12) {
                scoreCard("SKOR", "\(game.score)")
                scoreCard("REKOR", "\(bestScore)")
            }
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 4), spacing: 10) {
                ForEach(0..<16, id: \.self) { index in
                    let value = game.board[index]
                    ZStack {
                        RoundedRectangle(cornerRadius: 14)
                            .fill(value == 0 ? Palette.empty : tileColor(value))
                        if value != 0 {
                            Text("\(value)")
                                .font(.system(size: value >= 1024 ? 19 : 25, weight: .bold, design: .rounded))
                                .foregroundStyle(Palette.ink)
                        }
                    }
                    .aspectRatio(1, contentMode: .fit)
                }
            }
            .padding(10)
            .background(Palette.board, in: RoundedRectangle(cornerRadius: 24))
            .gesture(DragGesture(minimumDistance: 20).onEnded { gesture in
                let dx = gesture.translation.width
                let dy = gesture.translation.height
                let direction: MoveDirection = abs(dx) > abs(dy)
                    ? (dx > 0 ? .right : .left)
                    : (dy > 0 ? .down : .up)
                let result = GameEngine.move(game, direction)
                if result.moved {
                    previous = game
                    withAnimation(.easeInOut(duration: 0.15)) { game = result.state }
                    bestScore = max(bestScore, game.score)
                }
            })
            Text("Kaydırarak aynı sayıları birleştir").foregroundStyle(Palette.ink)
            HStack {
                Button("Geri Al") {
                    if let previous { game = previous; self.previous = nil }
                }
                .disabled(previous == nil)
                Button("Yeni Oyun") {
                    game = GameEngine.newGame()
                    previous = nil
                }
            }
            .buttonStyle(.borderedProminent)
            Spacer()
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Palette.canvas.ignoresSafeArea())
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
