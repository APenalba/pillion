import SwiftUI
import ReplayKit
import ExternalAccessory
import ComposeApp

/// Connects the shared Compose UI to iOS screen broadcasting:
/// - the Pillion "Start mirroring" button → triggers the system broadcast picker,
/// - a visible fallback picker (ReplayKit has no public API — programmatic taps fail on some iOS builds),
/// - the extension's Darwin start/stop/status → `MirrorState`,
/// - a live MFi accessory preflight so Connection status is visible before Start mirroring.
final class BroadcastBridge: ObservableObject {
    let controller: BroadcastMirrorController
    let sdlController: SdlBroadcastController
    private let sdlSession: SdlSession
    private weak var picker: RPSystemBroadcastPickerView?
    private var statusPoll: Timer?
    private var preflightPoll: Timer?
    /// Cancels stale "extension never started" timeouts when a newer Start is tapped.
    private var startGeneration = 0

    init() {
        controller = BroadcastMirrorController()
        sdlController = SdlBroadcastController()
        let sdlController = self.sdlController
        sdlSession = SdlSession(onState: { state in
            DispatchQueue.main.async {
                switch state {
                case .idle: sdlController.setIdle()
                case .connecting: sdlController.setConnecting()
                case .streaming: sdlController.setStreaming()
                case .error(let message): sdlController.setError(message: message)
                }
            }
        })
        controller.onToggle = { [weak self] in self?.triggerPicker() }
        self.sdlController.onStart = { [weak self] in self?.sdlSession.start() }
        self.sdlController.onStop = { [weak self] in self?.sdlSession.stop() }
        observeBroadcastState()
        startPreflightPoll()
    }

    func makeViewController() -> UIViewController {
        MainViewControllerKt.MainViewController(naviliteController: controller, sdlController: sdlController)
    }

    func register(_ picker: RPSystemBroadcastPickerView) { self.picker = picker }

    private func triggerPicker() {
        startGeneration += 1
        let gen = startGeneration
        controller.setAwaitingBroadcast()

        // Prefer a real layout pass — off-screen pickers sometimes have no UIButton yet.
        if let picker = picker {
            picker.setNeedsLayout()
            picker.layoutIfNeeded()
        }

        guard let picker = picker else {
            controller.setPickerFailed(reason:
                "System broadcast picker is not ready. Use the red record icon below the screen, or restart the app.")
            return
        }
        guard let control = Self.firstBroadcastControl(in: picker) else {
            controller.setPickerFailed(reason:
                "Could not trigger the system sheet automatically. Tap the red record icon under Connection status instead.")
            return
        }
        control.sendActions(for: .touchUpInside)

        // If the extension never posts "started", the UI used to look like Start did nothing.
        DispatchQueue.main.asyncAfter(deadline: .now() + 12) { [weak self] in
            guard let self = self, self.startGeneration == gen else { return }
            self.controller.setBroadcastDidNotStart()
        }
    }

    /// ReplayKit's picker embeds a UIButton (sometimes nested); walk the tree broadly.
    private static func firstBroadcastControl(in view: UIView) -> UIControl? {
        if let c = view as? UIControl { return c }
        for sub in view.subviews {
            if let c = firstBroadcastControl(in: sub) { return c }
        }
        return nil
    }

    private func observeBroadcastState() {
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        let me = Unmanaged.passUnretained(self).toOpaque()
        let callback: CFNotificationCallback = { _, observer, name, _, _ in
            guard let observer = observer, let name = name else { return }
            let bridge = Unmanaged<BroadcastBridge>.fromOpaque(observer).takeUnretainedValue()
            let raw = name.rawValue as String
            DispatchQueue.main.async {
                switch raw {
                case "app.pillion.broadcast.started":
                    bridge.startGeneration += 1   // invalidate pending timeout
                    bridge.controller.setActive(active: true)
                    bridge.startStatusPoll()
                    bridge.pullStatus()
                case "app.pillion.broadcast.stopped":
                    bridge.stopStatusPoll()
                    bridge.controller.setActive(active: false)
                    bridge.publishPreflight()
                case BroadcastStatus.updated:
                    bridge.pullStatus()
                default: break
                }
            }
        }
        for name in [
            "app.pillion.broadcast.started",
            "app.pillion.broadcast.stopped",
            BroadcastStatus.updated,
        ] {
            CFNotificationCenterAddObserver(center, me, callback, name as CFString, nil, .deliverImmediately)
        }
    }

    private func pullStatus() {
        guard let snap = BroadcastStatus.read() else { return }
        controller.applyStatus(
            phase: snap.phase,
            transport: snap.transport,
            bikeFound: snap.bikeFound,
            accessories: snap.accessories,
            message: snap.message,
            fps: snap.fps,
            kbPerFrame: Int32(snap.kbPerFrame)
        )
    }

    private func startStatusPoll() {
        stopStatusPoll()
        statusPoll = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.pullStatus()
        }
    }

    private func stopStatusPoll() {
        statusPoll?.invalidate()
        statusPoll = nil
    }

    private func startPreflightPoll() {
        publishPreflight()
        preflightPoll = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { [weak self] _ in
            self?.publishPreflight()
        }
    }

    private func publishPreflight() {
        let protocolId = BroadcastConfig.dashProtocol
        let accs = EAAccessoryManager.shared().connectedAccessories
        let bikeFound = accs.contains { $0.protocolStrings.contains(protocolId) }
        let lines: String
        if accs.isEmpty {
            lines = "MFi accessories: none\nCCU (\(protocolId)): NOT found\n\nPair the bike in Bluetooth settings, close StreetCross, put the dash in Navigation mode."
        } else {
            let list = accs.map { "• \($0.name): \($0.protocolStrings.joined(separator: ", "))" }
                .joined(separator: "\n")
            lines = "MFi accessories:\n\(list)\n\nCCU (\(protocolId)): \(bikeFound ? "found ✓" : "NOT found")"
        }
        controller.setPreflight(hint: lines)
    }
}
