import SwiftUI

private enum Palette {
    static let canvas = Color(red: 250/255, green: 248/255, blue: 245/255)
    static let ink = Color(red: 36/255, green: 36/255, blue: 58/255)
    static let board = Color(red: 233/255, green: 229/255, blue: 226/255)
    static let empty = Color(red: 245/255, green: 241/255, blue: 237/255)
    static let purple = Color(red: 102/255, green: 87/255, blue: 232/255)
}

struct GameScreen: View {
    var body: some View {
        VStack(spacing: 24) {
            Text("2048")
                .font(.system(size: 56, weight: .black, design: .rounded))
                .foregroundStyle(Palette.purple)
            Text("Modern Puzzle").foregroundStyle(Palette.ink)
            HStack(spacing: 12) {
                scoreCard("SKOR", "0")
                scoreCard("REKOR", "0")
            }
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 4), spacing: 10) {
                ForEach(0..<16, id: \.self) { _ in
                    RoundedRectangle(cornerRadius: 14)
                        .fill(Palette.empty)
                        .aspectRatio(1, contentMode: .fit)
                }
            }
            .padding(10)
            .background(Palette.board, in: RoundedRectangle(cornerRadius: 24))
            Text("Kaydırarak aynı sayıları birleştir").foregroundStyle(Palette.ink)
            Button("Yeni Oyun — Faz 1'de aktif") {}
                .buttonStyle(.borderedProminent)
                .disabled(true)
            Spacer()
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Palette.canvas.ignoresSafeArea())
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
