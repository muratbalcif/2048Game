import Foundation

enum GameMode: String, CaseIterable, Identifiable {
    case classic, target1024, endless
    var id: String { rawValue }
    var title: String {
        switch self {
        case .classic: return "Klasik 2048"
        case .target1024: return "Hedef 1024"
        case .endless: return "Süresiz"
        }
    }
    var target: Int? {
        switch self {
        case .classic: return 2048
        case .target1024: return 1024
        case .endless: return nil
        }
    }
    func reached(_ board: [Int]) -> Bool {
        guard let target else { return false }
        return board.contains { $0 >= target }
    }
}
