import Foundation

enum GameMode: String, CaseIterable, Identifiable {
    case classic, target1024, endless
    var id: String { rawValue }
    var title: String {
        switch self {
        case .classic: return NSLocalizedString("Klasik 2048", comment: "Classic game mode")
        case .target1024: return NSLocalizedString("Hedef 1024", comment: "1024 target game mode")
        case .endless: return NSLocalizedString("Süresiz", comment: "Endless game mode")
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
