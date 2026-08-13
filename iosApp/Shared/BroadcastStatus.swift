import Foundation

/// Live broadcast diagnostics shared app ↔ extension via the App Group. The extension writes; the
/// container app reads on each Darwin `updated` ping (and can poll as a safety net).
enum BroadcastStatus {
    static let updated = "app.pillion.broadcast.status"
    private static let key = "broadcast.status"
    private static var shared: UserDefaults? { UserDefaults(suiteName: BroadcastConfig.appGroup) }

    /// Snapshot the extension publishes while mirroring.
    struct Snapshot {
        var phase: String          // looking | connecting | handshake | streaming | error | stopped
        var transport: String      // bike | emulator | none
        var bikeFound: Bool
        var accessories: String    // human-readable list (or "none")
        var message: String        // phase detail / error
        var fps: Double
        var kbPerFrame: Int

        var asDict: [String: Any] {
            [
                "phase": phase,
                "transport": transport,
                "bikeFound": bikeFound,
                "accessories": accessories,
                "message": message,
                "fps": fps,
                "kbPerFrame": kbPerFrame,
            ]
        }

        static func from(_ d: [String: Any]?) -> Snapshot? {
            guard let d = d, let phase = d["phase"] as? String else { return nil }
            return Snapshot(
                phase: phase,
                transport: d["transport"] as? String ?? "none",
                bikeFound: d["bikeFound"] as? Bool ?? false,
                accessories: d["accessories"] as? String ?? "none",
                message: d["message"] as? String ?? "",
                fps: d["fps"] as? Double ?? 0,
                kbPerFrame: d["kbPerFrame"] as? Int ?? 0
            )
        }
    }

    static func publish(_ snap: Snapshot) {
        shared?.set(snap.asDict, forKey: key)
        // synchronize() is deprecated but still the only reliable flush across processes on older iOS.
        shared?.synchronize()
        CFNotificationCenterPostNotification(
            CFNotificationCenterGetDarwinNotifyCenter(),
            CFNotificationName(updated as CFString), nil, nil, true)
    }

    static func read() -> Snapshot? {
        Snapshot.from(shared?.dictionary(forKey: key))
    }

    static func clear() {
        shared?.removeObject(forKey: key)
        shared?.synchronize()
    }
}
