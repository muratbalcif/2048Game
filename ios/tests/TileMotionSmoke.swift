import Foundation

@main
enum TileMotionSmoke {
    static func main() {
        let row = [2, 2, 4, 0] + Array(repeating: 0, count: 12)
        let left = TileMotionPlanner.plan(row, .left)
        assert(left.map(\.to) == [0, 0, 1])
        assert(left.map(\.merged) == [true, true, false])
        let right = TileMotionPlanner.plan(row, .right)
        assert(right.map(\.to) == [3, 2, 2])
        let vertical = [2, 0, 0, 0] + Array(repeating: 0, count: 8) + [2, 0, 0, 0]
        assert(TileMotionPlanner.plan(vertical, .up).map(\.to) == [0, 0])
        print("TileMotion smoke tests passed")
    }
}
