import Foundation

@main
enum GameModeSmoke {
    static func main() {
        let board = [1024] + Array(repeating: 0, count: 15)
        assert(GameMode.target1024.reached(board))
        assert(!GameMode.classic.reached(board))
        assert(!GameMode.endless.reached(board))
        assert(!GameMode.endless.reached(Array(repeating: 2048, count: 16)))
        print("Game mode smoke tests passed")
    }
}
