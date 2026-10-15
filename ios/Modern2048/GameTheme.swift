import SwiftUI

enum GameTheme: String, CaseIterable, Identifiable {
    case sunset, ocean, midnight
    var id: String { rawValue }
    var title: String {
        switch self { case .sunset: "Gün Batımı"; case .ocean: "Okyanus"; case .midnight: "Gece" }
    }
    var canvas: Color {
        switch self { case .sunset: Color(red: 0.98, green: 0.97, blue: 0.96); case .ocean: Color(red: 0.92, green: 0.97, blue: 0.98); case .midnight: Color(red: 0.09, green: 0.10, blue: 0.16) }
    }
    var ink: Color { self == .midnight ? .white : Color(red: 0.14, green: 0.14, blue: 0.23) }
    var board: Color {
        switch self { case .sunset: Color(red: 0.91, green: 0.90, blue: 0.89); case .ocean: Color(red: 0.75, green: 0.87, blue: 0.90); case .midnight: Color(red: 0.19, green: 0.21, blue: 0.30) }
    }
    var accent: Color {
        switch self { case .sunset: Color(red: 0.40, green: 0.34, blue: 0.91); case .ocean: Color(red: 0.03, green: 0.49, blue: 0.60); case .midnight: Color(red: 0.65, green: 0.61, blue: 1.0) }
    }
    var empty: Color { self == .midnight ? Color(red: 0.25, green: 0.27, blue: 0.37) : Color(red: 0.96, green: 0.94, blue: 0.93) }
    func tile(_ value: Int) -> Color {
        if value == 0 { return empty }
        let colors: [UInt32]
        switch self {
        case .sunset: colors = [0xFFF0DA, 0xFFE0BA, 0xFFB16E, 0xFF866F, 0xFF6D93, 0xD879EC, 0x67B8F4, 0x43C5C2, 0x6FCF97, 0xB9A0FF, 0xFFD15C]
        case .ocean: colors = [0xD5F5F2, 0xAAE9E6, 0x7EDAD9, 0x54C6CF, 0x4AA8CE, 0x608FD4, 0x8583DC, 0x9F80D2, 0x6AC8AC, 0x40ADAA, 0xFFCE81]
        case .midnight: colors = [0xB4ADD8, 0xA79FDD, 0x8B8AD6, 0x747BC8, 0x696EB7, 0x8669B8, 0xA166BA, 0xBA6BA8, 0xCF818F, 0xDB9C76, 0xF2C875]
        }
        let index = min(max(value.trailingZeroBitCount - 1, 0), colors.count - 1)
        let rgb = colors[index]
        return Color(red: Double((rgb >> 16) & 255) / 255, green: Double((rgb >> 8) & 255) / 255, blue: Double(rgb & 255) / 255)
    }
}
